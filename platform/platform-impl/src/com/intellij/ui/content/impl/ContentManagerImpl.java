// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.content.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.ActiveRunnable;
import com.intellij.openapi.util.BusyObject;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.content.*;
import com.intellij.util.EventDispatcher;
import com.intellij.util.SmartList;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.*;
import java.util.function.Supplier;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class ContentManagerImpl implements ContentManager, PropertyChangeListener, Disposable.Parent {
  private static final Logger LOG = Logger.getInstance(ContentManagerImpl.class);

  private ContentUI myUI;
  private final List<Content> myContents = new ArrayList<>();
  private final List<ContentManagerImpl> myNestedManagers = new SmartList<>();
  private final EventDispatcher<ContentManagerListener> myDispatcher = EventDispatcher.create(ContentManagerListener.class);
  private final List<Content> mySelection = new ArrayList<>();
  private final boolean myCanCloseContents;

  private JPanel myComponent;

  private final Set<Content> myContentWithChangedComponent = new HashSet<>();

  private boolean myDisposed;
  private final Project myProject;

  private final List<DataProvider> dataProviders = new SmartList<>();
  private final ArrayList<Content> mySelectionHistory = new ArrayList<>();

  /**
   * WARNING: as this class adds listener to the ProjectManager which is removed on projectClosed event, all instances of this class
   * must be created on already OPENED projects, otherwise there will be memory leak!
   */
  public ContentManagerImpl(@NotNull ContentUI contentUI, boolean canCloseContents, @NotNull Project project) {
    this(contentUI, canCloseContents, project, project);
  }

  public ContentManagerImpl(@NotNull ContentUI contentUI, boolean canCloseContents, @NotNull Project project, @NotNull Disposable parentDisposable) {
    this(canCloseContents, project, parentDisposable, (contentManager, componentGetter) -> {
      // if some contentUI expects that myUI will be set before setManager (as it was before introducing ContentUiProducer)
      ((ContentManagerImpl)contentManager).myUI = contentUI;
      contentUI.setManager(contentManager);
      return contentUI;
    });
  }

  public interface ContentUiProducer {
    ContentUI createContent(@NotNull ContentManager contentManager, @NotNull Supplier<@NotNull JPanel> componentGetter);
  }

  @ApiStatus.Experimental
  public ContentManagerImpl(boolean canCloseContents, @NotNull Project project, @NotNull Disposable parentDisposable, @NotNull ContentUiProducer contentUiProducer) {
    myProject = project;
    myCanCloseContents = canCloseContents;
    ContentUI ui = contentUiProducer.createContent(this, () -> {
      LOG.assertTrue(myComponent == null);
      myComponent = new MyNonOpaquePanel();
      return myComponent;
    });
    myUI = ui;

    // register on project (will be disposed before services) because before Content disposal
    // the UsageView is disposed before which virtual file pointers should be externalized for which they need to be restored
    // for which com.intellij.psi.impl.smartPointers.SelfElementInfo.restoreFileFromVirtual() must be able to work
    // for which the findFile() must access fileManager for which it must be alive
    Disposer.register(parentDisposable, this);
    if (ui instanceof Disposable) {
      Disposer.register(this, (Disposable)ui);
    }
  }

  public void addNestedManager(@NotNull ContentManagerImpl manager) {
    myNestedManagers.add(manager);
    Disposer.register(manager, new Disposable() {
      @Override
      public void dispose() {
        removeNestedManager(manager);
      }
    });
  }

  public void removeNestedManager(@NotNull ContentManagerImpl manager) {
    myNestedManagers.remove(manager);
  }

  @Override
  public boolean canCloseContents() {
    return myCanCloseContents;
  }

  @Override
  public @NotNull JComponent getComponent() {
    if (myComponent == null) {
      myComponent = new MyNonOpaquePanel();
      myComponent.add(myUI.getComponent(), BorderLayout.CENTER);
    }
    return myComponent;
  }

  @Override
  public @NotNull ActionCallback getReady(@NotNull Object requestor) {
    Content selected = getSelectedContent();
    if (selected == null) return ActionCallback.DONE;
    BusyObject busyObject = selected.getBusyObject();
    return busyObject != null ? busyObject.getReady(requestor) : ActionCallback.DONE;
  }

  private final class MyNonOpaquePanel extends JBPanelWithEmptyText implements DataProvider {
    MyNonOpaquePanel() {
      super(new BorderLayout());

      setOpaque(false);
    }

    @Override
    public @Nullable Object getData(@NotNull @NonNls String dataId) {
      if (PlatformDataKeys.CONTENT_MANAGER.is(dataId) || PlatformDataKeys.NONEMPTY_CONTENT_MANAGER.is(dataId) && getContentCount() > 1) {
        return ContentManagerImpl.this;
      }

      for (DataProvider dataProvider : dataProviders) {
        Object data = dataProvider.getData(dataId);
        if (data != null) {
          return data;
        }
      }

      if (myUI instanceof DataProvider) {
        return ((DataProvider)myUI).getData(dataId);
      }

      DataProvider provider = DataManager.getDataProvider(this);
      return provider == null ? null : provider.getData(dataId);
    }
  }

  @Override
  public void addContent(@NotNull Content content, final int order) {
    doAddContent(content, order);
  }

  @Override
  public void addContent(@NotNull Content content) {
    doAddContent(content, -1);
  }

  private void doAddContent(final @NotNull Content content, final int index) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myContents.contains(content)) {
      myContents.remove(content);
      myContents.add(index < 0 ? myContents.size() : index, content);
      return;
    }

    if (!Content.TEMPORARY_REMOVED_KEY.get(content, false) && getContentCount() == 0 && !isEmpty()) {
      ContentManager oldManager = content.getManager();
      for (ContentManagerImpl nestedManager : myNestedManagers) {
        if (nestedManager.getContentCount() > 0) {
          nestedManager.doAddContent(content, index);
          if (content.getManager() != oldManager) {
            return;
          }
        }
      }
    }

    ((ContentImpl)content).setManager(this);
    final int insertIndex = index < 0 ? myContents.size() : index;
    myContents.add(insertIndex, content);
    content.addPropertyChangeListener(this);
    fireContentAdded(content, insertIndex);
    if (myUI.isToSelectAddedContent() || mySelection.isEmpty() && !myUI.canBeEmptySelection()) {
      if (myUI.isSingleSelection()) {
        setSelectedContent(content);
      }
      else {
        addSelectedContent(content);
      }
    }

    Disposer.register(this, content);
  }

  @Override
  public boolean removeContent(@NotNull Content content, boolean dispose) {
    boolean wasFocused = UIUtil.isFocusAncestor(content.getComponent());
    return removeContent(content, dispose, wasFocused, false).isDone();
  }

  @Override
  public @NotNull ActionCallback removeContent(@NotNull Content content, boolean dispose, boolean requestFocus, boolean forcedFocus) {
    ActionCallback result = new ActionCallback();
    doRemoveContent(content, dispose).doWhenDone(() -> {
      if (requestFocus) {
        Content current = getSelectedContent();
        if (current != null) {
          setSelectedContent(current, true, true, !forcedFocus).notify(result);
        }
        else {
          ToolWindowManager.getInstance(myProject).activateEditorComponent();
          result.setDone();
        }
      }
      else {
        result.setDone();
      }
    });

    return result;
  }

  private @NotNull ActionCallback doRemoveContent(@NotNull Content content, boolean dispose) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    int indexToBeRemoved = getIndexOfContent(content);
    if (indexToBeRemoved == -1) {
      return ActionCallback.REJECTED;
    }

    try {
      Content selection = mySelection.isEmpty() ? null : mySelection.get(mySelection.size() - 1);
      int selectedIndex = selection != null ? myContents.indexOf(selection) : -1;

      if (!fireContentRemoveQuery(content, indexToBeRemoved) || !content.isValid()) {
        return ActionCallback.REJECTED;
      }

      boolean wasSelected = isSelected(content);
      if (wasSelected) {
        removeFromSelection(content);
      }

      int indexToSelect = -1;
      if (wasSelected) {
        int i = indexToBeRemoved - 1;
        if (i >= 0) {
          indexToSelect = i;
        }
        else if (getContentCount() > 1) {
          indexToSelect = 0;
        }
      }
      else if (selectedIndex > indexToBeRemoved) {
        indexToSelect = selectedIndex - 1;
      }

      mySelectionHistory.remove(content);
      myContentWithChangedComponent.remove(content);
      myContents.remove(content);
      content.removePropertyChangeListener(this);

      fireContentRemoved(content, indexToBeRemoved);
      ((ContentImpl)content).setManager(null);

      if (dispose) {
        Disposer.dispose(content);
      }

      int newSize = myContents.size();
      if (newSize > 0) {
        if (indexToSelect > -1) {
          final Content toSelect = !mySelectionHistory.isEmpty() ? mySelectionHistory.get(0) : myContents.get(indexToSelect);
          if (!isSelected(toSelect)) {
            if (myUI.isSingleSelection()) {
              ActionCallback result = new ActionCallback();
              setSelectedContentCB(toSelect).notify(result);
              return result;
            }
            else {
              addSelectedContent(toSelect);
            }
          }
        }
      }
      else {
        mySelection.clear();
      }
      return ActionCallback.DONE;
    }
    finally {
      if (ApplicationManager.getApplication().isDispatchThread() && !myDisposed && myContents.isEmpty()) {
        // cleanup visibleComponent in TabbedPaneUI only if there is no content left,
        // otherwise immediate adding of a new content will lead to having visible two TabWrapper component at the same time.
        myUI.getComponent().updateUI(); //cleanup visibleComponent from Alloy...TabbedPaneUI
      }
    }
  }

  @Override
  public void removeAllContents(boolean dispose) {
    if (myContents.isEmpty()) {
      return;
    }

    for (Content content : List.copyOf(myContents)) {
      removeContent(content, dispose);
    }
  }

  @Override
  public int getContentCount() {
    return myContents.size();
  }

  @Override
  public boolean isEmpty() {
    boolean empty = ContentManager.super.isEmpty();
    if (!empty) return false;
    for (ContentManager manager : myNestedManagers) {
      if (!manager.isEmpty()) return false;
    }
    return true;
  }

  @Override
  public Content @NotNull [] getContents() {
    return myContents.toArray(new Content[0]);
  }

  public List<Content> getContentsRecursively() {
    SmartList<Content> list = new SmartList<>(myContents);
    for (ContentManagerImpl nestedManager : myNestedManagers) {
      list.addAll(nestedManager.getContentsRecursively());
    }
    return list;
  }

  @Override
  public Content findContent(String displayName) {
    for (Content content : myContents) {
      if (content.getDisplayName().equals(displayName)) {
        return content;
      }
    }
    return null;
  }

  @Override
  public Content getContent(int index) {
    return index >= 0 && index < myContents.size() ? myContents.get(index) : null;
  }

  @Override
  public Content getContent(@NotNull JComponent component) {
    Content[] contents = getContents();
    for (Content content : contents) {
      if (SwingUtilities.isDescendingFrom(component, content.getComponent())) {
        return content;
      }
    }
    return null;
  }

  @Override
  public int getIndexOfContent(@NotNull Content content) {
    return myContents.indexOf(content);
  }

  @Override
  public @NotNull String getCloseActionName() {
    return myUI.getCloseActionName();
  }

  @Override
  public @NotNull String getCloseAllButThisActionName() {
    return myUI.getCloseAllButThisActionName();
  }

  @Override
  public @NotNull String getPreviousContentActionName() {
    return myUI.getPreviousContentActionName();
  }

  @Override
  public @NotNull String getNextContentActionName() {
    return myUI.getNextContentActionName();
  }

  @Override
  public @NotNull List<AnAction> getAdditionalPopupActions(final @NotNull Content content) {
    return Collections.emptyList();
  }

  @Override
  public boolean canCloseAllContents() {
    if (!canCloseContents()) {
      return false;
    }
    for (Content content : myContents) {
      if (content.isCloseable()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void addSelectedContent(final @NotNull Content content) {
    if (!checkSelectionChangeShouldBeProcessed(content, false)) return;

    if (getIndexOfContent(content) == -1) {
      throw new IllegalArgumentException("content not found: " + content);
    }
    if (!isSelected(content)) {
      mySelection.add(content);
      fireSelectionChanged(content, ContentManagerEvent.ContentOperation.add);
    }
  }

  private boolean checkSelectionChangeShouldBeProcessed(@NotNull Content content, boolean implicit) {
    if (!myUI.canChangeSelectionTo(content, implicit)) {
      return false;
    }

    boolean result = !isSelected(content) || myContentWithChangedComponent.contains(content);
    myContentWithChangedComponent.remove(content);
    return result;
  }

  @Override
  public void removeFromSelection(@NotNull Content content) {
    if (!isSelected(content)) return;
    mySelection.remove(content);
    fireSelectionChanged(content, ContentManagerEvent.ContentOperation.remove);
  }

  @Override
  public boolean isSelected(@NotNull Content content) {
    return mySelection.contains(content);
  }

  @Override
  public Content @NotNull [] getSelectedContents() {
    return mySelection.toArray(new Content[0]);
  }

  @Override
  public @Nullable Content getSelectedContent() {
    return mySelection.isEmpty() ? null : mySelection.get(0);
  }

  @Override
  public void setSelectedContent(@NotNull Content content, boolean requestFocus) {
    setSelectedContentCB(content, requestFocus);
  }

  @Override
  public @NotNull ActionCallback setSelectedContentCB(final @NotNull Content content, final boolean requestFocus) {
    return setSelectedContentCB(content, requestFocus, true);
  }

  @Override
  public void setSelectedContent(@NotNull Content content, boolean requestFocus, boolean forcedFocus) {
    setSelectedContentCB(content, requestFocus, forcedFocus);
  }

  @Override
  public @NotNull ActionCallback setSelectedContentCB(final @NotNull Content content, final boolean requestFocus, final boolean forcedFocus) {
    return setSelectedContent(content, requestFocus, forcedFocus, false);
  }

  @Override
  public @NotNull ActionCallback setSelectedContent(final @NotNull Content content, final boolean requestFocus, final boolean forcedFocus, boolean implicit) {
    mySelectionHistory.remove(content);
    mySelectionHistory.add(0, content);
    if (isSelected(content) && requestFocus) {
      return getFocusManager().requestFocusInProject(getComponent(), myProject).doWhenProcessed(() -> requestFocus(content, forcedFocus));
    }

    if (!checkSelectionChangeShouldBeProcessed(content, implicit)) {
      return ActionCallback.REJECTED;
    }
    if (!myContents.contains(content)) {
      for (ContentManagerImpl manager : myNestedManagers) {
        ActionCallback nestedCallback = manager.setSelectedContent(content, requestFocus, forcedFocus, implicit);
        if (nestedCallback != ActionCallback.REJECTED) return nestedCallback;
      }
      return ActionCallback.REJECTED;
    }

    final boolean focused = isSelectionHoldsFocus();

    final Content[] old = getSelectedContents();

    final ActiveRunnable selection = new ActiveRunnable() {
      @Override
      public @NotNull ActionCallback run() {
        if (myDisposed || getIndexOfContent(content) == -1) return ActionCallback.REJECTED;

        for (Content each : old) {
          removeFromSelection(each);
        }

        addSelectedContent(content);

        if (requestFocus) {
          requestFocus(content, forcedFocus);
        }
        return ActionCallback.DONE;
      }
    };

    final ActionCallback result = new ActionCallback();
    boolean enabledFocus = getFocusManager().isFocusTransferEnabled();
    if (focused || requestFocus) {
      if (enabledFocus) {
        return getFocusManager().requestFocusInProject(getComponent(), myProject).doWhenProcessed(() -> selection.run().notify(result));
      }
    }
    return selection.run().notify(result);
  }

  private boolean isSelectionHoldsFocus() {
    boolean focused = false;
    final Content[] selection = getSelectedContents();
    for (Content each : selection) {
      if (UIUtil.isFocusAncestor(each.getComponent())) {
        focused = true;
        break;
      }
    }
    return focused;
  }

  @Override
  public @NotNull ActionCallback setSelectedContentCB(@NotNull Content content) {
    return setSelectedContentCB(content, false);
  }

  @Override
  public void setSelectedContent(@NotNull Content content) {
    setSelectedContentCB(content);
  }

  @Override
  public ActionCallback selectPreviousContent() {
    int contentCount = getContentCount();
    LOG.assertTrue(contentCount > 1);
    Content selectedContent = getSelectedContent();
    int index = selectedContent == null ? -1 : getIndexOfContent(selectedContent);
    index = (index - 1 + contentCount) % contentCount;
    final Content content = getContent(index);
    if (content == null) {
      return null;
    }
    return setSelectedContentCB(content, true);
  }

  @Override
  public ActionCallback selectNextContent() {
    int contentCount = getContentCount();
    LOG.assertTrue(contentCount > 1);
    Content selectedContent = getSelectedContent();
    int index = selectedContent == null ? -1 : getIndexOfContent(selectedContent);
    index = (index + 1) % contentCount;
    final Content content = getContent(index);
    if (content == null) {
      return null;
    }
    return setSelectedContentCB(content, true);
  }

  @Override
  public void addContentManagerListener(@NotNull ContentManagerListener l) {
    myDispatcher.getListeners().add(0, l);
  }

  @Override
  public void removeContentManagerListener(@NotNull ContentManagerListener l) {
    myDispatcher.removeListener(l);
  }

  private void fireContentAdded(@NotNull Content content, int newIndex) {
    ContentManagerEvent e = new ContentManagerEvent(this, content, newIndex, ContentManagerEvent.ContentOperation.add);
    myDispatcher.getMulticaster().contentAdded(e);
  }

  private void fireContentRemoved(@NotNull Content content, int oldIndex) {
    ContentManagerEvent e = new ContentManagerEvent(this, content, oldIndex, ContentManagerEvent.ContentOperation.remove);
    myDispatcher.getMulticaster().contentRemoved(e);
  }

  private void fireSelectionChanged(@NotNull Content content, ContentManagerEvent.ContentOperation operation) {
    ContentManagerEvent e = new ContentManagerEvent(this, content, getIndexOfContent(content), operation);
    myDispatcher.getMulticaster().selectionChanged(e);
  }

  private boolean fireContentRemoveQuery(@NotNull Content content, int oldIndex) {
    ContentManagerEvent event = new ContentManagerEvent(this, content, oldIndex, ContentManagerEvent.ContentOperation.undefined);
    for (ContentManagerListener listener : myDispatcher.getListeners()) {
      listener.contentRemoveQuery(event);
      if (event.isConsumed()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public @NotNull ActionCallback requestFocus(final Content content, final boolean forced) {
    final Content toSelect = content == null ? getSelectedContent() : content;
    if (toSelect == null) return ActionCallback.REJECTED;
    assert myContents.contains(toSelect);
    JComponent preferredFocusableComponent = toSelect.getPreferredFocusableComponent();
    return preferredFocusableComponent != null ? getFocusManager().requestFocusInProject(preferredFocusableComponent, myProject) : ActionCallback.REJECTED;
  }

  private IdeFocusManager getFocusManager() {
    return IdeFocusManager.getInstance(myProject);
  }

  @Override
  public void addDataProvider(final @NotNull DataProvider provider) {
    dataProviders.add(provider);
  }

  @Override
  public void propertyChange(@NotNull PropertyChangeEvent event) {
    if (Content.PROP_COMPONENT.equals(event.getPropertyName())) {
      myContentWithChangedComponent.add((Content)event.getSource());
    }
  }

  @Override
  public @NotNull ContentFactory getFactory() {
    return ApplicationManager.getApplication().getService(ContentFactory.class);
  }

  @Override
  public void beforeTreeDispose() {
    if (!myDisposed) {
      myUI.beforeDispose();
    }
  }

  @Override
  public void dispose() {
    if (myDisposed) return;
    myDisposed = true;

    myContents.clear();
    myNestedManagers.clear();
    mySelection.clear();
    myContentWithChangedComponent.clear();
    myUI = null;
    myDispatcher.getListeners().clear();
    dataProviders.clear();
    myComponent = null;
  }

  @Override
  public boolean isDisposed() {
    return myDisposed;
  }

  @Override
  public boolean isSingleSelection() {
    return myUI.isSingleSelection();
  }

  public void rebuildContentUi() {
    if (myUI instanceof ToolWindowContentUi) {
      ToolWindowContentUi contentUi = (ToolWindowContentUi)myUI;
      contentUi.rebuild();
    }
  }

  @Nullable
  public ContentUI getUI() {
    return myUI;
  }
}
