// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

class ProjectData {
  private static final Logger LOG = Logger.getInstance(ProjectData.class);

  private final @NotNull Project myProject;
  private final Map<BarType, BarContainer> myPermanentBars = new HashMap<>();
  private final Map<Editor, EditorData> myEditors = new HashMap<>();

  private AtomicInteger myActiveDebugSessions = new AtomicInteger(0);

  ProjectData(@NotNull Project project) {
    myProject = project;

    myProject.getMessageBus().connect().subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
      @Override
      public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler) {
        // System.out.println("processStarted: " + executorId);
        if (executorId.equals(ToolWindowId.DEBUG))
          myActiveDebugSessions.incrementAndGet();
      }
      @Override
      public void processTerminated(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler, int exitCode) {
        // System.out.println("processTerminated: " + executorId);
        if (executorId.equals(ToolWindowId.DEBUG)) {
          final int val = myActiveDebugSessions.decrementAndGet();
          if (val < 0) {
            LOG.error("received 'processTerminated' when no process wasn't started");
            myActiveDebugSessions.incrementAndGet();
          }
        }
      }
    });
  }

  boolean isDisposed() { return myProject.isDisposed(); }

  @Nullable BarContainer get(BarType type) {
    BarContainer result = myPermanentBars.get(type);
    if (result == null) {
      result = new BarContainer(type, TouchBar.EMPTY, null);
      _fillBarContainer(result);
      myPermanentBars.put(type, result);
    }
    return result;
  }

  @Nullable BarContainer findByComponent(Component child) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    for (EditorData editorData : myEditors.values()) {
      final JComponent ecmp = editorData.editorHeader;
      if (child == ecmp || SwingUtilities.isDescendingFrom(child, ecmp)) {
        // System.out.println("focused header: " + ecmp);
        if (editorData.containerSearch == null) {
          LOG.error("focused header of editor: " + editorData.editor + ", but BarContainer wasn't created, header: " + ecmp);
          continue;
        }
        return editorData.containerSearch;
      }
    }

    if (myActiveDebugSessions.get() <= 0)
      return null;

    final ToolWindowManagerEx twm = ToolWindowManagerEx.getInstanceEx(myProject);
    if (twm == null)
      return null;

    final ToolWindow dtw = twm.getToolWindow(ToolWindowId.DEBUG);
    final ToolWindow rtw = twm.getToolWindow(ToolWindowId.RUN_DASHBOARD);

    final Component compD = dtw != null ? dtw.getComponent() : null;
    final Component compR = rtw != null ? rtw.getComponent() : null;
    if (compD == null && compR == null)
      return null;

    if (
      child == compD || child == compR
      || (compD != null && SwingUtilities.isDescendingFrom(child, compD))
      || (compR != null && SwingUtilities.isDescendingFrom(child, compR))
    )
      return get(BarType.DEBUGGER);

    return null;
  }

  EditorData registerEditor(@NotNull Editor editor) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final EditorData result = new EditorData(editor);
    myEditors.put(editor, result);
    return result;
  }

  EditorData getEditorData(@NotNull Editor editor) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myEditors.get(editor);
  }

  void removeEditor(@NotNull Editor editor) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final EditorData removed = myEditors.remove(editor);
    if (removed == null) {
      // System.out.println("try to remove unregistered editor: " + editor);
      return;
    }

    removed.release();
  }

  private void _fillBarContainer(@NotNull BarContainer container) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final @NotNull BarType type = container.getType();

    final String barId;
    final boolean replaceEsc;
    if (type == BarType.DEFAULT) {
      barId = "Default";
      replaceEsc = false;
    } else if (type == BarType.DEBUGGER) {
      barId = ToolWindowId.DEBUG;
      replaceEsc = true;
    } else {
      LOG.error("can't create touchbar, unknown context: " + type);
      return;
    }

    final ActionGroup mainLayout = BuildUtils.getCustomizedGroup(barId);
    if (mainLayout == null) {
      LOG.info("can't create touchbar because corresponding ActionGroup isn't defined (seems that user deleted it), context: " + barId);
      return;
    }

    final Map<String, ActionGroup> strmod2alt = BuildUtils.getAltLayouts(mainLayout);
    final Map<Long, TouchBar> alts = new HashMap<>();
    if (strmod2alt != null && !strmod2alt.isEmpty()) {
      for (String modId: strmod2alt.keySet()) {
        final long mask = _str2mask(modId);
        if (mask == 0) {
          // System.out.println("ERROR: zero mask for modId="+modId);
          continue;
        }
        alts.put(mask, TouchBar.buildFromCustomizedGroup(type.name() + "_" + modId, strmod2alt.get(modId), replaceEsc));
      }
    }

    container.set(TouchBar.buildFromCustomizedGroup(type.name(), mainLayout, replaceEsc), alts);
  }

  void releaseAll() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myPermanentBars.forEach((t, bc)->bc.release());
    myPermanentBars.clear();
    myEditors.forEach((e, ed) -> ed.release());
  }

  void reloadAll() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myPermanentBars.forEach((t, bc)->{
      bc.release();
      _fillBarContainer(bc);
    });
  }

  void forEach(BiConsumer<? super BarType, ? super BarContainer> proc) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myPermanentBars.forEach(proc);
  }

  Collection<BarContainer> getAllContainers() { return myPermanentBars.values(); }

  int getDbgSessions() { return myActiveDebugSessions.get(); }

  private static long _str2mask(@NotNull String modifierId) {
    if (!modifierId.contains(".")) {
      if (modifierId.equalsIgnoreCase("alt"))
        return InputEvent.ALT_DOWN_MASK;
      if (modifierId.equalsIgnoreCase("cmd"))
        return InputEvent.META_DOWN_MASK;
      if (modifierId.equalsIgnoreCase("shift"))
        return InputEvent.SHIFT_DOWN_MASK;
      return 0;
    }

    final String[] spl = modifierId.split("\\.");
    if (spl == null)
      return 0;

    long mask = 0;
    for (String sub: spl)
      mask |= _str2mask(sub);
    return mask;
  }

  static class EditorData {
    final @NotNull Editor editor;
    JComponent editorHeader;

    ActionGroup actionsSearch;
    BarContainer containerSearch;

    EditorData(Editor editor) { this.editor = editor; }
    void release() {
      if (containerSearch != null)
        containerSearch.release();
      containerSearch = null;
    }
  }
}
