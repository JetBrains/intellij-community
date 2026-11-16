// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.editor.EditorLinePainter;
import com.intellij.openapi.editor.LineExtensionInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.IntFunction;

/// @noinspection TypeParameterExtendsFinalClass
@ApiStatus.Internal
public final class EditorLineExtensions {
  private final EditorView view;
  private final @Nullable Project project;
  private @Nullable VirtualFile virtualFile;
  private final List<@NotNull IntFunction<? extends @NotNull Collection<? extends LineExtensionInfo>>> extensions;

  public EditorLineExtensions(EditorView view, @Nullable Project project, @Nullable VirtualFile virtualFile) {
    this.view = view;
    this.project = project;
    this.virtualFile = virtualFile;
    this.extensions = new SmartList<>();
  }

  public void setVirtualFile(@Nullable VirtualFile virtualFile) {
    this.virtualFile = virtualFile;
  }

  public void registerLineExtensionPainter(@NotNull IntFunction<? extends @NotNull Collection<? extends LineExtensionInfo>> lineExtensionPainter) {
    extensions.add(lineExtensionPainter);
  }

  public boolean processLineExtensions(int line, @NotNull Processor<? super LineExtensionInfo> processor) {
    for (var painter : extensions) {
      for (LineExtensionInfo extension : painter.apply(line)) {
        if (!processor.process(extension)) {
          return false;
        }
      }
    }
    if (project != null && virtualFile != null) {
      for (EditorLinePainter painter : EditorLinePainter.EP_NAME.getExtensionList()) {
        if (LightEdit.owns(project) && !(painter instanceof LightEditCompatible)) {
          continue;
        }
        Collection<LineExtensionInfo> extensions = painter.getLineExtensions(project, virtualFile, line);
        if (extensions != null) {
          for (LineExtensionInfo info : extensions) {
            if (!processor.process(info)) {
              return false;
            }
          }
        }
      }
    }
    return true;
  }

  @NotNull List<LineExtensionLayout> getLayout(int logicalLine) {
    ArrayList<LineExtensionLayout> layout = null;
    for (var extension : extensions) {
      for (LineExtensionInfo info : extension.apply(logicalLine)) {
        if (layout == null) {
          layout = new ArrayList<>();
        }
        LineLayout text = LineLayout.createForStandaloneText(view, info.getText(), info.getFontType());
        layout.add(new LineExtensionLayout(info, text));
      }
    }
    if (project != null && virtualFile != null) {
      for (EditorLinePainter painter : EditorLinePainter.EP_NAME.getExtensionList()) {
        if (LightEdit.owns(project) && !(painter instanceof LightEditCompatible)) {
          continue;
        }
        Collection<LineExtensionInfo> extensions = painter.getLineExtensions(project, virtualFile, logicalLine);
        if (extensions != null) {
          for (LineExtensionInfo info : extensions) {
            if (layout == null) {
              layout = new ArrayList<>();
            }
            LineLayout text = LineLayout.createForStandaloneText(view, info.getText(), info.getFontType());
            layout.add(new LineExtensionLayout(info, text));
          }
        }
      }
    }
    return layout != null ? layout : Collections.emptyList();
  }
}
