// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.ui.*;
import com.intellij.ui.popup.util.DetailView;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem;
import com.intellij.xdebugger.impl.breakpoints.ui.XLightBreakpointPropertiesPanel;
import com.intellij.xdebugger.ui.DebuggerColors;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

class XBreakpointItem extends BreakpointItem {
  private final XBreakpoint<?> myBreakpoint;
  private XLightBreakpointPropertiesPanel myPropertiesPanel;

  XBreakpointItem(XBreakpoint<?> breakpoint) {
    myBreakpoint = breakpoint;
  }

  @Override
  public void setupRenderer(ColoredListCellRenderer renderer, Project project, boolean selected) {
    setupGenericRenderer(renderer, true);
  }

  @Override
  public void setupRenderer(ColoredTreeCellRenderer renderer, Project project, boolean selected) {
    setupGenericRenderer(renderer, false);
  }

  @Override
  public void setupGenericRenderer(SimpleColoredComponent renderer, boolean plainView) {
    renderer.setIcon(getIcon());
    final SimpleTextAttributes attributes =
      myBreakpoint.isEnabled() ? SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.GRAYED_ATTRIBUTES;
    renderer.append(StringUtil.notNullize(getDisplayText()), attributes);
    String description = getUserDescription();
    if (!StringUtil.isEmpty(description)) {
      renderer.append(" (" + description + ")", SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES);
    }
  }

  @Override
  public String getDisplayText() {
    return XBreakpointUtil.getShortText(myBreakpoint);
  }

  @Nullable
  @Nls
  private String getUserDescription() {
    return ((XBreakpointBase<?, ?, ?>)myBreakpoint).getUserDescription();
  }

  @Override
  public Icon getIcon() {
    return ((XBreakpointBase<?, ?, ?>)myBreakpoint).getIcon();
  }

  @Override
  public String speedSearchText() {
    return getDisplayText() + " " + StringUtil.notNullize(getUserDescription());
  }

  @Override
  @Nls
  public String footerText() {
    return null;
  }

  @Override
  public void saveState() {
    if (myPropertiesPanel != null) {
      myPropertiesPanel.saveProperties();
    }
  }

  @Override
  public void doUpdateDetailView(DetailView panel, boolean editorOnly) {
    XBreakpointBase breakpoint = (XBreakpointBase)myBreakpoint;
    Project project = breakpoint.getProject();
    //saveState();
    if (myPropertiesPanel != null) {
      myPropertiesPanel.dispose();
      myPropertiesPanel = null;
    }
    if (!editorOnly) {
      myPropertiesPanel = new XLightBreakpointPropertiesPanel(project, getManager(), breakpoint, true, false);

      panel.setPropertiesPanel(myPropertiesPanel.getMainPanel());
    }

    panel.clearEditor();
    ReadAction.nonBlocking(() -> myBreakpoint.getSourcePosition())
      .finishOnUiThread(ModalityState.defaultModalityState(), sourcePosition -> {
        if (sourcePosition != null && sourcePosition.getFile().isValid()) {
          showInEditor(panel, sourcePosition.getFile(), sourcePosition.getLine());
        }
        else {
          panel.clearEditor();
        }
      })
      .coalesceBy(panel)
      .submit(AppExecutorUtil.getAppExecutorService());

    if (myPropertiesPanel != null) {
      myPropertiesPanel.setDetailView(panel);
      myPropertiesPanel.loadProperties();
      myPropertiesPanel.getMainPanel().revalidate();
    }
  }

  private void showInEditor(DetailView panel, VirtualFile virtualFile, int line) {
    TextAttributes attributes =
      EditorColorsManager.getInstance().getGlobalScheme().getAttributes(DebuggerColors.BREAKPOINT_ATTRIBUTES);

    // null attributes to avoid the new highlighter for a line breakpoints
    DetailView.PreviewEditorState state =
      DetailView.PreviewEditorState.create(virtualFile, line, myBreakpoint instanceof XLineBreakpoint ? null : attributes);

    if (state.equals(panel.getEditorState())) {
      return;
    }

    panel.navigateInPreviewEditor(state);

    TextAttributes softerAttributes = attributes.clone();
    Color backgroundColor = softerAttributes.getBackgroundColor();
    if (backgroundColor != null) {
      softerAttributes.setBackgroundColor(ColorUtil.desaturate(backgroundColor, 10));
    }

    Editor editor = panel.getEditor();
    if (editor != null) {
      MarkupModel editorModel = editor.getMarkupModel();
      MarkupModel documentModel =
        DocumentMarkupModel.forDocument(editor.getDocument(), editor.getProject(), false);

      for (RangeHighlighter highlighter : documentModel.getAllHighlighters()) {
        if (highlighter.getUserData(DebuggerColors.BREAKPOINT_HIGHLIGHTER_KEY) == Boolean.TRUE) {
          int line1 = editor.offsetToLogicalPosition(highlighter.getStartOffset()).line;
          if (line1 != line) {
            if (highlighter.getTargetArea() == HighlighterTargetArea.LINES_IN_RANGE) {
              editorModel.addLineHighlighter(line1, DebuggerColors.BREAKPOINT_HIGHLIGHTER_LAYER + 1, softerAttributes);
            }
            else {
              editorModel.addRangeHighlighter(highlighter.getStartOffset(), highlighter.getEndOffset(),
                                              DebuggerColors.BREAKPOINT_HIGHLIGHTER_LAYER + 1, softerAttributes,
                                              HighlighterTargetArea.EXACT_RANGE);
            }
          }
        }
      }
    }
  }

  @Override
  public void navigate(boolean requestFocus) {
    Navigatable navigatable = myBreakpoint.getNavigatable();
    if (navigatable != null && navigatable.canNavigate()) {
      navigatable.navigate(requestFocus);
    }
  }

  @Override
  public boolean canNavigate() {
    Navigatable navigatable = myBreakpoint.getNavigatable();
    return navigatable != null && navigatable.canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    Navigatable navigatable = myBreakpoint.getNavigatable();
    return navigatable != null && navigatable.canNavigateToSource();
  }

  private XBreakpointManagerImpl getManager() {
    return ((XBreakpointBase<?, ?, ?>)myBreakpoint).getBreakpointManager();
  }

  @Override
  public boolean allowedToRemove() {
    return !getManager().isDefaultBreakpoint(myBreakpoint);
  }

  @Override
  public void removed(Project project) {
    XDebuggerUtil.getInstance().removeBreakpoint(project, myBreakpoint);
  }

  @Override
  public Object getBreakpoint() {
    return myBreakpoint;
  }

  @Override
  public boolean isEnabled() {
    return myBreakpoint.isEnabled();
  }

  @Override
  public void setEnabled(boolean state) {
    myBreakpoint.setEnabled(state);
  }

  @Override
  public boolean isDefaultBreakpoint() {
    return getManager().isDefaultBreakpoint(myBreakpoint);
  }

  @Override
  public int compareTo(BreakpointItem breakpointItem) {
    if (breakpointItem.getBreakpoint() instanceof XBreakpointBase) {
      return ((XBreakpointBase)myBreakpoint).compareTo((XBreakpoint)breakpointItem.getBreakpoint());
    }
    else {
      return 0;
    }
  }

  @Override
  public void dispose() {
    if (myPropertiesPanel != null) {
      myPropertiesPanel.dispose();
      myPropertiesPanel = null;
    }
  }
}
