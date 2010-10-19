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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.util.Query;
import com.intellij.util.xml.*;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public class PackageClassConverter extends ResolvingConverter<PsiClass> {
  private final String myExtendClassName;

  public PackageClassConverter(String extendClassName) {
    myExtendClassName = extendClassName;
  }

  public PackageClassConverter() {
    myExtendClassName = null;
  }

  @NotNull
  public Collection<? extends PsiClass> getVariants(ConvertContext context) {
    final List<PsiClass> result = new ArrayList<PsiClass>();
    DomElement domElement = context.getInvocationElement();
    Manifest manifest = domElement.getParentOfType(Manifest.class, true);
    final String packageName = manifest == null ? null : manifest.getPackage().getValue();
    ExtendClass extendClass = domElement.getAnnotation(ExtendClass.class);
    String className = extendClass != null ? extendClass.value() : myExtendClassName;
    if (className != null && packageName != null) {
      boolean inModuleOnly = domElement.getAnnotation(CompleteNonModuleClass.class) == null;
      result.addAll(findInheritors(context.getModule(), className, inModuleOnly));
    }
    return result;
  }

  public PsiClass fromString(@Nullable @NonNls String s, ConvertContext context) {
    if (s == null) return null;
    DomElement domElement = context.getInvocationElement();
    Manifest manifest = domElement.getParentOfType(Manifest.class, true);
    if (manifest != null) {
      s = s.replace('$', '.');
      String packageName = manifest.getPackage().getValue();
      String className;
      if (s.startsWith(".")) {
        className = packageName + s;
      }
      else {
        className = packageName + "." + s;
      }
      JavaPsiFacade facade = JavaPsiFacade.getInstance(context.getPsiManager().getProject());
      GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(context.getModule());
      PsiClass psiClass = facade.findClass(className, scope);
      if (psiClass == null) {
        psiClass = facade.findClass(s, scope);
      }
      return psiClass;
    }
    return null;
  }

  @Nullable
  private static String getQualifiedName(@NotNull PsiClass aClass) {
    PsiElement parent = aClass.getParent();
    if (parent instanceof PsiClass) {
      String parentQName = getQualifiedName((PsiClass)parent);
      if (parentQName == null) return null;
      return parentQName + "$" + aClass.getName();
    }
    return aClass.getQualifiedName();
  }

  @Nullable
  private static String getName(@NotNull PsiClass aClass) {
    PsiElement parent = aClass.getParent();
    if (parent instanceof PsiClass) {
      String parentName = getName((PsiClass)parent);
      if (parentName == null) return null;
      return parentName + "$" + aClass.getName();
    }
    return aClass.getName();
  }

  @Nullable
  public String toString(@Nullable PsiClass psiClass, ConvertContext context) {
    if (psiClass == null) return null;
    String qName = getQualifiedName(psiClass);
    if (qName == null) return null;
    DomElement domElement = context.getInvocationElement();
    Manifest manifest = domElement.getParentOfType(Manifest.class, true);
    final String packageName = manifest == null ? null : manifest.getPackage().getValue();
    PsiFile file = psiClass.getContainingFile();
    if (file instanceof PsiClassOwner) {
      PsiClassOwner psiFile = (PsiClassOwner)file;
      if (Comparing.equal(psiFile.getPackageName(), packageName)) {
        String name = getName(psiClass);
        if (name != null) {
          return '.' + name;
        }
      }
      else if (packageName != null && qName.startsWith(packageName)) {
        return qName.substring(packageName.length());
      }
    }
    return qName;
  }

  public void bindReference(GenericDomValue<PsiClass> genericValue, ConvertContext context, PsiElement newTarget) {
    if (newTarget instanceof PsiClass) {
      genericValue.setStringValue(toString((PsiClass)newTarget, context));
    }
    else {
      super.bindReference(genericValue, context, newTarget);
    }
  }

  @NotNull
  public static Collection<PsiClass> findInheritors(@NotNull final Module module, @NotNull final String className, boolean inModuleOnly) {
    final Project project = module.getProject();
    PsiClass base = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project));
    if (base != null) {
      GlobalSearchScope scope = inModuleOnly ? GlobalSearchScope.moduleWithDependenciesScope(module) : GlobalSearchScope.allScope(project);
      Query<PsiClass> query = ClassInheritorsSearch.search(base, scope, true);
      return query.findAll();
    }
    return new ArrayList<PsiClass>();
  }
}
