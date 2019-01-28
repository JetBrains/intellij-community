// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.content.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.content.*;
import com.intellij.util.EventDispatcher;
import com.intellij.util.SmartList;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class ContentManagerImpl implements ContentManager, PropertyChangeListener, Disposable.Parent {
  private static final Logger LOG = Logger.getInstance(ContentManagerImpl.class);

  private ContentUI myUI;
  private final List<Content> myContents = new ArrayList<>();
  private final EventDispatcher<ContentManagerListener> myDispatcher = EventDispatcher.create(ContentManagerListener.class);
  private final List<Content> mySelection = new ArrayList<>();
  private final boolean myCanCloseContents;

  private MyNonOpaquePanel myComponent;

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
    myProject = project;
    myCanCloseContents = canCloseContents;
    myUI = contentUI;
    myUI.setManager(this);

    // register on FileManager because before Content disposal the UsageView is disposed before which virtual file pointers should be externalized for which they need to be restored for which com.intellij.psi.impl.smartPointers.SelfElementInfo.restoreFileFromVirtual() must be able to work for which the findFile() must access filemanager for which it must be alive
    Disposer.register(((PsiManagerEx)PsiManager.getInstance(project)).getFileManager(), this);
    Disposer.register(this, contentUI);
  }

  @Override
  public boolean canCloseContents() {
    return myCanCloseContents;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    if (myComponent == null) {
      myComponent = new MyNonOpaquePanel();

      NonOpaquePanel contentComponent = new NonOpaquePanel();
      contentComponent.setContent(myUI.getComponent());

      myComponent.add(contentComponent, BorderLayout.CENTER);
    }
    return myComponent;
  }

  @NotNull
  @Override
  public ActionCallback getReady(@NotNull Object requestor) {
    Content selected = getSelectedContent();
    if (selected == null) return ActionCallback.DONE;
    BusyObject busyObject = selected.getBusyObject();
    return busyObject != null ? busyObject.getReady(requestor) : ActionCallback.DONE;
  }

  private class MyNonOpaquePanel extends NonOpaquePanel implements DataProvider {
    MyNonOpaquePanel() {
      super(new BorderLayout());
    }

    @Override
    @Nullable
    public Object getData(@NotNull @NonNls String dataId) {
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

  private void doAddContent(@NotNull final Content content, final int index) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myContents.contains(content)) {
      myContents.remove(content);
      myContents.add(index == -1 ? myContents.size() : index, content);
      return;
    }

    ((ContentImpl)content).setManager(this);
    final int insertIndex = index == -1 ? myContents.size() : index;
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
  public boolean removeContent(@NotNull Content content, final boolean dispose) {
    return removeContent(content, dispose, UIUtil.isFocusAncestor(content.getComponent()), false).isDone();
  }

  @NotNull
  @Override
  public ActionCallback removeContent(@NotNull Content content, boolean dispose, final boolean requestFocus, final boolean forcedFocus) {
    final ActionCallback result = new ActionCallback();
    doRemoveContent(content, dispose).doWhenDone(() -> {
      if (requestFocus) {
        Content current = getSelectedContent();
        if (current != null) {
          setSelectedContent(current, true, true, !forcedFocus).notify(result);
        }
        else {
          result.setDone();
        }
      }
      else {
        result.setDone();
      }
    });

    return result;
  }

  @NotNull
  private ActionCallback doRemoveContent(@NotNull Content content, boolean dispose) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    int indexToBeRemoved = getIndexOfContent(content);
    if (indexToBeRemoved == -1) return ActionCallback.REJECTED;

    try {
      Content selection = mySelection.isEmpty() ? null : mySelection.get(mySelection.size() - 1);
      int selectedIndex = selection != null ? myContents.indexOf(selection) : -1;

      if (!fireContentRemoveQuery(content, indexToBeRemoved)) {
        return ActionCallback.REJECTED;
      }
      if (!content.isValid()) {
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
      if (ApplicationManager.getApplication().isDispatchThread()) {
        if (!myDisposed) {
          myUI.getComponent().updateUI(); //cleanup visibleComponent from Alloy...TabbedPaneUI
        }
      }
    }
  }

  @Override
  public void removeAllContents(final boolean dispose) {
    Content[] contents = getContents();
    for (Content content : contents) {
      removeContent(content, dispose);
    }
  }

  @Override
  public int getContentCount() {
    return myContents.size();
  }

  @Override
  @NotNull
  public Content[] getContents() {
    return myContents.toArray(new Content[0]);
  }

  //TODO[anton,vova] is this method needed?
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
      if (Comparing.equal(component, content.getComponent())) {
        return content;
      }
    }
    return null;
  }

  @Override
  public int getIndexOfContent(@NotNull Content content) {
    return myContents.indexOf(content);
  }

  @NotNull
  @Override
  public String getCloseActionName() {
    return myUI.getCloseActionName();
  }

  @NotNull
  @Override
  public String getCloseAllButThisActionName() {
    return myUI.getCloseAllButThisActionName();
  }

  @NotNull
  @Override
  public String getPreviousContentActionName() {
    return myUI.getPreviousContentActionName();
  }

  @NotNull
  @Override
  public String getNextContentActionName() {
    return myUI.getNextContentActionName();
  }

  @NotNull
  @Override
  public List<AnAction> getAdditionalPopupActions(@NotNull final Content content) {
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
  public void addSelectedContent(@NotNull final Content content) {
    if (!checkSelectionChangeShouldBeProcessed(content, false)) return;

    if (getIndexOfContent(content) == -1) {
      throw new IllegalArgumentException("content not found: " + content);
    }
    if (!isSelected(content)) {
      mySelection.add(content);
      fireSelectionChanged(content, ContentManagerEvent.ContentOperation.add);
    }
  }

  private boolean checkSelectionChangeShouldBeProcessed(Content content, boolean implicit) {
    if (!myUI.canChangeSelectionTo(content, implicit)) {
      return false;
    }

    final boolean result = !isSelected(content) || myContentWithChangedComponent.contains(content);
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
  @NotNull
  public Content[] getSelectedContents() {
    return mySelection.toArray(new Content[0]);
  }

  @Override
  @Nullable
  public Content getSelectedContent() {
    return mySelection.isEmpty() ? null : mySelection.get(0);
  }

  @Override
  public void setSelectedContent(@NotNull Content content, boolean requestFocus) {
    setSelectedContentCB(content, requestFocus);
  }

  @NotNull
  @Override
  public ActionCallback setSelectedContentCB(@NotNull final Content content, final boolean requestFocus) {
    return setSelectedContentCB(content, requestFocus, true);
  }

  @Override
  public void setSelectedContent(@NotNull Content content, boolean requestFocus, boolean forcedFocus) {
    setSelectedContentCB(content, requestFocus, forcedFocus);
  }

  @NotNull
  @Override
  public ActionCallback setSelectedContentCB(@NotNull final Content content, final boolean requestFocus, final boolean forcedFocus) {
    return setSelectedContent(content, requestFocus, forcedFocus, false);
  }

  @NotNull
  @Override
  public ActionCallback setSelectedContent(@NotNull final Content content, final boolean requestFocus, final boolean forcedFocus, boolean implicit) {
    mySelectionHistory.remove(content);
    mySelectionHistory.add(0, content);
    if (isSelected(content) && requestFocus) {
      return requestFocus(content, forcedFocus);
    }

    if (!checkSelectionChangeShouldBeProcessed(content, implicit)) {
      return ActionCallback.REJECTED;
    }
    if (!myContents.contains(content)) {
      throw new IllegalArgumentException("Cannot find content:" + content.getDisplayName());
    }

    final boolean focused = isSelectionHoldsFocus();

    final Content[] old = getSelectedContents();

    final ActiveRunnable selection = new ActiveRunnable() {
      @NotNull
      @Override
      public ActionCallback run() {
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
        return getFocusManager().requestFocus(getComponent(), true).doWhenProcessed(() -> selection.run().notify(result));
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

  @NotNull
  @Override
  public ActionCallback setSelectedContentCB(@NotNull Content content) {
    return setSelectedContentCB(content, false);
  }

  @Override
  public void setSelectedContent(@NotNull final Content content) {
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

  @NotNull
  @Override
  public ActionCallback requestFocus(final Content content, final boolean forced) {
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
  public void addDataProvider(@NotNull final DataProvider provider) {
    dataProviders.add(provider);
  }

  @Override
  public void propertyChange(@NotNull PropertyChangeEvent event) {
    if (Content.PROP_COMPONENT.equals(event.getPropertyName())) {
      myContentWithChangedComponent.add((Content)event.getSource());
    }
  }

  @Override
  @NotNull
  public ContentFactory getFactory() {
    return ServiceManager.getService(ContentFactory.class);
  }

  @Override
  public void beforeTreeDispose() {
    if (myDisposed) return;
    myUI.beforeDispose();
  }

  @Override
  public void dispose() {
    if (myDisposed) return;
    myDisposed = true;

    myContents.clear();
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
}
