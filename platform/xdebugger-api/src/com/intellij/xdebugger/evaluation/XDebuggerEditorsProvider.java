// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.evaluation;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Collections;

public abstract class XDebuggerEditorsProvider {
  public abstract @NotNull FileType getFileType();

  /** @deprecated Use {@link #createDocument(Project, XExpression, XSourcePosition, EvaluationMode)} instead */
  @Deprecated
  public @NotNull Document createDocument(@NotNull Project project,
                                 @NotNull String text,
                                 @Nullable XSourcePosition sourcePosition,
                                 @NotNull EvaluationMode mode) {
    throw new AbstractMethodError("createDocument must be implemented in " + getClass());
  }

  public @NotNull Document createDocument(@NotNull Project project,
                                          @NotNull XExpression expression,
                                          @Nullable XSourcePosition sourcePosition,
                                          @NotNull EvaluationMode mode) {
    return createDocument(project, expression.getExpression(), sourcePosition, mode);
  }
  
  public void afterEditorCreated(@Nullable Editor editor) {}

  public @NotNull @Unmodifiable Collection<Language> getSupportedLanguages(@NotNull Project project, @Nullable XSourcePosition sourcePosition) {
    FileType type = getFileType();
    return type instanceof LanguageFileType ? Collections.singleton(((LanguageFileType)type).getLanguage()) : Collections.emptyList();
  }

  public @NotNull XExpression createExpression(@NotNull Project project, @NotNull Document document, @Nullable Language language, @NotNull EvaluationMode mode) {
    return XDebuggerUtil.getInstance().createExpression(document.getText(), language, null, mode);
  }

  public @NotNull InlineDebuggerHelper getInlineDebuggerHelper() {
    return InlineDebuggerHelper.DEFAULT;
  }

  /**
   * Return false to disable evaluate expression field in the debugger tree.
   */
  @ApiStatus.Experimental
  public boolean isEvaluateExpressionFieldEnabled() {
    return true;
  }
}