/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author sergey.evdokimov
 */
public abstract class I18nizeHandlerProvider {

  public static final ExtensionPointName<I18nizeHandlerProvider> EP_NAME = ExtensionPointName.create("com.intellij.java-i18n.i18nizeHandlerProvider");

  @Nullable
  public abstract I18nQuickFixHandler getHandler(@NotNull final PsiFile psiFile, @NotNull final Editor editor, @NotNull TextRange range);

}
