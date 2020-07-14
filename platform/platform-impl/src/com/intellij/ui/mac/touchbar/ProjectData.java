// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.execution.dashboard.RunDashboardManager;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.XDebugSessionService;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import com.intellij.ui.mac.TouchbarDataKeys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ProjectData {
  private static final Logger LOG = Logger.getInstance(ProjectData.class);

  private final @NotNull Project myProject;
  private final Map<BarType, BarContainer> myPermanentBars = new HashMap<>();
  private final Map<Editor, EditorData> myEditors = new HashMap<>();
  private final Map<ToolWindow, ToolWindowData> myToolWindows = new HashMap<>();

  ProjectData(@NotNull Project project) {
    myProject = project;

    myProject.getMessageBus().connect().subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
      @Override
      public void stateChanged(@NotNull ToolWindowManager toolWindowManager) {
        BarContainer bc = myPermanentBars.get(BarType.DEBUGGER);
        if (bc != null) {
          handleDebugBar(toolWindowManager, bc);
        }
      }

      private void handleDebugBar(@NotNull ToolWindowManager toolWindowManager,
                                  @NotNull BarContainer debugBar) {
        boolean hasActiveDebugSession = XDebugSessionService.getInstance(project).hasActiveDebugSession(project);
        if (isRelatesToDebug(toolWindowManager.getActiveToolWindowId()) &&
            hasActiveDebugSession) {
          debugBar.show();
        }
        else if (!hasActiveDebugSession) {
          debugBar.hide();
        }
      }

      private boolean isRelatesToDebug(@Nullable String toolWindowId) {
        return toolWindowId != null &&
               (toolWindowId.equals(ToolWindowId.DEBUG) ||
                toolWindowId.equals(ToolWindowId.RUN_DASHBOARD) ||
                toolWindowId.equals(ToolWindowId.SERVICES));
      }

      @Override
      public void toolWindowsRegistered(@NotNull List<String> ids, @NotNull ToolWindowManager toolWindowManager) {
        ApplicationManager.getApplication().assertIsDispatchThread();
        for (String id : ids) {
          ToolWindow toolWindow = toolWindowManager.getToolWindow(id);
          ToolWindowData toolWindowData = new ToolWindowData(toolWindow, id);
          myToolWindows.put(toolWindow, toolWindowData);
          // System.out.println("register tool-window: " + id);
          toolWindow.addContentManagerListener(toolWindowData);
        }
      }

      @Override
      public void toolWindowUnregistered(@NotNull String id, @NotNull ToolWindow toolWindow) {
        ApplicationManager.getApplication().assertIsDispatchThread();

        if (myToolWindows.isEmpty()) {
          // already cleared
          return;
        }

        ToolWindowData removed = myToolWindows.remove(toolWindow);
        if (removed == null) {
          LOG.error("try to remove unregistered tool-window: " + id + ", tw=" + toolWindow);
          return;
        }
        removed.release();
      }
    });
  }

  boolean isDisposed() {
    return myProject.isDisposed();
  }

  @NotNull
  BarContainer get(BarType type) {
    BarContainer result = myPermanentBars.get(type);
    if (result == null) {
      result = new BarContainer(type, TouchBar.EMPTY, null, null);
      _fillBarContainer(result);
      myPermanentBars.put(type, result);
    }
    return result;
  }

  boolean checkToolWindowContents(Component child) {
    for (ToolWindowData twd : myToolWindows.values()) {
      final ToolWindowData.ContentData cnt = twd.findParentContent(child);
      if (cnt != null) {
        cnt.setContextActionsVisible(true);
        return true;
      }
    }
    return false;
  }

  @Nullable
  EditorData findEditorDataByComponent(Component child) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    for (EditorData editorData : myEditors.values()) {
      final JComponent ecmp = editorData.editorHeader;
      if (child == ecmp || SwingUtilities.isDescendingFrom(child, ecmp)) {
        // System.out.println("focused header: " + ecmp);
        if (editorData.containerSearch == null) {
          // System.out.println("focused header of editor '" + editorData.editor + "', but BarContainer wasn't created (seems that ui-component doesn't contain actions for touchbar), header: " + ecmp);
          continue;
        }
        return editorData;
      }
    }

    return null;
  }

  @Nullable
  BarContainer findDebugToolWindowByComponent(Component child) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    ToolWindow dtw = toolWindowManager.getToolWindow(ToolWindowId.DEBUG);
    ToolWindow rtw = toolWindowManager.getToolWindow(RunDashboardManager.getInstance(myProject).getToolWindowId());

    Component compD = dtw != null ? dtw.getComponent() : null;
    Component compR = rtw != null ? rtw.getComponent() : null;
    if (compD == null && compR == null) {
      return null;
    }

    if (child == compD || child == compR
      || (compD != null && SwingUtilities.isDescendingFrom(child, compD))
      || (compR != null && SwingUtilities.isDescendingFrom(child, compR))) {
      return get(BarType.DEBUGGER);
    }

    return null;
  }

  void registerEditor(@NotNull Editor editor) {
    myEditors.put(editor, new EditorData(editor));
  }

  EditorData getEditorData(@NotNull Editor editor) {
    return myEditors.get(editor);
  }

  void removeEditor(@NotNull Editor editor) {
    // already cleared
    if (myEditors.isEmpty()) {
      return;
    }

    final EditorData removed = myEditors.remove(editor);
    if (removed == null) {
      // System.out.println("remove unregistered editor: " + editor);
      return;
    }

    removed.release();
  }

  private static void _fillBarContainer(@NotNull BarContainer container) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final @NotNull BarType type = container.getType();

    final String barId;
    final boolean replaceEsc;
    if (type == BarType.DEFAULT) {
      barId = "Default";
      replaceEsc = false;
    }
    else if (type == BarType.DEBUGGER) {
      barId = ToolWindowId.DEBUG;
      replaceEsc = true;
    }
    else {
      LOG.error("can't create touchbar, unknown context: " + type);
      return;
    }

    final ActionGroup mainLayout = BuildUtils.getCustomizedGroup(barId);
    if (mainLayout == null) {
      LOG.info("can't create touchbar because corresponding ActionGroup isn't defined (seems that user deleted it), context: " + barId);
      return;
    }

    Map<String, ActionGroup> strmod2alt = BuildUtils.getAltLayouts(mainLayout);
    Map<Long, TouchBar> alts = new HashMap<>();
    if (!strmod2alt.isEmpty()) {
      for (String modId : strmod2alt.keySet()) {
        final long mask = _str2mask(modId);
        if (mask == 0) {
          // System.out.println("ERROR: zero mask for modId="+modId);
          continue;
        }
        final @NotNull TouchBar altTouchbar =
          BuildUtils.buildFromCustomizedGroup(type.name() + "_" + modId, strmod2alt.get(modId), replaceEsc);
        alts.put(mask, altTouchbar);
        altTouchbar.setAllowSkipSlowUpdates(true);
      }
    }

    container.set(BuildUtils.buildFromCustomizedGroup(type.name(), mainLayout, replaceEsc), alts);
  }

  void releaseAll() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myPermanentBars.forEach((t, bc) -> bc.release());
    myPermanentBars.clear();
    myEditors.forEach((e, ed) -> ed.release());
    myEditors.clear();
    myToolWindows.forEach((tw, twd) -> twd.release());
    myToolWindows.clear();
    // System.out.println("released all data of project: " + myProject);
  }

  void reloadAll() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myPermanentBars.forEach((t, bc) -> {
      bc.release();
      _fillBarContainer(bc);
    });
    // System.out.println("reloaded permanent bars");
  }

  Collection<BarContainer> getAllContainers() {
    return myPermanentBars.values();
  }

  static long getUsedKeyMask() {
    return InputEvent.ALT_DOWN_MASK |
           InputEvent.META_DOWN_MASK |
           InputEvent.CTRL_DOWN_MASK |
           InputEvent.SHIFT_DOWN_MASK;
  }

  private static long _str2mask(@NotNull String modifierId) {
    if (!modifierId.contains(".")) {
      if (modifierId.equalsIgnoreCase("alt")) {
        return InputEvent.ALT_DOWN_MASK;
      }
      if (modifierId.equalsIgnoreCase("cmd")) {
        return InputEvent.META_DOWN_MASK;
      }
      if (modifierId.equalsIgnoreCase("ctrl")) {
        return InputEvent.CTRL_DOWN_MASK;
      }
      if (modifierId.equalsIgnoreCase("shift")) {
        return InputEvent.SHIFT_DOWN_MASK;
      }
      return 0;
    }

    final String[] spl = modifierId.split("\\.");
    long mask = 0;
    for (String sub : spl) {
      mask |= _str2mask(sub);
    }
    return mask;
  }

  static final class EditorData {
    final @NotNull Editor editor;
    JComponent editorHeader;

    ActionGroup actionsSearch;
    BarContainer containerSearch;

    EditorData(@NotNull Editor editor) {
      this.editor = editor;
    }

    void release() {
      if (containerSearch != null) {
        containerSearch.release();
      }
      containerSearch = null;
    }
  }

  class ToolWindowData implements ContentManagerListener {
    final @NotNull ToolWindow toolWindow;
    final @NotNull String toolWindowId;
    final @NotNull Map<Content, ContentData> contents = new HashMap<>();

    ToolWindowData(@NotNull ToolWindow toolWindow, @NotNull String toolWindowId) {
      this.toolWindow = toolWindow;
      this.toolWindowId = toolWindowId;
    }

    void release() {}

    @Nullable
    ContentData findParentContent(@NotNull Component childOfToolWindow) {
      if (contents.isEmpty()) {
        return null;
      }

      for (ContentData cd : contents.values()) {
        if (SwingUtilities.isDescendingFrom(childOfToolWindow, cd.content.getComponent())) {
          return cd;
        }
      }

      return null;
    }

    @Override
    public void contentAdded(@NotNull ContentManagerEvent event) {
      ApplicationManager.getApplication().assertIsDispatchThread();

      final Content content = event.getContent();
      final ContentData cd = contents.get(content);

      if (cd != null) {
        LOG.error("try to register existing content '" + content + "', will be skipped");
        return;
      }

      final ActionGroup optAction = _getTouchbarActions(content);
      if (optAction == null) {
        // System.out.println("\t\tskip content " + content + " of ToolWindow " + toolWindowId + " because it hasn't any touchbar actions");
        return;
      }

      // System.out.printf("register content of ToolWindow %s with touchbar-action '%s' [%s]\n", toolWindowId, optAction.toString(), ActionManager.getInstance().getId(optAction));
      _registerContent(content, optAction);

      //content.addPropertyChangeListener(evt -> {
      //  if (Content.PROP_COMPONENT.equals(evt.getPropertyName())) {
      //    // final Component oldComponent = (Component)evt.getOldValue();
      //    // final Component newComponent = (Component)evt.getNewValue();
      //    // final ActionGroup actions = _getTouchbarActions(newComponent);
      //    // TODO: update link to ToolWindowData.JComponent, ToolWindowData.AnAction, items of corresponding touchbar
      //  }
      //});
    }

    @Override
    public void contentRemoved(@NotNull ContentManagerEvent event) { _removeContent(event.getContent()); }

    @Override
    public void contentRemoveQuery(@NotNull ContentManagerEvent event) {}

    @Override
    public void selectionChanged(@NotNull ContentManagerEvent event) {}

    private void _registerContent(@NotNull Content content, @NotNull ActionGroup optActions) {
      ApplicationManager.getApplication().assertIsDispatchThread();

      ContentData cd = contents.get(content);
      if (cd != null) {
        LOG.error("try to register existing content '" + content + "' of ToolWindow " + toolWindowId + ", will be skipped");
        return;
      }

      // System.out.printf("register content of ToolWindow %s with touchbar-action '%s' [%s]\n", toolWindowId, optActions.toString(), ActionManager.getInstance().getId(optActions));

      cd = new ContentData(content, get(BarType.DEFAULT));
      contents.put(content, cd);
      cd.setOptionalContextActions(optActions);
    }

    private void _removeContent(@NotNull Content content) {
      ApplicationManager.getApplication().assertIsDispatchThread();

      ContentData cd = contents.get(content);
      if (cd == null) {
        // System.out.println("skip removing unregistered content: " + content + " of ToolWindow " + toolWindowId);
        return;
      }

      contents.remove(content);
      cd.setOptionalContextActions(null);
    }

    private final class ContentData {
      final @NotNull Content content;
      final @NotNull BarContainer barContainer;
      final @NotNull String contextName;

      ContentData(@NotNull Content content, @NotNull BarContainer barContainer) {
        this.content = content;
        this.barContainer = barContainer;
        contextName = toolWindowId + "_" + content.toString();
      }

      void setOptionalContextActions(ActionGroup group) {
        barContainer.setOptionalContextActions(group, contextName);
      }

      void setContextActionsVisible(boolean visible) {
        barContainer.setOptionalContextVisible(visible ? contextName : null);
      }
    }
  }

  private static ActionGroup _getTouchbarActions(Content content) {
    final JComponent component = content.getComponent();
    return _getTouchbarActions(component);
  }

  private static ActionGroup _getTouchbarActions(Component component) {
    return component instanceof DataProvider ? TouchbarDataKeys.ACTIONS_KEY.getData((DataProvider)component) : null;
  }
}
