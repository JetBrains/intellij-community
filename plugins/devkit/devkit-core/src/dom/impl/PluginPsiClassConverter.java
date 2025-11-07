// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.IntelliJProjectUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;

public class PluginPsiClassConverter extends PsiClassConverter {

  @Override
  public PsiClass fromString(final String s, final @NotNull ConvertContext context) {
    if (s == null) return null;
    final String fqn = s.trim();
    if (fqn.isEmpty()) return null;
    final DomElement element = context.getInvocationElement();
    final XmlFile file = context.getFile();
    final Module module = context.getModule();
    if (!(element instanceof GenericDomValue)) {
      return DomJavaUtil.findClass(fqn, file, module, null);
    }
    final Project project = context.getProject();
    if (IntelliJProjectUtil.isIntelliJPlatformProject(project)) {
        // (IJPL-212813) try to find in project sources:
        PsiClass cls = DomJavaUtil.findClass(fqn, file, module, GlobalSearchScope.projectScope(project));
        if (cls != null) {
          return cls;
        }
        // fallback to all, if not found:
        return DomJavaUtil.findClass(fqn, file, module, GlobalSearchScope.allScope(project));
    }
    return DomJavaUtil.findClass(fqn, file, module, super.getScope(context));
  }

  @Override
  protected GlobalSearchScope getScope(@NotNull ConvertContext context) {
    final Project project = context.getProject();

    if (IntelliJProjectUtil.isIntelliJPlatformProject(project)) {
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
