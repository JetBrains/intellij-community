// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.inline;

import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class DebuggerInlayListener implements EditorMouseMotionListener, EditorMouseListener {
  private final Project myProject;
  private Inlay lastHoveredInlay = null;
  private boolean myListening;


  public DebuggerInlayListener(@NotNull Project project) {
    myProject = project;
  }

  public void startListening() {
    if (!myListening) {
      myListening = true;
      EditorEventMulticaster multicaster = EditorFactory.getInstance().getEventMulticaster();
      multicaster.addEditorMouseMotionListener(this, myProject);
      multicaster.addEditorMouseListener(this, myProject);
    }
  }

  @Override
  public void mouseMoved(@NotNull EditorMouseEvent event) {
    Inlay inlay = event.getInlay();
    if (lastHoveredInlay != null) {
      InlineDebugRendererBase renderer = (InlineDebugRendererBase)lastHoveredInlay.getRenderer();
      if (lastHoveredInlay != event.getInlay()) {
        renderer.onMouseExit(lastHoveredInlay);
      }
      lastHoveredInlay = null;
    }
    if (inlay != null) {
      if (inlay.getRenderer() instanceof InlineDebugRendererBase) {
        ((InlineDebugRendererBase)inlay.getRenderer()).onMouseMove(inlay, event);
        lastHoveredInlay = inlay;
      } else {
        lastHoveredInlay = null;
      }
    }
  }

  @Override
  public void mouseClicked(@NotNull EditorMouseEvent event) {
    if (event.isConsumed()) return;
    Inlay inlay = event.getInlay();
    if (inlay != null && inlay.getRenderer() instanceof InlineDebugRendererBase) {
      ((InlineDebugRendererBase)inlay.getRenderer()).onClick(inlay, event);
      event.consume();
    }
  }

  public static DebuggerInlayListener getInstance(Project project) {
    return project.getService(DebuggerInlayListener.class);
  }
}
