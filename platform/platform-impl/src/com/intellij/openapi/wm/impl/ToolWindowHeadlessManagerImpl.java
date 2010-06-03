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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.ActiveRunnable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.wm.*;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings({"ConstantConditions"})
public class ToolWindowHeadlessManagerImpl extends ToolWindowManagerEx {

  public void notifyByBalloon(@NotNull final String toolWindowId, @NotNull final MessageType type, @NotNull final String htmlBody) {
  }

  public static final ToolWindow HEADLESS_WINDOW = new ToolWindow(){
    public boolean isActive() {
      return false;
    }

    public void activate(@Nullable Runnable runnable) {
    }

    public boolean isDisposed() {
      return false;
    }

    public boolean isVisible() {
      return false;
    }
    

    public void show(@Nullable Runnable runnable) {
    }

    public void hide(@Nullable Runnable runnable) {
    }

    public ToolWindowAnchor getAnchor() {
      return ToolWindowAnchor.BOTTOM;
    }

    public void setAnchor(ToolWindowAnchor anchor, @Nullable Runnable runnable) {
    }

    public boolean isSplitMode() {
      return false;
    }

    public void setSplitMode(final boolean isSideTool, @Nullable final Runnable runnable) {

    }

    public boolean isAutoHide() {
      return false;
    }

    public void setAutoHide(boolean state) {
    }

    public void setToHideOnEmptyContent(final boolean hideOnEmpty) {
    }

    public boolean isToHideOnEmptyContent() {
      return false;
    }

    public ToolWindowType getType() {
      return ToolWindowType.SLIDING;
    }

    public void setType(ToolWindowType type, @Nullable Runnable runnable) {
    }

    public Icon getIcon() {
      return null;
    }

    public void setIcon(Icon icon) {
    }

    public String getTitle() {
      return "";
    }

    public void setTitle(String title) {
    }

    public boolean isAvailable() {
      return false;
    }

    public void setContentUiType(ToolWindowContentUiType type, @Nullable Runnable runnable) {
    }

    public void setDefaultContentUiType(@NotNull ToolWindowContentUiType type) {
    }

    public ToolWindowContentUiType getContentUiType() {
      return ToolWindowContentUiType.TABBED;
    }

    public void setAvailable(boolean available, @Nullable Runnable runnable) {
    }

    public void installWatcher(ContentManager contentManager) {
    }

    public JComponent getComponent() {
      return null;
    }

    public ContentManager getContentManager() {
      return MOCK_CONTENT_MANAGER;
    }

    public void setDefaultState(@Nullable final ToolWindowAnchor anchor, @Nullable final ToolWindowType type, @Nullable final Rectangle floatingBounds) {
    }

    public void activate(@Nullable final Runnable runnable, final boolean autoFocusContents) {
    }

    public void activate(@Nullable Runnable runnable, boolean autoFocusContents, boolean forced) {
    }

    public void showContentPopup(InputEvent inputEvent) {
    }

    public ActionCallback getActivation() {
      return new ActionCallback.Done();
    }
  };

  @NonNls private static final ContentManager MOCK_CONTENT_MANAGER = new ContentManager() {
    public void addContent(@NotNull final Content content) { }
    public void addContent(@NotNull final Content content, final Object constraints) { }
    public void addContentManagerListener(@NotNull final ContentManagerListener l) { }
    public void addDataProvider(@NotNull final DataProvider provider) { }
    public void addSelectedContent(@NotNull final Content content) { }
    public boolean canCloseAllContents() { return false; }
    public boolean canCloseContents() { return false; }
    public Content findContent(final String displayName) { return null; }
    public List<AnAction> getAdditionalPopupActions(@NotNull final Content content) { return Collections.emptyList(); }
    public String getCloseActionName() { return "close"; }
    public String getCloseAllButThisActionName() { return "closeallbutthis"; }
    public JComponent getComponent() { return new JLabel(); }
    public Content getContent(final JComponent component) { return null; }
    @Nullable
    public Content getContent(final int index) { return null; }
    public int getContentCount() { return 0; }
    @NotNull
    public Content[] getContents() { return new Content[0]; }
    public int getIndexOfContent(final Content content) { return -1; }
    @Nullable
    public Content getSelectedContent() { return null; }
    @NotNull
    public Content[] getSelectedContents() { return new Content[0]; }
    public boolean isSelected(@NotNull final Content content) { return false; }
    public void removeAllContents(final boolean dispose) { }
    public boolean removeContent(@NotNull final Content content, final boolean dispose) { return false; }

    public ActionCallback removeContent(@NotNull Content content, boolean dispose, boolean trackFocus, boolean implicitFocus) {
      return new ActionCallback.Done();
    }

    public void removeContentManagerListener(@NotNull final ContentManagerListener l) { }
    public void removeFromSelection(@NotNull final Content content) { }
    public ActionCallback selectNextContent() { return new ActionCallback.Done();}
    public ActionCallback selectPreviousContent() { return new ActionCallback.Done();}
    public void setSelectedContent(@NotNull final Content content) { }
    public ActionCallback setSelectedContentCB(@NotNull Content content) { return new ActionCallback.Done(); }
    public void setSelectedContent(@NotNull final Content content, final boolean requestFocus) { }
    public ActionCallback setSelectedContentCB(@NotNull final Content content, final boolean requestFocus) { return new ActionCallback.Done();}

    public void setSelectedContent(@NotNull Content content, boolean requestFocus, boolean forcedFocus) {
    }

    public ActionCallback setSelectedContentCB(@NotNull final Content content, final boolean requestFocus, final boolean forcedFocus) {
      return new ActionCallback.Done();
    }

    public ActionCallback setSelectedContent(@NotNull Content content, boolean requestFocus, boolean forcedFocus, boolean implicit) {
      return new ActionCallback.Done();
    }

    public ActionCallback requestFocus(@Nullable final Content content, final boolean forced) {
      return new ActionCallback.Done(); 
    }

    public void dispose() {}

    public boolean isDisposed() {
      return false;
    }

    public boolean isSingleSelection() {
      return true;
    }

    @NotNull
    public ContentFactory getFactory() {
      return ServiceManager.getService(ContentFactory.class);
    }
  };

  public ToolWindow registerToolWindow(@NotNull String id, @NotNull JComponent component, @NotNull ToolWindowAnchor anchor) {
    return HEADLESS_WINDOW;
  }

  public ToolWindow registerToolWindow(@NotNull String id, @NotNull JComponent component, @NotNull ToolWindowAnchor anchor, Disposable parentDisposable, boolean canWorkInDumbMode) {
    return HEADLESS_WINDOW;
  }

  public ToolWindow registerToolWindow(@NotNull String id, @NotNull JComponent component, @NotNull ToolWindowAnchor anchor, Disposable parentDisposable) {
    return HEADLESS_WINDOW;
  }

  public ToolWindow registerToolWindow(@NotNull final String id, final boolean canCloseContent, @NotNull final ToolWindowAnchor anchor) {
    return HEADLESS_WINDOW;
  }

  public ToolWindow registerToolWindow(@NotNull final String id, final boolean canCloseContent, @NotNull final ToolWindowAnchor anchor, final boolean sideTool) {
    return HEADLESS_WINDOW;
  }

  public ToolWindow registerToolWindow(@NotNull final String id, final boolean canCloseContent, @NotNull final ToolWindowAnchor anchor,
                                       final Disposable parentDisposable, final boolean dumbAware) {
    return HEADLESS_WINDOW;
  }

  public void unregisterToolWindow(@NotNull String id) {
  }

  public void activateEditorComponent() {
  }

  public boolean isEditorComponentActive() {
    return false;
  }

  public ActionCallback requestFocus(final Component c, final boolean forced) {
    return new ActionCallback.Done();
  }

  public ActionCallback requestFocus(final ActiveRunnable command, final boolean forced) {
    return new ActionCallback.Done();
  }

  public JComponent getFocusTargetFor(final JComponent comp) {
    return null;
  }

  public String[] getToolWindowIds() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  public String getActiveToolWindowId() {
    return null;
  }

  public ToolWindow getToolWindow(String id) {
    return HEADLESS_WINDOW;
  }

  public void invokeLater(Runnable runnable) {
  }

  public IdeFocusManager getFocusManager() {
    return IdeFocusManagerHeadless.INSTANCE;
  }

  @Override
  public void notifyByBalloon(@NotNull final String toolWindowId, final MessageType type, @NotNull final String text, @Nullable final Icon icon,
                              @Nullable final HyperlinkListener listener) {
  }

  public void addToolWindowManagerListener(@NotNull ToolWindowManagerListener l) {

  }

  public void removeToolWindowManagerListener(@NotNull ToolWindowManagerListener l) {
  }

  public String getLastActiveToolWindowId() {
    return null;
  }

  public String getLastActiveToolWindowId(Condition<JComponent> condition) {
    return null;
  }

  public DesktopLayout getLayout() {
    return new DesktopLayout();
  }

  public void setLayoutToRestoreLater(DesktopLayout layout) {
  }

  public DesktopLayout getLayoutToRestoreLater() {
    return new DesktopLayout();
  }

  public void setLayout(@NotNull DesktopLayout layout) {
  }

  public void clearSideStack() {
  }

  public void hideToolWindow(@NotNull final String id, final boolean hideSide) {
  }

  public List<String> getIdsOn(@NotNull final ToolWindowAnchor anchor) {
    return new ArrayList<String>();
  }
}
