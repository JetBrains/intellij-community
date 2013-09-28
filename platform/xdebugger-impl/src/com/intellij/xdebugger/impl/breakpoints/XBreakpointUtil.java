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
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointPanelProvider;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author nik
 */
public class XBreakpointUtil {
  private XBreakpointUtil() {
  }

  public static <B extends XBreakpoint<?>> String getShortText(B breakpoint) {
    return getType(breakpoint).getShortText(breakpoint);
  }

  public static <B extends XBreakpoint<?>> String getDisplayText(@NotNull B breakpoint) {
    return getType(breakpoint).getDisplayText(breakpoint);
  }

  public static <B extends XBreakpoint<?>> XBreakpointType<B, ?> getType(@NotNull B breakpoint) {
    //noinspection unchecked
    return (XBreakpointType<B,?>)breakpoint.getType();
  }

  @Nullable
  public static XBreakpointType<?,?> findType(@NotNull @NonNls String id) {
    for (XBreakpointType breakpointType : getBreakpointTypes()) {
      if (id.equals(breakpointType.getId())) {
        return breakpointType;
      }
    }
    return null;
  }

  public static XBreakpointType<?,?>[] getBreakpointTypes() {
    return XBreakpointType.EXTENSION_POINT_NAME.getExtensions();
  }

  @NotNull
  public static Pair<GutterIconRenderer, Object> findSelectedBreakpoint(@NotNull final Project project, @NotNull final Editor editor) {
    int offset = editor.getCaretModel().getOffset();
    Document editorDocument = editor.getDocument();

    DebuggerSupport[] debuggerSupports = DebuggerSupport.getDebuggerSupports();
    for (DebuggerSupport debuggerSupport : debuggerSupports) {
      final BreakpointPanelProvider<?> provider = debuggerSupport.getBreakpointPanelProvider();

      final int textLength = editor.getDocument().getTextLength();
      if (offset > textLength) {
        offset = textLength;
      }

      Object breakpoint = provider.findBreakpoint(project, editorDocument, offset);
      if (breakpoint != null) {
        final GutterIconRenderer iconRenderer = provider.getBreakpointGutterIconRenderer(breakpoint);
        return Pair.create(iconRenderer, breakpoint);
      }
    }
    return Pair.create(null, null);
  }

  public static List<BreakpointPanelProvider> collectPanelProviders() {
    List<BreakpointPanelProvider> panelProviders = new ArrayList<BreakpointPanelProvider>();
    for (DebuggerSupport debuggerSupport : DebuggerSupport.getDebuggerSupports()) {
      panelProviders.add(debuggerSupport.getBreakpointPanelProvider());
    }
    Collections.sort(panelProviders, new Comparator<BreakpointPanelProvider>() {
      @Override
      public int compare(BreakpointPanelProvider o1, BreakpointPanelProvider o2) {
        return o2.getPriority() - o1.getPriority();
      }
    });
    return panelProviders;
  }

  @Nullable
  public static DebuggerSupport getDebuggerSupport(Project project, BreakpointItem breakpointItem) {
    DebuggerSupport[] debuggerSupports = DebuggerSupport.getDebuggerSupports();
    List<BreakpointItem> items = new ArrayList<BreakpointItem>();
    for (DebuggerSupport support : debuggerSupports) {
      support.getBreakpointPanelProvider().provideBreakpointItems(project, items);
      if (items.contains(breakpointItem))
        return support;
      items.clear();
    }
    return null;
  }
}
