// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.references;

import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UseScopeEnlarger;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class DomTargetUseScopeEnlarger extends UseScopeEnlarger {
  @Override
  public @Nullable SearchScope getAdditionalUseScope(@NotNull PsiElement element) {
    if (element instanceof PomTargetPsiElement pt
        && pt.getTarget() instanceof DomTarget domTarget) {
      DomElement domElement = domTarget.getDomElement();
      if (domElement != null
          && domElement.getXmlTag() != null
          && "registryKey".equals(domElement.getXmlTag().getName())) {
        return GlobalSearchScope.projectScope(pt.getProject());
      }
    }
    return null;
  }
}
