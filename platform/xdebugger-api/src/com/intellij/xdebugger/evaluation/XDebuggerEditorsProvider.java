/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.xdebugger.evaluation;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public abstract class XDebuggerEditorsProvider {
  @NotNull
  public abstract FileType getFileType();

  /**
   * @deprecated Use {@link #createDocument(com.intellij.openapi.project.Project, com.intellij.xdebugger.XExpression,
   * com.intellij.xdebugger.XSourcePosition, com.intellij.xdebugger.evaluation.EvaluationMode)} instead
   */
  @NotNull
  @Deprecated
  public Document createDocument(@NotNull Project project,
                                 @NotNull String text,
                                 @Nullable XSourcePosition sourcePosition,
                                 @NotNull EvaluationMode mode) {
    throw new AbstractMethodError();
  }

  @NotNull
  public Document createDocument(@NotNull Project project,
                                          @NotNull XExpression expression,
                                          @Nullable XSourcePosition sourcePosition,
                                          @NotNull EvaluationMode mode) {
    //noinspection deprecation
    return createDocument(project, expression.getExpression(), sourcePosition, mode);
  }

  @NotNull
  public Collection<Language> getSupportedLanguages(@NotNull Project project, @Nullable XSourcePosition sourcePosition) {
    FileType type = getFileType();
    if (type instanceof LanguageFileType) {
      return Collections.singleton(((LanguageFileType)type).getLanguage());
    }
    return Collections.emptyList();
  }

  @NotNull
  public XExpression createExpression(@NotNull Project project, @NotNull Document document, @Nullable Language language, @NotNull EvaluationMode mode) {
    return XDebuggerUtil.getInstance().createExpression(document.getText(), language, null, mode);
  }

  @NotNull
  public InlineDebuggerHelper getInlineDebuggerHelper() {
    return InlineDebuggerHelper.DEFAULT;
  }
}