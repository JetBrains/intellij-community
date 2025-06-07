// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProvider;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import javax.swing.*;

public final class GrFileItemPresentationProvider implements ItemPresentationProvider<GroovyFile> {
  @Override
  public ItemPresentation getPresentation(final @NotNull GroovyFile file) {
    return new ItemPresentation() {
      @Override
      public String getPresentableText() {
        return GroovyBundle.message("groovy.file.0", file.getName());
      }

      @Override
      public String getLocationString() {
        PsiDirectory directory = file.getContainingDirectory();
        return ItemPresentationProviders.getItemPresentation(directory).getPresentableText();
      }

      @Override
      public Icon getIcon(boolean unused) {
        return file.getIcon(Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS);
      }
    };
  }
}
