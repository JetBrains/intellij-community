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
package com.intellij.openapi.wm.impl;

import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowContentUiType;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.impl.ContentImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ToolWindowImpl implements ToolWindowEx {
  private final PropertyChangeSupport myChangeSupport;
  private final ToolWindowManagerImpl myToolWindowManager;
  private final String myId;
  private final JComponent myComponent;
  private boolean myAvailable;
  private final ContentManager myContentManager;
  private Icon myIcon = null;

  private static final Content EMPTY_CONTENT = new ContentImpl(new JLabel(), "", false);
  private final ToolWindowContentUi myContentUI;

  private InternalDecorator myDecorator;

  private boolean myHideOnEmptyContent = false;
  private boolean myPlaceholderMode;
  private ToolWindowFactory myContentFactory;

  private ActionCallback myActivation = new ActionCallback.Done();

  ToolWindowImpl(final ToolWindowManagerImpl toolWindowManager, final String id, boolean canCloseContent, @Nullable final JComponent component) {
    myToolWindowManager = toolWindowManager;
    myChangeSupport = new PropertyChangeSupport(this);
    myId = id;
    myAvailable = true;

    final ContentFactory contentFactory = ServiceManager.getService(ContentFactory.class);
    myContentUI = new ToolWindowContentUi(this);
    myContentManager =
      contentFactory.createContentManager(myContentUI, canCloseContent, toolWindowManager.getProject());

    if (component != null) {
      final Content content = contentFactory.createContent(component, "", false);
      myContentManager.addContent(content);
      myContentManager.setSelectedContent(content, false);
    }

    myComponent = myContentManager.getComponent();
  }

  public final void addPropertyChangeListener(final PropertyChangeListener l) {
    myChangeSupport.addPropertyChangeListener(l);
  }

  public final void removePropertyChangeListener(final PropertyChangeListener l) {
    myChangeSupport.removePropertyChangeListener(l);
  }

  public final void activate(final Runnable runnable) {
    activate(runnable, true);
  }

  public void activate(@Nullable final Runnable runnable, final boolean autoFocusContents) {
    activate(runnable, autoFocusContents, true);
  }

  public void activate(@Nullable final Runnable runnable, boolean autoFocusContents, boolean forced) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myToolWindowManager.activateToolWindow(myId, forced, autoFocusContents);

    if (runnable != null) {
      getActivation().doWhenDone(new Runnable() {
        public void run() {
          myToolWindowManager.invokeLater(runnable);
        }
      });
    }
  }

  public final boolean isActive() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myToolWindowManager.isToolWindowActive(myId);
  }

  public final void show(final Runnable runnable) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myToolWindowManager.showToolWindow(myId);
    if (runnable != null) {
      getActivation().doWhenDone(new Runnable() {
        public void run() {
          myToolWindowManager.invokeLater(runnable);
        }
      });
    }
  }

  public final void hide(final Runnable runnable) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myToolWindowManager.hideToolWindow(myId, false);
    if (runnable != null) {
      myToolWindowManager.invokeLater(runnable);
    }
  }

  public final boolean isVisible() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myToolWindowManager.isToolWindowVisible(myId);
  }

  public final ToolWindowAnchor getAnchor() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myToolWindowManager.getToolWindowAnchor(myId);
  }

  public final void setAnchor(final ToolWindowAnchor anchor, final Runnable runnable) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myToolWindowManager.setToolWindowAnchor(myId, anchor);
    if (runnable != null) {
      myToolWindowManager.invokeLater(runnable);
    }
  }

  public boolean isSplitMode() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myToolWindowManager.isSplitMode(myId);
  }

  public void setContentUiType(ToolWindowContentUiType type, Runnable runnable) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myToolWindowManager.setContentUiType(myId, type);
    if (runnable != null) {
      myToolWindowManager.invokeLater(runnable);
    }
  }

  public void setDefaultContentUiType(@NotNull ToolWindowContentUiType type) {
    myToolWindowManager.setDefaultContentUiType(this, type);
  }

  public ToolWindowContentUiType getContentUiType() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myToolWindowManager.getContentUiType(myId);
  }

  public void setSplitMode(final boolean isSideTool, @Nullable final Runnable runnable) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myToolWindowManager.setSideTool(myId, isSideTool);
    if (runnable != null) {
      myToolWindowManager.invokeLater(runnable);
    }
  }

  public final void setAutoHide(final boolean state) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myToolWindowManager.setToolWindowAutoHide(myId, state);
  }

  public final boolean isAutoHide() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myToolWindowManager.isToolWindowAutoHide(myId);
  }

  public final boolean isFloating() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myToolWindowManager.isToolWindowFloating(myId);
  }

  public final ToolWindowType getType() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myToolWindowManager.getToolWindowType(myId);
  }

  public final void setType(final ToolWindowType type, final Runnable runnable) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myToolWindowManager.setToolWindowType(myId, type);
    if (runnable != null) {
      myToolWindowManager.invokeLater(runnable);
    }
  }

  public final ToolWindowType getInternalType() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myToolWindowManager.getToolWindowInternalType(myId);
  }

  public void stretchWidth(int value) {
    myToolWindowManager.stretchWidth(this, value);
  }

  public void stretchHeight(int value) {
    myToolWindowManager.stretchHeight(this, value);
  }

  public InternalDecorator getDecorator() {
    return myDecorator;
  }

  public final void setAvailable(final boolean available, final Runnable runnable) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final Boolean oldAvailable = myAvailable ? Boolean.TRUE : Boolean.FALSE;
    myAvailable = available;
    myChangeSupport.firePropertyChange(PROP_AVAILABLE, oldAvailable, myAvailable ? Boolean.TRUE : Boolean.FALSE);
    if (runnable != null) {
      myToolWindowManager.invokeLater(runnable);
    }
  }

  public void installWatcher(ContentManager contentManager) {
    new ContentManagerWatcher(this, contentManager);
  }

  /**
   * @return <code>true</code> if the component passed into constructor is not instance of
   *         <code>ContentManager</code> class. Otherwise it delegates the functionality to the
   *         passed content manager.
   */
  public final boolean isAvailable() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myAvailable && myComponent != null;
  }

  public final JComponent getComponent() {
    return myComponent;
  }

  public ContentManager getContentManager() {
    return myContentManager;
  }

  public ToolWindowContentUi getContentUI() {
    return myContentUI;
  }

  public final Icon getIcon() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myIcon;
    //return getSelectedContent().getIcon();
  }

  public final String getId() {
    return myId;
  }

  public final String getTitle() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return getSelectedContent().getDisplayName();
  }

  public final void setIcon(final Icon icon) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final Icon oldIcon = getIcon();
    getSelectedContent().setIcon(icon);
    myIcon = icon;
    myChangeSupport.firePropertyChange(PROP_ICON, oldIcon, icon);
  }

  public final void setTitle(final String title) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final String oldTitle = getTitle();
    getSelectedContent().setDisplayName(title);
    myChangeSupport.firePropertyChange(PROP_TITLE, oldTitle, title);
  }

  private Content getSelectedContent() {
    final Content selected = getContentManager().getSelectedContent();
    return selected != null ? selected : EMPTY_CONTENT;
  }

  public void setDecorator(final InternalDecorator decorator) {
    myDecorator = decorator;
  }

  public void fireActivated() {
    if (myDecorator != null) {
      myDecorator.fireActivated();
    }
  }

  public void fireHidden() {
    if (myDecorator != null) {
      myDecorator.fireHidden();
    }
  }

  public void fireHiddenSide() {
    if (myDecorator != null) {
      myDecorator.fireHiddenSide();
    }
  }


  public ToolWindowManagerImpl getToolWindowManager() {
    return myToolWindowManager;
  }

  @Nullable
  public ActionGroup getPopupGroup() {
    return myDecorator != null ? myDecorator.createPopupGroup() : null;
  }

  public void setDefaultState(@Nullable final ToolWindowAnchor anchor, @Nullable final ToolWindowType type, @Nullable final Rectangle floatingBounds) {
    myToolWindowManager.setDefaultState(this, anchor, type, floatingBounds);
  }

  public void setToHideOnEmptyContent(final boolean hideOnEmpty) {
    myHideOnEmptyContent = hideOnEmpty;
  }

  public boolean isToHideOnEmptyContent() {
    return myHideOnEmptyContent;
  }

  public boolean isDisposed() {
    return myContentManager.isDisposed();
  }

  public boolean isPlaceholderMode() {
    return myPlaceholderMode;
  }

  public void setPlaceholderMode(final boolean placeholderMode) {
    myPlaceholderMode = placeholderMode;
  }

  public ActionCallback getActivation() {
    return myActivation;
  }

  public ActionCallback setActivation(ActionCallback activation) {
    if (myActivation != null && !myActivation.isProcessed() && !myActivation.equals(activation)) {
      myActivation.setRejected();
    }

    myActivation = activation;
    return myActivation;
  }

  public void setContentFactory(ToolWindowFactory contentFactory) {
    myContentFactory = contentFactory;
  }

  public void ensureContentInitialized() {
    if (myContentFactory != null) {
      getContentManager().removeAllContents(false);
      myContentFactory.createToolWindowContent(myToolWindowManager.getProject(), this);
      myContentFactory = null;
    }
  }

  public void showContentPopup(InputEvent inputEvent) {
    myContentUI.showContentPopup(inputEvent);
  }
}
