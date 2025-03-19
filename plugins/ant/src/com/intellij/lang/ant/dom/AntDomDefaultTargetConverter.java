// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.dom;

import com.intellij.lang.ant.AntSupport;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiReference;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.CustomReferenceConverter;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 */
public class AntDomDefaultTargetConverter extends Converter<TargetResolver.Result> implements CustomReferenceConverter<TargetResolver.Result>{

  @Override
  public PsiReference @NotNull [] createReferences(final GenericDomValue<TargetResolver.Result> value, PsiElement element, ConvertContext context) {
    return new PsiReference[] {new AntDomTargetReference(element)};
  }

  @Override
  public @Nullable TargetResolver.Result fromString(@Nullable @NonNls String s, @NotNull ConvertContext context) {
    final AntDomElement element = AntSupport.getInvocationAntDomElement(context);
    if (element != null && s != null) {
      final AntDomProject project = element.getAntProject();
      AntDomProject projectToSearchFrom;
      final AntDomAnt antDomAnt = element.getParentOfType(AntDomAnt.class, false);
      if (antDomAnt != null) {
        final PsiFileSystemItem antFile = antDomAnt.getAntFilePath().getValue();
        projectToSearchFrom = antFile instanceof PsiFile? AntSupport.getAntDomProjectForceAntFile((PsiFile)antFile) : null;
      }
      else {
        projectToSearchFrom = project.getContextAntProject();
      }
      if (projectToSearchFrom == null) {
        return null;
      }
      final TargetResolver.Result result = TargetResolver.resolve(projectToSearchFrom, null, s);
      result.setRefsString(s);
      return result;
    }
    return null;
  }

  @Override
  public @Nullable String toString(@Nullable TargetResolver.Result result, @NotNull ConvertContext context) {
    return result != null? result.getRefsString() : null;
  }

}
