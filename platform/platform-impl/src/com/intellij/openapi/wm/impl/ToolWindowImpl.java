/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.ide.UiActivity;
import com.intellij.ide.UiActivityMonitor;
import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.BusyObject;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.impl.commands.FinalizableCommand;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.impl.ContentImpl;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;

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
  private Icon myIcon;
  private String myStripeTitle;

  private static final Content EMPTY_CONTENT = new ContentImpl(new JLabel(), "", false);
  private final ToolWindowContentUi myContentUI;

  private InternalDecorator myDecorator;

  private boolean myHideOnEmptyContent = false;
  private boolean myPlaceholderMode;
  private ToolWindowFactory myContentFactory;

  private ActionCallback myActivation = new ActionCallback.Done();
  private final BusyObject.Impl myShowing = new BusyObject.Impl() {
    @Override
    public boolean isReady() {
      return myComponent != null && myComponent.isShowing();
    }
  };
  private boolean myUseLastFocused = true;

  private static final Logger LOG = Logger.getInstance(ToolWindowImpl.class);

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

    UiNotifyConnector notifyConnector = new UiNotifyConnector(myComponent, new Activatable.Adapter() {
      @Override
      public void showNotify() {
        myShowing.onReady();
      }
    });
    Disposer.register(myContentManager, notifyConnector);
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

    final UiActivity activity = new UiActivity.Focus("toolWindow:" + myId);
    UiActivityMonitor.getInstance().addActivity(myToolWindowManager.getProject(), activity, ModalityState.NON_MODAL);

    myToolWindowManager.activateToolWindow(myId, forced, autoFocusContents);

    getActivation().doWhenDone(new Runnable() {
      public void run() {
        myToolWindowManager.invokeLater(new Runnable() {
          @Override
          public void run() {
            if (runnable != null) {
              runnable.run();
            }
            UiActivityMonitor.getInstance().removeActivity(myToolWindowManager.getProject(), activity);
          }
        });
      }
    });
  }

  public final boolean isActive() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myToolWindowManager.isEditorComponentActive()) return false;
    return myToolWindowManager.isToolWindowActive(myId) || myDecorator != null && myDecorator.isFocused();
  }

  @NotNull
  @Override
  public ActionCallback getReady(@NotNull final Object requestor) {
    final ActionCallback result = new ActionCallback();
    myShowing.getReady(this).doWhenDone(new Runnable() {
      @Override
      public void run() {
        ArrayList<FinalizableCommand> cmd = new ArrayList<FinalizableCommand>();
        cmd.add(new FinalizableCommand(null) {
          @Override
          public void run() {
            IdeFocusManager.getInstance(myToolWindowManager.getProject()).doWhenFocusSettlesDown(new Runnable() {
              @Override
              public void run() {
                if (myContentManager.isDisposed()) return;
                myContentManager.getReady(requestor).notify(result);
              }
            });
          }
        });
        myToolWindowManager.execute(cmd);
      }
    });
    return result;
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

  public final void hide(@Nullable final Runnable runnable) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myToolWindowManager.hideToolWindow(myId, false);
    if (runnable != null) {
      myToolWindowManager.invokeLater(runnable);
    }
  }

  public final boolean isVisible() {
    return myToolWindowManager.isToolWindowVisible(myId);
  }

  public final ToolWindowAnchor getAnchor() {
    return myToolWindowManager.getToolWindowAnchor(myId);
  }

  public final void setAnchor(final ToolWindowAnchor anchor, @Nullable final Runnable runnable) {
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

  public void setContentUiType(ToolWindowContentUiType type, @Nullable Runnable runnable) {
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

  public final ToolWindowType getType() {
    return myToolWindowManager.getToolWindowType(myId);
  }

  public final void setType(final ToolWindowType type, @Nullable final Runnable runnable) {
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

  @Override
  public void setAdditionalGearActions(ActionGroup additionalGearActions) {
    getDecorator().setAdditionalGearActions(additionalGearActions);
  }

  @Override
  public void setTitleActions(AnAction... actions) {
    getDecorator().setTitleActions(actions);
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

  @NotNull
  public final String getStripeTitle() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return ObjectUtils.notNull(myStripeTitle, myId);
  }

  public final void setIcon(final Icon icon) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final Icon oldIcon = getIcon();
    if (oldIcon != icon && !(icon instanceof LayeredIcon) && (icon.getIconHeight() != 13 || icon.getIconWidth() != 13)) {
      LOG.warn("ToolWindow icons should be 13x13. Please fix " + icon);
    }
    getSelectedContent().setIcon(icon);
    myIcon = icon;
    myChangeSupport.firePropertyChange(PROP_ICON, oldIcon, icon);
  }

  public final void setTitle(String title) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    String oldTitle = getTitle();
    getSelectedContent().setDisplayName(title);
    myChangeSupport.firePropertyChange(PROP_TITLE, oldTitle, title);
  }

  public final void setStripeTitle(@NotNull String stripeTitle) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    String oldTitle = myStripeTitle;
    myStripeTitle = stripeTitle;
    myChangeSupport.firePropertyChange(PROP_STRIPE_TITLE, oldTitle, stripeTitle);
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
    if (contentFactory instanceof ToolWindowFactoryEx) {
      ((ToolWindowFactoryEx)contentFactory).init(this);
    }
  }

  public void ensureContentInitialized() {
    if (myContentFactory != null) {
      getContentManager().removeAllContents(false);
      myContentFactory.createToolWindowContent(myToolWindowManager.getProject(), this);
      myContentFactory = null;
    }
  }

  public void showContentPopup(InputEvent inputEvent) {
    myContentUI.toggleContentPopup();
  }

  @Override
  public void setUseLastFocusedOnActivation(boolean focus) {
    myUseLastFocused = focus;
  }

  @Override
  public boolean isUseLastFocusedOnActivation() {
    return myUseLastFocused;
  }
}
