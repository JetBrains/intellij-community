// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.themes;

import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class ThemeJsonPsiReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(
      PlatformPatterns.psiElement(JsonStringLiteral.class)
        .inVirtualFile(PlatformPatterns.virtualFile().with(new PatternCondition<>("theme.json") {
          @Override
          public boolean accepts(@NotNull VirtualFile file, ProcessingContext context) {
            return ThemeJsonUtil.isThemeFilename(file.getName());
          }
        })),
      new ThemeJsonNamedColorPsiReferenceProvider());
  }
}
