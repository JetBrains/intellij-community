/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.actions.MarkObjectActionHandler;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.tree.ValueMarkerPresentationDialog;
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class XMarkObjectActionHandler extends MarkObjectActionHandler {
  @Override
  public void perform(@NotNull Project project, AnActionEvent event) {
    XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
    if (session == null) return;

    XValueMarkers<?, ?> markers = ((XDebugSessionImpl)session).getValueMarkers();
    XValueNodeImpl node = XDebuggerTreeActionBase.getSelectedNode(event.getDataContext());
    if (markers == null || node == null) return;
    XValue value = node.getValueContainer();

    ValueMarkup existing = markers.getMarkup(value);
    if (existing != null) {
      markers.unmarkValue(value);
    }
    else {
      ValueMarkerPresentationDialog dialog = new ValueMarkerPresentationDialog(node.getName());
      dialog.show();
      ValueMarkup markup = dialog.getConfiguredMarkup();
      if (dialog.isOK() && markup != null) {
        markers.markValue(value, markup);
      }
    }
    session.rebuildViews();
  }

  @Override
  public boolean isEnabled(@NotNull Project project, AnActionEvent event) {
    XValueMarkers<?, ?> markers = getValueMarkers(project);
    if (markers == null) return false;

    XValue value = XDebuggerTreeActionBase.getSelectedValue(event.getDataContext());
    return value != null && markers.canMarkValue(value);
  }

  @Override
  public boolean isMarked(@NotNull Project project, @NotNull AnActionEvent event) {
    XValueMarkers<?, ?> markers = getValueMarkers(project);
    if (markers == null) return false;

    XValue value = XDebuggerTreeActionBase.getSelectedValue(event.getDataContext());
    return value != null && markers.getMarkup(value) != null;
  }

  @Override
  public boolean isHidden(@NotNull Project project, AnActionEvent event) {
    return getValueMarkers(project) == null;
  }

  @Nullable
  private static XValueMarkers<?, ?> getValueMarkers(@NotNull Project project) {
    XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
    return session != null ? ((XDebugSessionImpl)session).getValueMarkers() : null;
  }
}
