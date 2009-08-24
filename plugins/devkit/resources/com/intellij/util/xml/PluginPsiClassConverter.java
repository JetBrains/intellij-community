/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml;

import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.psi.search.GlobalSearchScope;

/**
 * @author peter
*/
public class PluginPsiClassConverter extends PsiClassConverter {
  protected GlobalSearchScope getScope(final GenericDomValue domValue) {
    return GlobalSearchScope.allScope(domValue.getManager().getProject());
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
