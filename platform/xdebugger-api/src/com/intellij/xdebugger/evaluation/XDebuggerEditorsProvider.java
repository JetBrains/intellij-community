// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.evaluation;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public abstract class XDebuggerEditorsProvider {
  @NotNull
  public abstract FileType getFileType();

  /** @deprecated Use {@link #createDocument(Project, XExpression, XSourcePosition, EvaluationMode)} instead */
  @NotNull
  @Deprecated
  @SuppressWarnings("DeprecatedIsStillUsed")
  public Document createDocument(@NotNull Project project,
                                 @NotNull String text,
                                 @Nullable XSourcePosition sourcePosition,
                                 @NotNull EvaluationMode mode) {
    throw new AbstractMethodError();
  }

  @NotNull
  @SuppressWarnings("deprecation")
  public Document createDocument(@NotNull Project project,
                                 @NotNull XExpression expression,
                                 @Nullable XSourcePosition sourcePosition,
                                 @NotNull EvaluationMode mode) {
    return createDocument(project, expression.getExpression(), sourcePosition, mode);
  }

  @NotNull
  public Collection<Language> getSupportedLanguages(@NotNull Project project, @Nullable XSourcePosition sourcePosition) {
    FileType type = getFileType();
    return type instanceof LanguageFileType ? Collections.singleton(((LanguageFileType)type).getLanguage()) : Collections.emptyList();
  }

  @NotNull
  public XExpression createExpression(@NotNull Project project, @NotNull Document document, @Nullable Language language, @NotNull EvaluationMode mode) {
    return XDebuggerUtil.getInstance().createExpression(document.getText(), language, null, mode);
  }

  @NotNull
  public InlineDebuggerHelper getInlineDebuggerHelper() {
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