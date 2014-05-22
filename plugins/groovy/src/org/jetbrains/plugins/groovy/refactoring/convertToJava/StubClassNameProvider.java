/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class StubClassNameProvider implements ClassNameProvider {
  private final Set<VirtualFile> myAllToCompile;

  public StubClassNameProvider(Set<VirtualFile> allToCompile) {
    myAllToCompile = allToCompile;
  }

  @Override
  public String getQualifiedClassName(@Nullable PsiClass psiClass, @Nullable PsiElement context) {
    if (context != null && psiClass != null) {
      psiClass = GenerationUtil.findAccessibleSuperClass(context, psiClass);
    }
    if (psiClass == null) {
      return CommonClassNames.JAVA_LANG_OBJECT;
    }

    if (psiClass instanceof GrTypeDefinition) {
      if (!myAllToCompile.contains(psiClass.getContainingFile().getVirtualFile())) {
        final PsiClass container = psiClass.getContainingClass();
        if (container != null) {
          return getQualifiedClassName(container, null) + "$" + psiClass.getName();
        }
      }
    }
    final String name = psiClass.getQualifiedName();
    if (name!=null) return name;
    return psiClass.getName();
  }

  @Override
  public boolean forStubs() {
    return true;
  }
}
