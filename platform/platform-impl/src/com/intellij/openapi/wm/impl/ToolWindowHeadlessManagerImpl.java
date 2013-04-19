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

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 13-Jul-2006
 * Time: 12:07:39
 */
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerListener;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.InputEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings({"ConstantConditions"})
public class ToolWindowHeadlessManagerImpl extends ToolWindowManagerEx {
  @Override
  public boolean canShowNotification(@NotNull String toolWindowId) {
    return false;
  }

  @Override
  public void notifyByBalloon(@NotNull final String toolWindowId, @NotNull final MessageType type, @NotNull final String htmlBody) {
  }

  public static final ToolWindow HEADLESS_WINDOW = new ToolWindowEx() {
    @Override
    public boolean isActive() {
      return false;
    }

    @Override
    public void activate(@Nullable Runnable runnable) {
    }

    @Override
    public boolean isDisposed() {
      return false;
    }

    @Override
    public boolean isVisible() {
      return false;
    }

    @Override
    public ActionCallback getReady(@NotNull Object requestor) {
      return new ActionCallback.Done();
    }

    @Override
    public void show(@Nullable Runnable runnable) {
    }

    @Override
    public void hide(@Nullable Runnable runnable) {
    }

    @Override
    public ToolWindowAnchor getAnchor() {
      return ToolWindowAnchor.BOTTOM;
    }

    @Override
    public void setAnchor(ToolWindowAnchor anchor, @Nullable Runnable runnable) {
    }

    @Override
    public boolean isSplitMode() {
      return false;
    }

    @Override
    public void setSplitMode(final boolean isSideTool, @Nullable final Runnable runnable) {

    }

    @Override
    public boolean isAutoHide() {
      return false;
    }

    @Override
    public void setAutoHide(boolean state) {
    }

    @Override
    public void setToHideOnEmptyContent(final boolean hideOnEmpty) {
    }

    @Override
    public boolean isToHideOnEmptyContent() {
      return false;
    }

    @Override
    public ToolWindowType getType() {
      return ToolWindowType.SLIDING;
    }

    @Override
    public void setType(ToolWindowType type, @Nullable Runnable runnable) {
    }

    @Override
    public Icon getIcon() {
      return null;
    }

    @Override
    public void setIcon(Icon icon) {
    }

    @Override
    public String getTitle() {
      return "";
    }

    @Override
    public void setTitle(String title) {
    }

    @Override
    public boolean isAvailable() {
      return false;
    }

    @Override
    public void setContentUiType(ToolWindowContentUiType type, @Nullable Runnable runnable) {
    }

    @Override
    public void setDefaultContentUiType(@NotNull ToolWindowContentUiType type) {
    }

    @Override
    public ToolWindowContentUiType getContentUiType() {
      return ToolWindowContentUiType.TABBED;
    }

    @Override
    public void setAvailable(boolean available, @Nullable Runnable runnable) {
    }

    @Override
    public void installWatcher(ContentManager contentManager) {
    }

    @Override
    public JComponent getComponent() {
      return null;
    }

    @Override
    public ContentManager getContentManager() {
      return MOCK_CONTENT_MANAGER;
    }

    @Override
    public void setDefaultState(@Nullable final ToolWindowAnchor anchor,
                                @Nullable final ToolWindowType type,
                                @Nullable final Rectangle floatingBounds) {
    }

    @Override
    public void activate(@Nullable final Runnable runnable, final boolean autoFocusContents) {
    }

    @Override
    public void activate(@Nullable Runnable runnable, boolean autoFocusContents, boolean forced) {
    }

    @Override
    public void showContentPopup(InputEvent inputEvent) {
    }

    @Override
    public ActionCallback getActivation() {
      return new ActionCallback.Done();
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener l) {
    }

    @Override
    public ToolWindowType getInternalType() {
      return ToolWindowType.DOCKED;
    }

    @Override
    public void stretchWidth(int value) {
    }

    @Override
    public void stretchHeight(int value) {
    }

    @Override
    public InternalDecorator getDecorator() {
      return null;
    }

    @Override
    public void setAdditionalGearActions(ActionGroup additionalGearActions) {
    }

    @Override
    public void setTitleActions(AnAction... actions) {
    }

    @Override
    public void setUseLastFocusedOnActivation(boolean focus) {
    }

    @Override
    public boolean isUseLastFocusedOnActivation() {
      return false;
    }
  };

  @NonNls private static final ContentManager MOCK_CONTENT_MANAGER = new ContentManager() {
    private final List<Content> myContents = new ArrayList<Content>();
    private Content mySelected;

    @Override
    public ActionCallback getReady(@NotNull Object requestor) {
      return new ActionCallback.Done();
    }

    @Override
    public void addContent(@NotNull final Content content) {
    }

    @Override
    public void addContent(@NotNull Content content, int order) {
      myContents.add(order, content);
    }

    @Override
    public void addContent(@NotNull final Content content, final Object constraints) {
    }

    @Override
    public void addContentManagerListener(@NotNull final ContentManagerListener l) {
    }

    @Override
    public void addDataProvider(@NotNull final DataProvider provider) {
    }

    @Override
    public void addSelectedContent(@NotNull final Content content) {
    }

    @Override
    public boolean canCloseAllContents() {
      return false;
    }

    @Override
    public boolean canCloseContents() {
      return false;
    }

    @Override
    public Content findContent(final String displayName) {
      return null;
    }

    @Override
    public List<AnAction> getAdditionalPopupActions(@NotNull final Content content) {
      return Collections.emptyList();
    }

    @NotNull
    @Override
    public String getCloseActionName() {
      return "close";
    }

    @NotNull
    @Override
    public String getCloseAllButThisActionName() {
      return "closeallbutthis";
    }

    @NotNull
    @Override
    public String getPreviousContentActionName() {
      return "previous";
    }

    @NotNull
    @Override
    public String getNextContentActionName() {
      return "next";
    }

    @NotNull
    @Override
    public JComponent getComponent() {
      return new JLabel();
    }

    @Override
    public Content getContent(final JComponent component) {
      return null;
    }

    @Override
    @Nullable
    public Content getContent(final int index) {
      return null;
    }

    @Override
    public int getContentCount() {
      return 0;
    }

    @Override
    @NotNull
    public Content[] getContents() {
      return myContents.toArray(new Content[myContents.size()]);
    }

    @Override
    public int getIndexOfContent(final Content content) {
      return -1;
    }

    @Override
    @Nullable
    public Content getSelectedContent() {
      return mySelected;
    }

    @Override
    @NotNull
    public Content[] getSelectedContents() {
      return new Content[0];
    }

    @Override
    public boolean isSelected(@NotNull final Content content) {
      return false;
    }

    @Override
    public void removeAllContents(final boolean dispose) {
      for (int i = myContents.size() - 1; i >= 0; i--) {
        Content content = myContents.get(i);
        removeContent(content, true);
      }
      mySelected = null;
    }

    @Override
    public boolean removeContent(@NotNull final Content content, final boolean dispose) {
      Disposer.dispose(content);
      if (mySelected == content) {
        mySelected = null;
      }
      return myContents.remove(content);
    }

    @NotNull
    @Override
    public ActionCallback removeContent(@NotNull Content content, boolean dispose, boolean trackFocus, boolean implicitFocus) {
      return new ActionCallback.Done();
    }

    @Override
    public void removeContentManagerListener(@NotNull final ContentManagerListener l) {
    }

    @Override
    public void removeFromSelection(@NotNull final Content content) {
    }

    @Override
    public ActionCallback selectNextContent() {
      return new ActionCallback.Done();
    }

    @Override
    public ActionCallback selectPreviousContent() {
      return new ActionCallback.Done();
    }

    @Override
    public void setSelectedContent(@NotNull final Content content) {
      mySelected = content;
    }

    @NotNull
    @Override
    public ActionCallback setSelectedContentCB(@NotNull Content content) {
      return new ActionCallback.Done();
    }

    @Override
    public void setSelectedContent(@NotNull final Content content, final boolean requestFocus) {
    }

    @NotNull
    @Override
    public ActionCallback setSelectedContentCB(@NotNull final Content content, final boolean requestFocus) {
      return new ActionCallback.Done();
    }

    @Override
    public void setSelectedContent(@NotNull Content content, boolean requestFocus, boolean forcedFocus) {
    }

    @NotNull
    @Override
    public ActionCallback setSelectedContentCB(@NotNull final Content content, final boolean requestFocus, final boolean forcedFocus) {
      return new ActionCallback.Done();
    }

    @NotNull
    @Override
    public ActionCallback setSelectedContent(@NotNull Content content, boolean requestFocus, boolean forcedFocus, boolean implicit) {
      return new ActionCallback.Done();
    }

    @NotNull
    @Override
    public ActionCallback requestFocus(@Nullable final Content content, final boolean forced) {
      return new ActionCallback.Done();
    }

    @Override
    public void dispose() {
      removeAllContents(true);
    }

    @Override
    public boolean isDisposed() {
      return false;
    }

    @Override
    public boolean isSingleSelection() {
      return true;
    }

    @Override
    @NotNull
    public ContentFactory getFactory() {
      return ServiceManager.getService(ContentFactory.class);
    }
  };

  @Override
  public ToolWindow registerToolWindow(@NotNull String id,
                                       @NotNull JComponent component,
                                       @NotNull ToolWindowAnchor anchor,
                                       Disposable parentDisposable,
                                       boolean canWorkInDumbMode) {
    return HEADLESS_WINDOW;
  }

  @Override
  public ToolWindow registerToolWindow(@NotNull String id, @NotNull JComponent component, @NotNull ToolWindowAnchor anchor) {
    return HEADLESS_WINDOW;
  }

  @Override
  public ToolWindow registerToolWindow(@NotNull String id,
                                       @NotNull JComponent component,
                                       @NotNull ToolWindowAnchor anchor,
                                       Disposable parentDisposable,
                                       boolean canWorkInDumbMode,
                                       boolean canCloseContents) {
    return HEADLESS_WINDOW;
  }

  @Override
  public ToolWindow registerToolWindow(@NotNull String id,
                                       @NotNull JComponent component,
                                       @NotNull ToolWindowAnchor anchor,
                                       Disposable parentDisposable) {
    return HEADLESS_WINDOW;
  }

  @Override
  public ToolWindow registerToolWindow(@NotNull final String id, final boolean canCloseContent, @NotNull final ToolWindowAnchor anchor) {
    return HEADLESS_WINDOW;
  }

  @Override
  public ToolWindow registerToolWindow(@NotNull final String id,
                                       final boolean canCloseContent,
                                       @NotNull final ToolWindowAnchor anchor,
                                       final boolean secondary) {
    return HEADLESS_WINDOW;
  }

  @Override
  public ToolWindow registerToolWindow(@NotNull final String id, final boolean canCloseContent, @NotNull final ToolWindowAnchor anchor,
                                       final Disposable parentDisposable, final boolean dumbAware) {
    return HEADLESS_WINDOW;
  }

  @Override
  public void unregisterToolWindow(@NotNull String id) {
  }

  @Override
  public void activateEditorComponent() {
  }

  @Override
  public boolean isEditorComponentActive() {
    return false;
  }

  @Override
  public String[] getToolWindowIds() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  public String getActiveToolWindowId() {
    return null;
  }

  @Override
  public ToolWindow getToolWindow(String id) {
    return HEADLESS_WINDOW;
  }

  @Override
  public void invokeLater(Runnable runnable) {
  }

  @Override
  public IdeFocusManager getFocusManager() {
    return IdeFocusManagerHeadless.INSTANCE;
  }

  @Override
  public void notifyByBalloon(@NotNull final String toolWindowId,
                              @NotNull final MessageType type,
                              @NotNull final String text,
                              @Nullable final Icon icon,
                              @Nullable final HyperlinkListener listener) {
  }

  @Override
  public Balloon getToolWindowBalloon(String id) {
    return null;
  }

  @Override
  public void initToolWindow(ToolWindowEP bean) {

  }

  @Override
  public void addToolWindowManagerListener(@NotNull ToolWindowManagerListener l) {

  }

  @Override
  public void removeToolWindowManagerListener(@NotNull ToolWindowManagerListener l) {
  }

  @Override
  public String getLastActiveToolWindowId() {
    return null;
  }

  @Override
  public String getLastActiveToolWindowId(Condition<JComponent> condition) {
    return null;
  }

  @Override
  public DesktopLayout getLayout() {
    return new DesktopLayout();
  }

  @Override
  public void setLayoutToRestoreLater(DesktopLayout layout) {
  }

  @Override
  public DesktopLayout getLayoutToRestoreLater() {
    return new DesktopLayout();
  }

  @Override
  public void setLayout(@NotNull DesktopLayout layout) {
  }

  @Override
  public void clearSideStack() {
  }

  @Override
  public void hideToolWindow(@NotNull final String id, final boolean hideSide) {
  }

  @Override
  public List<String> getIdsOn(@NotNull final ToolWindowAnchor anchor) {
    return new ArrayList<String>();
  }
}
