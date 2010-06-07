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
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.FocusCommand;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.content.*;
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
  private ArrayList<Content> myContents;
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
  public ContentManagerImpl(ContentUI contentUI, boolean canCloseContents, Project project) {
    myProject = project;
    myCanCloseContents = canCloseContents;
    myContents = new ArrayList<Content>();
    myListeners = new EventListenerList();
    myUI = contentUI;
    myUI.setManager(this);

    Disposer.register(project, this);
    Disposer.register(this, contentUI);
  }

  public boolean canCloseContents() {
    return myCanCloseContents;
  }

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

  private class MyContentComponent extends NonOpaquePanel implements DataProvider {

    private final List<DataProvider> myProviders = new ArrayList<DataProvider>();

    public void addProvider(final DataProvider provider) {
      myProviders.add(provider);
    }

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
      return null;
    }
  }

  private class MyFocusProxy extends Wrapper.FocusHolder implements DataProvider {
    private MyFocusProxy() {
      setOpaque(false);
      setPreferredSize(new Dimension(0, 0));
    }

    @Nullable
    public Object getData(@NonNls final String dataId) {
      return myContentComponent.getData(dataId);
    }
  }

  public void addContent(@NotNull Content content, final int order) {
    addContent(content, null, order);
  }

  public void addContent(@NotNull Content content) {
    addContent(content, null, -1);
  }

  public void addContent(@NotNull final Content content, final Object constraints) {
    addContent(content, constraints, -1);
  }

  private void addContent(@NotNull final Content content, final Object constraints, final int index) {
    if (myContents.contains(content)) return;

    ((ContentImpl)content).setManager(this);
    final int insertIndex = (index == -1) ? myContents.size() : index;
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

  public boolean removeContent(@NotNull Content content, final boolean dispose) {
    return removeContent(content, true, dispose);
  }

  private boolean removeContent(final Content content, boolean trackSelection, boolean dispose) {
    int indexToBeRemoved = getIndexOfContent(content);
    if (indexToBeRemoved == -1) return false;

    try {
      Content selection = mySelection.isEmpty() ? null : mySelection.get(mySelection.size() - 1);
      int selectedIndex = selection != null ? myContents.indexOf(selection) : -1;

      if (!fireContentRemoveQuery(content, indexToBeRemoved, ContentManagerEvent.ContentOperation.undefined)) {
        return false;
      }
      if (!content.isValid()) {
        return false; // the content has already been invalidated by another thread or something
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

      int newSize = myContents.size();
      if (newSize > 0 && trackSelection) {
        if (indexToSelect > -1) {
          final Content toSelect = myContents.get(indexToSelect);
          if (!isSelected(toSelect)) {
            if (myUI.isSingleSelection()) {
              setSelectedContent(toSelect);
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
      fireContentRemoved(content, indexToBeRemoved, ContentManagerEvent.ContentOperation.remove);
      ((ContentImpl)content).setManager(null);


      if (dispose) {
        Disposer.dispose(content);
      }

      return true;
    }
    finally {
      if (ApplicationManager.getApplication().isDispatchThread()) {
        myUI.getComponent().updateUI(); //cleanup visibleComponent from Alloy...TabbedPaneUI
      }
    }
  }

  public void removeAllContents(final boolean dispose) {
    Content[] contents = getContents();
    for (Content content : contents) {
      removeContent(content, dispose);
    }
  }

  public int getContentCount() {
    return myContents.size();
  }

  @NotNull
  public Content[] getContents() {
    return myContents.toArray(new Content[myContents.size()]);
  }

  //TODO[anton,vova] is this method needed?
  public Content findContent(String displayName) {
    for (Content content : myContents) {
      if (content.getDisplayName().equals(displayName)) {
        return content;
      }
    }
    return null;
  }

  public Content getContent(int index) {
    return index >= 0 && index < myContents.size() ? myContents.get(index) : null;
  }

  public Content getContent(JComponent component) {
    Content[] contents = getContents();
    for (Content content : contents) {
      if (Comparing.equal(component, content.getComponent())) {
        return content;
      }
    }
    return null;
  }

  public int getIndexOfContent(Content content) {
    return myContents.indexOf(content);
  }

  public String getCloseActionName() {
    return UIBundle.message("tabbed.pane.close.tab.action.name");
  }


  public String getCloseAllButThisActionName() {
    return UIBundle.message("tabbed.pane.close.all.tabs.but.this.action.name");
  }

  public List<AnAction> getAdditionalPopupActions(@NotNull final Content content) {
    return null;
  }

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

  public void removeFromSelection(@NotNull Content content) {
    if (!isSelected(content)) return;
    mySelection.remove(content);
    fireSelectionChanged(content, ContentManagerEvent.ContentOperation.remove);
  }

  public boolean isSelected(@NotNull Content content) {
    return mySelection.contains(content);
  }

  @NotNull
  public Content[] getSelectedContents() {
    return mySelection.toArray(new Content[mySelection.size()]);
  }

  @Nullable
  public Content getSelectedContent() {
    return mySelection.isEmpty() ? null : mySelection.get(0);
  }

  public void setSelectedContent(@NotNull Content content, boolean requestFocus) {
    setSelectedContentCB(content, requestFocus);
  }

  public ActionCallback setSelectedContentCB(@NotNull final Content content, final boolean requestFocus) {
    return setSelectedContentCB(content, requestFocus, true);
  }

  public void setSelectedContent(@NotNull Content content, boolean requestFocus, boolean forcedFocus) {
    setSelectedContentCB(content, requestFocus, forcedFocus);
  }

  public ActionCallback setSelectedContentCB(@NotNull final Content content, final boolean requestFocus, final boolean forcedFocus) {
    return setSelectedContent(content, requestFocus, forcedFocus, false);
  }

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

    Runnable selection = new Runnable() {
      public void run() {
        if (myDisposed || getIndexOfContent(content) == -1) return;

        for (Content each : old) {
          removeFromSelection(each);
        }

        addSelectedContent(content);

        if (requestFocus) {
          requestFocus(content, forcedFocus);
        }
      }
    };

    if (focused || requestFocus) {
      return getFocusManager().requestFocus(myFocusProxy, true).doWhenProcessed(selection);
    }
    else {
      selection.run();
      return new ActionCallback.Done();
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

  public ActionCallback setSelectedContentCB(@NotNull Content content) {
    return setSelectedContentCB(content, false);
  }

  public void setSelectedContent(@NotNull final Content content) {
    setSelectedContentCB(content); 
  }

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

  public void addContentManagerListener(@NotNull ContentManagerListener l) {
    myListeners.add(ContentManagerListener.class, l);
  }

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

  public ActionCallback requestFocus(final Content content, final boolean forced) {
    final Content toSelect = content == null ? getSelectedContent() : content;
    if (toSelect == null) return new ActionCallback.Rejected();
    assert myContents.contains(toSelect);


    return getFocusManager().requestFocus(new FocusCommand(content, toSelect.getPreferredFocusableComponent()) {
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

  public void addDataProvider(@NotNull final DataProvider provider) {
    myContentComponent.addProvider(provider);
  }

  public void propertyChange(final PropertyChangeEvent evt) {
    if (Content.PROP_COMPONENT.equals(evt.getPropertyName())) {
      myContentWithChangedComponent.add((Content)evt.getSource());
    }
  }

  @NotNull
  public ContentFactory getFactory() {
    return ServiceManager.getService(ContentFactory.class);
  }

  public void beforeTreeDispose() {
    myUI.beforeDispose();
  }

  public void dispose() {
    myDisposed = true;

    myContents = null;
    mySelection = null;
    myContentWithChangedComponent.clear();
    myUI = null;
    myListeners = null;
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  public boolean isSingleSelection() {
    return myUI.isSingleSelection();
  }
}
