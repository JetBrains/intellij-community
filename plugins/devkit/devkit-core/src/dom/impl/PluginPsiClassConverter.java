// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ExtendClass;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.PsiClassConverter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.util.PsiUtil;

public class PluginPsiClassConverter extends PsiClassConverter {

  @Override
  protected GlobalSearchScope getScope(@NotNull ConvertContext context) {
    final Project project = context.getProject();

    if (PsiUtil.isIdeaProject(project)) {
      return GlobalSearchScope.allScope(project);
    }

    return super.getScope(context);
  }

  @Override
  protected JavaClassReferenceProvider createClassReferenceProvider(GenericDomValue<PsiClass> genericDomValue,
                                                                    ConvertContext context,
                                                                    ExtendClass extendClass) {
    final JavaClassReferenceProvider provider = super.createClassReferenceProvider(genericDomValue, context, extendClass);
    provider.setOption(JavaClassReferenceProvider.JVM_FORMAT, Boolean.TRUE);
    provider.setOption(JavaClassReferenceProvider.ALLOW_DOLLAR_NAMES, Boolean.TRUE);
    return provider;
  }
}
