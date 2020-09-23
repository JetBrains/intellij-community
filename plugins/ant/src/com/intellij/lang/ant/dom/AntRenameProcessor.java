// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.dom;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Trinity;
import com.intellij.pom.PomTarget;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 */
public class AntRenameProcessor extends RenamePsiElementProcessor{

  @Override
  public void prepareRenaming(@NotNull PsiElement element, @NotNull @NlsSafe String newName, @NotNull Map<PsiElement, String> allRenames) {
    final AntDomElement antElement = convertToAntDomElement(element);
    String propName = null;
    if (antElement instanceof AntDomProperty) {
      propName = ((AntDomProperty)antElement).getName().getStringValue();
    }
    else if (antElement instanceof AntDomAntCallParam) {
      propName = ((AntDomAntCallParam)antElement).getName().getStringValue();
    }
    if (propName != null) {
      final AntDomProject contextProject = antElement.getContextAntProject();
      final List<PsiElement> additional = AntCallParamsFinder.resolve(contextProject, propName);
      for (PsiElement psiElement : additional) {
        allRenames.put(psiElement, newName);
      }
      if (antElement instanceof AntDomAntCallParam) {
        final Trinity<PsiElement, Collection<String>, PropertiesProvider> result = PropertyResolver.resolve(contextProject, propName, null);
        if (result.getFirst() != null) {
          allRenames.put(result.getFirst(), newName);
        }
      }
    }
  }

  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    final AntDomElement antElement = convertToAntDomElement(element);
    if (antElement instanceof AntDomProperty || antElement instanceof AntDomAntCallParam) {
      return true;
    }
    return false;
  }

  @Nullable
  private static AntDomElement convertToAntDomElement(PsiElement element) {
    if (element instanceof PomTargetPsiElement) {
      final PomTarget target = ((PomTargetPsiElement)element).getTarget();
      if (target instanceof DomTarget) {
        final DomElement domElement = ((DomTarget)target).getDomElement();
        if (domElement instanceof AntDomElement) {
          return (AntDomElement)domElement;
        }
      }
    }
    return null;
  }
}
