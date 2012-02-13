/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.android.dom.converters;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.HashSet;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.SimpleClassMapConstructor;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: 23.06.2009
 * Time: 18:48:18
 * To change this template use File | Settings | File Templates.
 */
public class ViewClassConverter extends ResolvingConverter<PsiClass> {
  @Nullable
  private static Map<String, PsiClass> getViewClassMap(ConvertContext context) {
    AndroidFacet facet = AndroidFacet.getInstance(context);
    if (facet != null) {
      return facet.getClassMap(AndroidUtils.VIEW_CLASS_NAME, SimpleClassMapConstructor.getInstance());
    }
    return null;
  }

  @NotNull
  public Collection<? extends PsiClass> getVariants(ConvertContext context) {
    Map<String, PsiClass> viewClassMap = getViewClassMap(context);
    if (viewClassMap != null) {
      return new HashSet<PsiClass>(viewClassMap.values());
    }
    return Collections.emptyList();
  }

  public PsiClass fromString(@Nullable @NonNls String s, ConvertContext context) {
    if (s == null) return null;
    final AndroidFacet facet = AndroidFacet.getInstance(context);
    if (facet != null) {
      s = s.replace('$', '.');

      final String className = s;
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(facet.getModule().getProject());

      return ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
        @Nullable
        public PsiClass compute() {
          return facade.findClass(className, facet.getModule().getModuleWithDependenciesAndLibrariesScope(true));
        }
      });
    }
    return null;
  }

  @Nullable
  private static String getClassName(@NotNull PsiClass c) {
    PsiElement parent = c.getParent();
    if (parent instanceof PsiClassOwner) {
      PsiClassOwner owner = (PsiClassOwner)parent;
      String packageName = owner.getPackageName();
      return packageName + '.' + c.getName();
    }
    else if (parent instanceof PsiClass) {
      return getClassName((PsiClass)parent) + '$' + c.getName();
    }
    return null;
  }

  public String toString(@Nullable PsiClass psiClass, ConvertContext context) {
    return psiClass != null ? getClassName(psiClass) : null;
  }
}
