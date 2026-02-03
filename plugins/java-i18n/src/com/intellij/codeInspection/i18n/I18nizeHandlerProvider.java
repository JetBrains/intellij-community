// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.i18n;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class I18nizeHandlerProvider {

  public static final ExtensionPointName<I18nizeHandlerProvider> EP_NAME = ExtensionPointName.create("com.intellij.java-i18n.i18nizeHandlerProvider");

  public abstract @Nullable I18nQuickFixHandler<?> getHandler(final @NotNull PsiFile psiFile, final @NotNull Editor editor, @NotNull TextRange range);

}
