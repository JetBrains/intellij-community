// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.content.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.BusyObject;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.content.*;
import com.intellij.util.EventDispatcher;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;

public class ContentManagerImpl implements ContentManager, PropertyChangeListener, Disposable.Parent {
  private static final Logger LOG = Logger.getInstance(ContentManagerImpl.class);

  private ContentUI myUI;
  private final List<Content> contents = new ArrayList<>();
  private final List<ContentManagerImpl> myNestedManagers = new SmartList<>();
  private final EventDispatcher<ContentManagerListener> myDispatcher = EventDispatcher.create(ContentManagerListener.class);
  private final List<Content> mySelection = new ArrayList<>();
  private final boolean myCanCloseContents;

  private JPanel myComponent;

  private final Set<Content> myContentWithChangedComponent = new HashSet<>();

  private boolean myDisposed;
  private final Project myProject;

  private final List<UiDataProvider> myDataProviders = new SmartList<>();
  private final ArrayList<Content> mySelectionHistory = new ArrayList<>();

  /**
   * WARNING: as this class adds listener to the ProjectManager which is removed on projectClosed event, all instances of this class
   * must be created on already OPENED projects, otherwise there will be memory leak!
   */
  public ContentManagerImpl(@NotNull ContentUI contentUI, boolean canCloseContents, @NotNull Project project) {
    this(contentUI, canCloseContents, project, project);
  }

  public ContentManagerImpl(@NotNull ContentUI contentUI,
                            boolean canCloseContents,
                            @NotNull Project project,
                            @NotNull Disposable parentDisposable) {
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
  public ContentManagerImpl(boolean canCloseContents,
                            @NotNull Project project,
                            @NotNull Disposable parentDisposable,
                            @NotNull ContentUiProducer contentUiProducer) {
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

  public static @Nullable ContentManager getContentManager(@Nullable Component component) {
    return component instanceof MyNonOpaquePanel o ? o.getContentManager() : null;
  }

  private final class MyNonOpaquePanel extends JBPanelWithEmptyText implements UiDataProvider {
    MyNonOpaquePanel() {
      super(new BorderLayout());

      setOpaque(false);
    }

    @NotNull ContentManager getContentManager() {
      return ContentManagerImpl.this;
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      for (Object dataProvider : ContainerUtil.concat(myDataProviders, Arrays.asList(myUI, DataManager.getDataProvider(this)))) {
        DataSink.uiDataSnapshot(sink, dataProvider);
      }
      sink.set(PlatformDataKeys.CONTENT_MANAGER, ContentManagerImpl.this);
      if (getContentCount() > 1) {
        sink.set(PlatformDataKeys.NONEMPTY_CONTENT_MANAGER, ContentManagerImpl.this);
      }
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
    ThreadingAssertions.assertEventDispatchThread();
    String tabName = content.getTabName();
    if (tabName != null && myUI instanceof ToolWindowContentUi toolWindowContentUi) {
      ToolWindowContentPostProcessor contentReplacer = ToolWindowContentPostProcessor.EP_NAME
        .findFirstSafe(ep -> ep.isEnabled(myProject, content, toolWindowContentUi.getWindow()));
      if (contentReplacer != null) {
        contentReplacer.postprocessContent(myProject, content, toolWindowContentUi.getWindow());
      }
    }

    if (contents.contains(content)) {
      contents.remove(content);
      contents.add(index < 0 ? contents.size() : index, content);
      return;
    }

    if (!Content.TEMPORARY_REMOVED_KEY.get(content, false) && getContentCount() == 0 && !isEmpty()) {
      ContentManager oldManager = content.getManager();
      for (ContentManagerImpl nestedManager : myNestedManagers) {
        if (!nestedManager.isEmpty()) {
          nestedManager.doAddContent(content, index);
          if (content.getManager() != oldManager) {
            return;
          }
        }
      }
    }

    ((ContentImpl)content).setManager(this);
    final int insertIndex = index < 0 ? contents.size() : index;
    contents.add(insertIndex, content);
    content.addPropertyChangeListener(this);
    fireContentAdded(content, insertIndex);
    if (myUI.isToSelectAddedContent() || mySelection.isEmpty() && !myUI.canBeEmptySelection()) {
      if (myUI.isSingleSelection()) {
        setSelectedContent(content);
      }
      else {
        addSelectedContent(content);
      }
      if (myComponent != null && myComponent.isFocusOwner() && contents.size() == 1) {
        requestFocus(content, true);
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
        if (current == null) {
          ToolWindowManager.getInstance(myProject).activateEditorComponent();
          result.setDone();
        }
        else {
          setSelectedContent(current, true, true, !forcedFocus).notify(result);
        }
      }
      else {
        result.setDone();
      }
    });

    return result;
  }

  private @NotNull ActionCallback doRemoveContent(@NotNull Content content, boolean dispose) {
    ThreadingAssertions.assertEventDispatchThread();
    int indexToBeRemoved = getIndexOfContent(content);
    if (indexToBeRemoved == -1) {
      return ActionCallback.REJECTED;
    }

    try {
      Content selection = mySelection.isEmpty() ? null : mySelection.get(mySelection.size() - 1);
      int selectedIndex = selection != null ? contents.indexOf(selection) : -1;

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
      contents.remove(content);
      content.removePropertyChangeListener(this);

      fireContentRemoved(content, indexToBeRemoved);
      ((ContentImpl)content).setManager(null);

      if (dispose) {
        Disposer.dispose(content);
      }

      int newSize = contents.size();
      if (newSize > 0) {
        if (indexToSelect > -1) {
          final Content toSelect = !mySelectionHistory.isEmpty() ? mySelectionHistory.get(0) : contents.get(indexToSelect);
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
      if (ApplicationManager.getApplication().isDispatchThread() && !myDisposed && contents.isEmpty()) {
        // cleanup visibleComponent in TabbedPaneUI only if there is no content left,
        // otherwise immediate adding of a new content will lead to having visible two TabWrapper component at the same time.
        myUI.getComponent().updateUI(); //cleanup visibleComponent from Alloy...TabbedPaneUI
      }
    }
  }

  @Override
  public void removeAllContents(boolean dispose) {
    if (contents.isEmpty()) {
      return;
    }

    for (Content content : List.copyOf(contents)) {
      removeContent(content, dispose);
    }
  }

  @Override
  public int getContentCount() {
    return contents.size();
  }

  @Override
  public boolean isEmpty() {
    boolean empty = ContentManager.super.isEmpty();
    if (!empty) {
      return false;
    }
    for (ContentManager manager : myNestedManagers) {
      if (!manager.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public Content @NotNull [] getContents() {
    return contents.toArray(new Content[0]);
  }

  public int getRecursiveContentCount() {
    var count = contents.size();
    for (ContentManagerImpl nestedManager : myNestedManagers) {
      count += nestedManager.getRecursiveContentCount();
    }
    return count;
  }

  @Override
  public @NotNull List<@NotNull Content> getContentsRecursively() {
    List<Content> list = new ArrayList<>();
    collectContentsRecursively(list);
    return list;
  }

  private void collectContentsRecursively(@NotNull List<@NotNull Content> to) {
    to.addAll(contents);
    for (ContentManagerImpl nestedManager : myNestedManagers) {
      nestedManager.collectContentsRecursively(to);
    }
  }

  @Override
  public Content findContent(String displayName) {
    for (Content content : contents) {
      if (content.getDisplayName().equals(displayName)) {
        return content;
      }
    }
    return null;
  }

  @Override
  public Content getContent(int index) {
    return index >= 0 && index < contents.size() ? contents.get(index) : null;
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
    return contents.indexOf(content);
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
    for (Content content : contents) {
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
  public @NotNull ActionCallback setSelectedContentCB(final @NotNull Content content,
                                                      final boolean requestFocus,
                                                      final boolean forcedFocus) {
    return setSelectedContent(content, requestFocus, forcedFocus, false);
  }

  @Override
  public @NotNull ActionCallback setSelectedContent(final @NotNull Content content,
                                                    final boolean requestFocus,
                                                    final boolean forcedFocus,
                                                    boolean implicit) {
    mySelectionHistory.remove(content);
    mySelectionHistory.add(0, content);
    if (isSelected(content) && requestFocus) {
      requestFocusWithFallback(content);
      return ActionCallback.DONE;
    }

    if (!checkSelectionChangeShouldBeProcessed(content, implicit)) {
      return ActionCallback.REJECTED;
    }
    if (!contents.contains(content)) {
      for (ContentManagerImpl manager : myNestedManagers) {
        ActionCallback nestedCallback = manager.setSelectedContent(content, requestFocus, forcedFocus, implicit);
        if (nestedCallback != ActionCallback.REJECTED) return nestedCallback;
      }
      return ActionCallback.REJECTED;
    }

    final boolean focused = isSelectionHoldsFocus();

    final Content[] old = getSelectedContents();

    if (myDisposed || getIndexOfContent(content) == -1) return ActionCallback.REJECTED;

    for (Content each : old) {
      removeFromSelection(each);
    }

    addSelectedContent(content);

    if (requestFocus || focused) {
      requestFocusWithFallback(content);
    }
    return ActionCallback.DONE;
  }

  private boolean isSelectionHoldsFocus() {
    boolean focused = false;
    final Content[] selection = getSelectedContents();
    for (Content each : selection) {
      if (isFocusAncestorStrict(each.getComponent())) {
        focused = true;
        break;
      }
    }
    return focused;
  }

  private static boolean isFocusAncestorStrict(JComponent component) {
    Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (owner == null) return false;
    return SwingUtilities.isDescendingFrom(owner, component);
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
    if (Registry.is("ide.content.manager.listeners.order.fix")) {
      myDispatcher.getListeners().add(l);
      return;
    }
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
    assert contents.contains(toSelect);
    JComponent preferredFocusableComponent = toSelect.getPreferredFocusableComponent();
    return preferredFocusableComponent != null
           ? getFocusManager().requestFocusInProject(preferredFocusableComponent, myProject)
           : ActionCallback.REJECTED;
  }

  /**
   * Focus the content if it defines a preferred focused component, focus our root panel otherwise.
   */
  private void requestFocusWithFallback(Content content) {
    requestFocus(content, true).doWhenRejected(() -> {
      getFocusManager().requestFocusInProject(getComponent(), myProject);
    });
  }

  private IdeFocusManager getFocusManager() {
    return IdeFocusManager.getInstance(myProject);
  }

  @Override
  public void addDataProvider(@NotNull DataProvider provider) {
    addUiDataProvider(Utils.wrapToUiDataProvider(provider));
  }

  @Override
  public void addUiDataProvider(@NotNull UiDataProvider provider) {
    myDataProviders.add(provider);
  }

  @Override
  public void propertyChange(@NotNull PropertyChangeEvent event) {
    if (Content.PROP_COMPONENT.equals(event.getPropertyName())) {
      myContentWithChangedComponent.add((Content)event.getSource());
    }
  }

  @Override
  public @NotNull ContentFactory getFactory() {
    return ContentFactory.getInstance();
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

    contents.clear();
    myNestedManagers.clear();
    mySelection.clear();
    myContentWithChangedComponent.clear();
    myUI = null;
    myDispatcher.getListeners().clear();
    myDataProviders.clear();
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

  public @Nullable ContentUI getUI() {
    return myUI;
  }
}
