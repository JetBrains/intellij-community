// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.ui.content.*;
import com.intellij.ui.content.impl.ContentImpl;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.EventDispatcher;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.util.List;
import java.util.*;

// not final for android
public class ToolWindowHeadlessManagerImpl extends ToolWindowManagerEx {
  private final Map<String, ToolWindow> myToolWindows = new HashMap<>();
  private final Project myProject;

  public ToolWindowHeadlessManagerImpl(Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public List<ToolWindow> getToolWindows() {
    return List.copyOf(myToolWindows.values());
  }

  @Override
  public boolean canShowNotification(@NotNull String toolWindowId) {
    return false;
  }

  public @NotNull ToolWindow doRegisterToolWindow(@NotNull String id) {
    MockToolWindow toolWindow = new MockToolWindow(myProject);
    myToolWindows.put(id, toolWindow);
    return toolWindow;
  }

  @Override
  public @NotNull ToolWindow registerToolWindow(@NotNull RegisterToolWindowTask task) {
    return doRegisterToolWindow(task.getId());
  }

  @Override
  public void unregisterToolWindow(@NotNull String id) {
    ToolWindow toolWindow = myToolWindows.remove(id);
    if (toolWindow != null) {
      Disposer.dispose(((MockToolWindow)toolWindow).myContentManager);
    }
  }

  @Override
  public void activateEditorComponent() {
  }

  @Override
  public boolean isEditorComponentActive() {
    return false;
  }

  @Override
  public String @NotNull [] getToolWindowIds() {
    return ArrayUtilRt.EMPTY_STRING_ARRAY;
  }

  @Override
  public @NotNull Set<String> getToolWindowIdSet() {
    return Collections.emptySet();
  }

  @Override
  public String getActiveToolWindowId() {
    return null;
  }

  @Override
  public @Nullable String getLastActiveToolWindowId() {
    return null;
  }

  @Override
  public ToolWindow getToolWindow(@Nullable String id) {
    return myToolWindows.get(id);
  }

  @Override
  public void invokeLater(@NotNull Runnable runnable) {
  }

  @Override
  public @NotNull IdeFocusManager getFocusManager() {
    return IdeFocusManagerHeadless.INSTANCE;
  }

  @Override
  public void notifyByBalloon(@NotNull ToolWindowBalloonShowOptions options) {
  }

  @Override
  public Balloon getToolWindowBalloon(@NotNull String id) {
    return null;
  }

  @Override
  public boolean isMaximized(@NotNull ToolWindow wnd) {
    return false;
  }

  @Override
  public void setMaximized(@NotNull ToolWindow window, boolean maximized) {
  }

  @Override
  public void initToolWindow(@NotNull ToolWindowEP bean) {
    doRegisterToolWindow(bean.id);
  }

  @Override
  public @NotNull DesktopLayout getLayout() {
    return new DesktopLayout();
  }

  @Override
  public void setLayoutToRestoreLater(@Nullable DesktopLayout layout) {
  }

  @Override
  public @Nullable DesktopLayout getLayoutToRestoreLater() {
    return null;
  }

  @Override
  public void setLayout(@NotNull DesktopLayout layout) {
  }

  @Override
  public void clearSideStack() {
  }

  @Override
  public void hideToolWindow(@NotNull String id, boolean hideSide) {
  }

  @Override
  public @NotNull List<String> getIdsOn(@NotNull ToolWindowAnchor anchor) {
    return Collections.emptyList();
  }

  public static class MockToolWindow implements ToolWindowEx {
    final ContentManager myContentManager = new MockContentManager();
    private final Project project;

    public MockToolWindow(@NotNull Project project) {
      this.project = project;
      Disposer.register(project, myContentManager);
    }

    @Override
    public @NotNull Project getProject() {
      return project;
    }

    @Override
    public @NotNull Disposable getDisposable() {
      return myContentManager;
    }

    @Override
    public void remove() {
    }

    @Override
    public @NotNull String getId() {
      return "";
    }

    @Override
    public boolean isActive() {
      return false;
    }

    @Override
    public void activate(@Nullable Runnable runnable) {
      if (runnable != null) runnable.run();
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
    public void setShowStripeButton(boolean show) {
    }

    @Override
    public boolean isShowStripeButton() {
      return true;
    }

    @Override
    public @NotNull ActionCallback getReady(@NotNull Object requestor) {
      return ActionCallback.DONE;
    }

    @Override
    public void show(@Nullable Runnable runnable) {
    }

    @Override
    public void hide(@Nullable Runnable runnable) {
    }

    @Override
    public @NotNull ToolWindowAnchor getAnchor() {
      return ToolWindowAnchor.BOTTOM;
    }

    @Override
    public void setAnchor(@NotNull ToolWindowAnchor anchor, @Nullable Runnable runnable) {
    }

    @Override
    public boolean isSplitMode() {
      return false;
    }

    @Override
    public void setSplitMode(final boolean isSideTool, final @Nullable Runnable runnable) {

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
    public @NotNull ToolWindowType getType() {
      return ToolWindowType.SLIDING;
    }

    @Override
    public void setType(@NotNull ToolWindowType type, @Nullable Runnable runnable) {
    }

    @Override
    public @Nullable Icon getIcon() {
      return null;
    }

    @Override
    public void setIcon(@NotNull Icon icon) {
    }

    @Override
    public String getTitle() {
      return "";
    }

    @Override
    public void setTitle(String title) {
    }

    @Override
    public @NotNull String getStripeTitle() {
      return "";
    }

    @Override
    public void setStripeTitle(@NotNull String title) {
    }

    @Override
    public boolean isAvailable() {
      return false;
    }

    @Override
    public void setContentUiType(@NotNull ToolWindowContentUiType type, @Nullable Runnable runnable) {
    }

    @Override
    public void setDefaultContentUiType(@NotNull ToolWindowContentUiType type) {
    }

    @Override
    public @NotNull ToolWindowContentUiType getContentUiType() {
      return ToolWindowContentUiType.TABBED;
    }

    @Override
    public void setAvailable(boolean available, @Nullable Runnable runnable) {
    }

    @Override
    public void setAvailable(boolean value) {
    }

    @Override
    public void installWatcher(ContentManager contentManager) {
    }

    @Override
    public @NotNull JComponent getComponent() {
      return new JLabel();
    }

    @Override
    public @NotNull ContentManager getContentManager() {
      return myContentManager;
    }

    @Override
    public @Nullable ContentManager getContentManagerIfCreated() {
      return myContentManager;
    }

    @Override
    public void addContentManagerListener(@NotNull ContentManagerListener listener) {
    }

    @Override
    public void setDefaultState(final @Nullable ToolWindowAnchor anchor,
                                final @Nullable ToolWindowType type,
                                final @Nullable Rectangle floatingBounds) {
    }

    @Override
    public void activate(final @Nullable Runnable runnable, final boolean autoFocusContents) {
      activate(runnable);
    }

    @Override
    public void activate(@Nullable Runnable runnable, boolean autoFocusContents, boolean forced) {
      activate(runnable);
    }

    @Override
    public void showContentPopup(@NotNull InputEvent inputEvent) {
    }

    @Override
    public @NotNull ToolWindowType getInternalType() {
      return ToolWindowType.DOCKED;
    }

    @Override
    public void stretchWidth(int value) {
    }

    @Override
    public void stretchHeight(int value) {
    }

    @Override
    public @NotNull InternalDecorator getDecorator() {
      throw new IncorrectOperationException();
    }

    @Override
    public void setAdditionalGearActions(ActionGroup additionalGearActions) {
    }

    @Override
    public void setTitleActions(@NotNull List<? extends AnAction> actions) {
    }

    @Override
    public void setTabActions(@NotNull AnAction @NotNull... actions) {
    }

    @Override
    public void setTabDoubleClickActions(@NotNull List<AnAction> actions) {
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral", "DialogTitleCapitalization"})
  private static class MockContentManager implements ContentManager {
    private final EventDispatcher<ContentManagerListener> myDispatcher = EventDispatcher.create(ContentManagerListener.class);
    private final List<Content> myContents = new ArrayList<>();
    private Content mySelected;

    @Override
    public @NotNull ActionCallback getReady(@NotNull Object requestor) {
      return ActionCallback.DONE;
    }

    @Override
    public void addContent(final @NotNull Content content) {
      addContent(content, -1);
    }

    @Override
    public void addContent(@NotNull Content content, int order) {
      myContents.add(order == -1 ? myContents.size() : order, content);
      if (content instanceof ContentImpl && content.getManager() == null) {
        ((ContentImpl)content).setManager(this);
      }
      Disposer.register(this, content);
      ContentManagerEvent e = new ContentManagerEvent(this, content, myContents.indexOf(content), ContentManagerEvent.ContentOperation.add);
      myDispatcher.getMulticaster().contentAdded(e);
      if (mySelected == null) setSelectedContent(content);
    }

    @Override
    public void addSelectedContent(final @NotNull Content content) {
      addContent(content);
      setSelectedContent(content);
    }

    @Override
    public void addContentManagerListener(final @NotNull ContentManagerListener l) {
      myDispatcher.getListeners().add(0, l);
    }

    @Override
    public void addDataProvider(final @NotNull DataProvider provider) {
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
      for (Content each : myContents) {
        if (each.getDisplayName().equals(displayName)) return each;
      }
      return null;
    }

    @Override
    public @NotNull List<AnAction> getAdditionalPopupActions(final @NotNull Content content) {
      return Collections.emptyList();
    }

    @Override
    public @NotNull String getCloseActionName() {
      return "close";
    }

    @Override
    public @NotNull String getCloseAllButThisActionName() {
      return "closeallbutthis";
    }

    @Override
    public @NotNull String getPreviousContentActionName() {
      return "previous";
    }

    @Override
    public @NotNull String getNextContentActionName() {
      return "next";
    }

    @Override
    public @NotNull JComponent getComponent() {
      return new JLabel();
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
    public @Nullable Content getContent(int index) {
      return myContents.get(index);
    }

    @Override
    public int getContentCount() {
      return myContents.size();
    }

    @Override
    public Content @NotNull [] getContents() {
      return myContents.toArray(new Content[0]);
    }

    @Override
    public int getIndexOfContent(@NotNull Content content) {
      return myContents.indexOf(content);
    }

    @Override
    public @Nullable Content getSelectedContent() {
      return mySelected;
    }

    @Override
    public Content @NotNull [] getSelectedContents() {
      return mySelected != null ? new Content[]{mySelected} : new Content[0];
    }

    @Override
    public boolean isSelected(final @NotNull Content content) {
      return content == mySelected;
    }

    @Override
    public void removeAllContents(final boolean dispose) {
      for (Content content : getContents()) {
        removeContent(content, dispose);
      }
    }

    @Override
    public boolean removeContent(final @NotNull Content content, final boolean dispose) {
      boolean wasSelected = mySelected == content;
      int oldIndex = myContents.indexOf(content);
      if (wasSelected) {
        removeFromSelection(content);
      }
      boolean result = myContents.remove(content);
      if (dispose) Disposer.dispose(content);
      ContentManagerEvent e = new ContentManagerEvent(this, content, oldIndex, ContentManagerEvent.ContentOperation.remove);
      myDispatcher.getMulticaster().contentRemoved(e);
      Content item = ContainerUtil.getFirstItem(myContents);
      if (item != null) {
        setSelectedContent(item);
      }
      else {
        mySelected = null;
      }
      return result;
    }

    @Override
    public @NotNull ActionCallback removeContent(@NotNull Content content, boolean dispose, boolean requestFocus, boolean implicitFocus) {
      removeContent(content, dispose);
      return ActionCallback.DONE;
    }

    @Override
    public void removeContentManagerListener(final @NotNull ContentManagerListener l) {
      myDispatcher.removeListener(l);
    }

    @Override
    public void removeFromSelection(final @NotNull Content content) {
      ContentManagerEvent e = new ContentManagerEvent(this, content, myContents.indexOf(mySelected), ContentManagerEvent.ContentOperation.remove);
      myDispatcher.getMulticaster().selectionChanged(e);
    }

    @Override
    public ActionCallback selectNextContent() {
      return ActionCallback.DONE;
    }

    @Override
    public ActionCallback selectPreviousContent() {
      return ActionCallback.DONE;
    }

    @Override
    public void setSelectedContent(final @NotNull Content content) {
      if (mySelected != null) {
        removeFromSelection(mySelected);
      }
      mySelected = content;
      ContentManagerEvent e = new ContentManagerEvent(this, content, myContents.indexOf(content), ContentManagerEvent.ContentOperation.add);
      myDispatcher.getMulticaster().selectionChanged(e);
    }

    @Override
    public @NotNull ActionCallback setSelectedContentCB(@NotNull Content content) {
      setSelectedContent(content);
      return ActionCallback.DONE;
    }

    @Override
    public void setSelectedContent(final @NotNull Content content, final boolean requestFocus) {
      setSelectedContent(content);
    }

    @Override
    public @NotNull ActionCallback setSelectedContentCB(final @NotNull Content content, final boolean requestFocus) {
      return setSelectedContentCB(content);
    }

    @Override
    public void setSelectedContent(@NotNull Content content, boolean requestFocus, boolean forcedFocus) {
      setSelectedContent(content);
    }

    @Override
    public @NotNull ActionCallback setSelectedContentCB(final @NotNull Content content, final boolean requestFocus, final boolean forcedFocus) {
      return setSelectedContentCB(content);
    }

    @Override
    public @NotNull ActionCallback setSelectedContent(@NotNull Content content, boolean requestFocus, boolean forcedFocus, boolean implicit) {
      return setSelectedContentCB(content);
    }

    @Override
    public @NotNull ActionCallback requestFocus(final @Nullable Content content, final boolean forced) {
      return ActionCallback.DONE;
    }

    @Override
    public void dispose() {
      myContents.clear();
      mySelected = null;
      myDispatcher.getListeners().clear();
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
    public @NotNull ContentFactory getFactory() {
      return ApplicationManager.getApplication().getService(ContentFactory.class);
    }
  }}
