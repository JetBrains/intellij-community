/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui.content.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.wm.FocusCommand;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.content.*;
import com.intellij.ui.switcher.SwitchProvider;
import com.intellij.ui.switcher.SwitchTarget;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class ContentManagerImpl implements ContentManager, PropertyChangeListener, Disposable.Parent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.content.impl.ContentManagerImpl");

  private ContentUI myUI;
  private final ArrayList<Content> myContents;
  private EventListenerList myListeners;
  private List<Content> mySelection = new ArrayList<Content>();
  private final boolean myCanCloseContents;

  private MyContentComponent myContentComponent;
  private MyFocusProxy myFocusProxy;
  private JPanel myComponent;


  private final Set<Content> myContentWithChangedComponent = new HashSet<Content>();

  private boolean myDisposed;
  private final Project myProject;

  /**
   * WARNING: as this class adds listener to the ProjectManager which is removed on projectClosed event, all instances of this class
   * must be created on already OPENED projects, otherwise there will be memory leak!
   */
  public ContentManagerImpl(@NotNull ContentUI contentUI, boolean canCloseContents, @NotNull Project project) {
    myProject = project;
    myCanCloseContents = canCloseContents;
    myContents = new ArrayList<Content>();
    myListeners = new EventListenerList();
    myUI = contentUI;
    myUI.setManager(this);

    Disposer.register(project, this);
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
      myComponent = new NonOpaquePanel(new BorderLayout());

      myFocusProxy = new MyFocusProxy();
      myContentComponent = new MyContentComponent();
      myContentComponent.setContent(myUI.getComponent());
      myContentComponent.setFocusCycleRoot(true);

      myComponent.add(myFocusProxy, BorderLayout.NORTH);
      myComponent.add(myContentComponent, BorderLayout.CENTER);
    }
    return myComponent;
  }

  @Override
  public ActionCallback getReady(@NotNull Object requestor) {
    Content selected = getSelectedContent();
    if (selected == null) return new ActionCallback.Done();
    BusyObject busyObject = selected.getBusyObject();
    return busyObject != null ? busyObject.getReady(requestor) : new ActionCallback.Done();
  }

  private class MyContentComponent extends NonOpaquePanel implements DataProvider, SwitchProvider {

    private final List<DataProvider> myProviders = new ArrayList<DataProvider>();

    public void addProvider(final DataProvider provider) {
      myProviders.add(provider);
    }

    @Override
    @Nullable
    public Object getData(@NonNls final String dataId) {
      if (PlatformDataKeys.CONTENT_MANAGER.is(dataId)) return ContentManagerImpl.this;
      if (PlatformDataKeys.NONEMPTY_CONTENT_MANAGER.is(dataId) && getContentCount() > 1) {
        return ContentManagerImpl.this;
      }

      for (DataProvider each : myProviders) {
        final Object data = each.getData(dataId);
        if (data != null) return data;
      }

      if (myUI instanceof DataProvider) {
        return ((DataProvider)myUI).getData(dataId);
      }

      return null;
    }

    @Override
    public List<SwitchTarget> getTargets(boolean onlyVisible, boolean originalProvider) {
      if (myUI instanceof SwitchProvider) {
        return ((SwitchProvider)myUI).getTargets(onlyVisible, false);
      }
      return new ArrayList<SwitchTarget>();
    }

    @Override
    public SwitchTarget getCurrentTarget() {
      if (myUI instanceof SwitchProvider) {
        return ((SwitchProvider)myUI).getCurrentTarget();
      }

      return null;
    }

    @Override
    public JComponent getComponent() {
      if (myUI instanceof SwitchProvider) {
        return myUI.getComponent();
      }

      return this;
    }

    @Override
    public boolean isCycleRoot() {
      return myUI instanceof SwitchProvider && ((SwitchProvider)myUI).isCycleRoot();
    }
  }

  private class MyFocusProxy extends Wrapper.FocusHolder implements DataProvider {
    private MyFocusProxy() {
      setOpaque(false);
      setPreferredSize(new Dimension(0, 0));
    }

    @Override
    @Nullable
    public Object getData(@NonNls final String dataId) {
      return myContentComponent.getData(dataId);
    }
  }

  @Override
  public void addContent(@NotNull Content content, final int order) {
    addContent(content, null, order);
  }

  @Override
  public void addContent(@NotNull Content content) {
    addContent(content, null, -1);
  }

  @Override
  public void addContent(@NotNull final Content content, final Object constraints) {
    addContent(content, constraints, -1);
  }

  private void addContent(@NotNull final Content content, final Object constraints, final int index) {
    if (myContents.contains(content)) return;

    ((ContentImpl)content).setManager(this);
    final int insertIndex = index == -1 ? myContents.size() : index;
    myContents.add(insertIndex, content);
    content.addPropertyChangeListener(this);
    fireContentAdded(content, insertIndex, ContentManagerEvent.ContentOperation.add);
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
    return removeContent(content, true, dispose);
  }

  @NotNull
  @Override
  public ActionCallback removeContent(@NotNull Content content, boolean dispose, final boolean trackFocus, final boolean forcedFocus) {
    final ActionCallback result = new ActionCallback();
    _removeContent(content, true, dispose).doWhenDone(new Runnable() {
      @Override
      public void run() {
        if (trackFocus) {
          Content current = getSelectedContent();
          if (current != null) {
            setSelectedContent(current, true, true, !forcedFocus);
          } else {
            result.setDone();
          }
        } else {
          result.setDone();
        }
      }
    });

    return result;
  }

  private boolean removeContent(final Content content, boolean trackSelection, boolean dispose) {
    return _removeContent(content, trackSelection, dispose).isDone();
  }

  private ActionCallback _removeContent(Content content, boolean trackSelection, boolean dispose) {
    int indexToBeRemoved = getIndexOfContent(content);
    if (indexToBeRemoved == -1) return new ActionCallback.Rejected();

    try {
      Content selection = mySelection.isEmpty() ? null : mySelection.get(mySelection.size() - 1);
      int selectedIndex = selection != null ? myContents.indexOf(selection) : -1;

      if (!fireContentRemoveQuery(content, indexToBeRemoved, ContentManagerEvent.ContentOperation.undefined)) {
        return new ActionCallback.Rejected();
      }
      if (!content.isValid()) {
        return new ActionCallback.Rejected();
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

      myContents.remove(content);
      content.removePropertyChangeListener(this);

      fireContentRemoved(content, indexToBeRemoved, ContentManagerEvent.ContentOperation.remove);
      ((ContentImpl)content).setManager(null);


      if (dispose) {
        Disposer.dispose(content);
      }

      int newSize = myContents.size();

      ActionCallback result = new ActionCallback();

      if (newSize > 0 && trackSelection) {
        if (indexToSelect > -1) {
          final Content toSelect = myContents.get(indexToSelect);
          if (!isSelected(toSelect)) {
            if (myUI.isSingleSelection()) {
              setSelectedContentCB(toSelect).notify(result);
            }
            else {
              addSelectedContent(toSelect);
              result.setDone();
            }
          }
        }
      }
      else {
        mySelection.clear();
      }

      return result;
    }
    finally {
      if (ApplicationManager.getApplication().isDispatchThread()) {
        myUI.getComponent().updateUI(); //cleanup visibleComponent from Alloy...TabbedPaneUI
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
    return myContents.toArray(new Content[myContents.size()]);
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
  public Content getContent(JComponent component) {
    Content[] contents = getContents();
    for (Content content : contents) {
      if (Comparing.equal(component, content.getComponent())) {
        return content;
      }
    }
    return null;
  }

  @Override
  public int getIndexOfContent(Content content) {
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

  @Override
  public List<AnAction> getAdditionalPopupActions(@NotNull final Content content) {
    return null;
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
    return mySelection.toArray(new Content[mySelection.size()]);
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
    if (isSelected(content) && requestFocus) {
      return requestFocus(content, forcedFocus);
    }

    if (!checkSelectionChangeShouldBeProcessed(content, implicit)) {
      return new ActionCallback.Rejected();
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
        if (myDisposed || getIndexOfContent(content) == -1) return new ActionCallback.Rejected();

        for (Content each : old) {
          removeFromSelection(each);
        }

        addSelectedContent(content);

        if (requestFocus) {
          return requestFocus(content, forcedFocus);
        }
        return new ActionCallback.Done();
      }
    };

    final ActionCallback result = new ActionCallback();
    boolean enabledFocus = getFocusManager().isFocusTransferEnabled();
    if (focused || requestFocus) {
      if (enabledFocus) {
        return getFocusManager().requestFocus(myFocusProxy, true).doWhenProcessed(new Runnable() {
          @Override
          public void run() {
            selection.run().notify(result);
          }
        });
      }
      return selection.run().notify(result);
    }
    else {
      return selection.run().notify(result);
    }
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
    int index = getIndexOfContent(selectedContent);
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
    int index = getIndexOfContent(selectedContent);
    index = (index + 1) % contentCount;
    final Content content = getContent(index);
    if (content == null) {
      return null;
    }
    return setSelectedContentCB(content, true);
  }

  @Override
  public void addContentManagerListener(@NotNull ContentManagerListener l) {
    myListeners.add(ContentManagerListener.class, l);
  }

  @Override
  public void removeContentManagerListener(@NotNull ContentManagerListener l) {
    myListeners.remove(ContentManagerListener.class, l);
  }


  private void fireContentAdded(Content content, int newIndex, ContentManagerEvent.ContentOperation operation) {
    ContentManagerEvent event = new ContentManagerEvent(this, content, newIndex, operation);
    ContentManagerListener[] listeners = myListeners.getListeners(ContentManagerListener.class);
    for (ContentManagerListener listener : listeners) {
      listener.contentAdded(event);
    }
  }

  private void fireContentRemoved(Content content, int oldIndex, ContentManagerEvent.ContentOperation operation) {
    ContentManagerEvent event = new ContentManagerEvent(this, content, oldIndex, operation);
    ContentManagerListener[] listeners = myListeners.getListeners(ContentManagerListener.class);
    for (ContentManagerListener listener : listeners) {
      listener.contentRemoved(event);
    }
  }

  private void fireSelectionChanged(Content content, ContentManagerEvent.ContentOperation operation) {
    ContentManagerEvent event = new ContentManagerEvent(this, content, myContents.indexOf(content), operation);
    ContentManagerListener[] listeners = myListeners.getListeners(ContentManagerListener.class);
    for (ContentManagerListener listener : listeners) {
      listener.selectionChanged(event);
    }
  }

  private boolean fireContentRemoveQuery(Content content, int oldIndex, ContentManagerEvent.ContentOperation operation) {
    ContentManagerEvent event = new ContentManagerEvent(this, content, oldIndex, operation);
    ContentManagerListener[] listeners = myListeners.getListeners(ContentManagerListener.class);
    for (ContentManagerListener listener : listeners) {
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
    if (toSelect == null) return new ActionCallback.Rejected();
    assert myContents.contains(toSelect);


    return getFocusManager().requestFocus(new FocusCommand(content, toSelect.getPreferredFocusableComponent()) {
      @NotNull
      @Override
      public ActionCallback run() {
        return doRequestFocus(toSelect);
      }
    }, forced);
  }

  private IdeFocusManager getFocusManager() {
    return IdeFocusManager.getInstance(myProject);
  }

  private static ActionCallback doRequestFocus(final Content toSelect) {
    JComponent toFocus = computeWillFocusComponent(toSelect);

    if (toFocus != null) {
      toFocus.requestFocus();
    }

    return new ActionCallback.Done();
  }

  private static JComponent computeWillFocusComponent(Content toSelect) {
    JComponent toFocus = toSelect.getPreferredFocusableComponent();
    if (toFocus != null) {
      toFocus = IdeFocusTraversalPolicy.getPreferredFocusedComponent(toFocus);
    }

    if (toFocus == null) toFocus = toSelect.getPreferredFocusableComponent();
    return toFocus;
  }

  @Override
  public void addDataProvider(@NotNull final DataProvider provider) {
    myContentComponent.addProvider(provider);
  }

  @Override
  public void propertyChange(final PropertyChangeEvent evt) {
    if (Content.PROP_COMPONENT.equals(evt.getPropertyName())) {
      myContentWithChangedComponent.add((Content)evt.getSource());
    }
  }

  @Override
  @NotNull
  public ContentFactory getFactory() {
    return ServiceManager.getService(ContentFactory.class);
  }

  @Override
  public void beforeTreeDispose() {
    myUI.beforeDispose();
  }

  @Override
  public void dispose() {
    myDisposed = true;

    myContents.clear();
    mySelection = null;
    myContentWithChangedComponent.clear();
    myUI = null;
    myListeners = null;
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
