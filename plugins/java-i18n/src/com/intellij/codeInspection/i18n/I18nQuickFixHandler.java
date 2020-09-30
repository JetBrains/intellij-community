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
package com.intellij.codeInspection.i18n;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.PropertyCreationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UExpression;

import java.util.Collection;

public interface I18nQuickFixHandler<T extends UExpression> {
  void checkApplicability(final PsiFile psiFile,
                          final Editor editor) throws IncorrectOperationException;
  void performI18nization(final PsiFile psiFile,
                          final Editor editor,
                          T literalExpression,
                          Collection<PropertiesFile> propertiesFiles,
                          String key,
                          String value,
                          String i18nizedText,
                          UExpression[] parameters,
                          PropertyCreationHandler propertyCreationHandler) throws IncorrectOperationException;

  T getEnclosingLiteral(PsiFile file, Editor editor);

  @Nullable
  JavaI18nizeQuickFixDialog<T> createDialog(Project project, Editor editor, PsiFile psiFile);
}
