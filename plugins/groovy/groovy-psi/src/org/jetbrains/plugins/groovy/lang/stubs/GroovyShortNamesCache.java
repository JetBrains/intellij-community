/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.stubs;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.indexing.IdFilter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAnnotationMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.search.GrSourceFilterScope;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author ilyas
 */
public class GroovyShortNamesCache extends PsiShortNamesCache {
  private final Project myProject;

  public GroovyShortNamesCache(Project project) {
    myProject = project;
  }

  public static GroovyShortNamesCache getGroovyShortNamesCache(Project project) {
    return ObjectUtils.assertNotNull(ContainerUtil.findInstance(project.getExtensions(PsiShortNamesCache.EP_NAME), GroovyShortNamesCache.class));
  }

  @Override
  @NotNull
  public PsiClass[] getClassesByName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope) {
    Collection<PsiClass> allClasses = new SmartList<>();
    processClassesWithName(name, Processors.cancelableCollectProcessor(allClasses), scope, null);
    if (allClasses.isEmpty()) return PsiClass.EMPTY_ARRAY;
    return allClasses.toArray(new PsiClass[allClasses.size()]);
  }

  public List<PsiClass> getScriptClassesByFQName(final String name, final GlobalSearchScope scope, final boolean srcOnly) {
    GlobalSearchScope actualScope = srcOnly ? new GrSourceFilterScope(scope) : scope;
    final Collection<GroovyFile> files = StubIndex.getElements(GrFullScriptNameIndex.KEY, name.hashCode(), myProject, actualScope,
                                                               GroovyFile.class);
    if (files.isEmpty()) {
      return Collections.emptyList();
    }

    final ArrayList<PsiClass> result = new ArrayList<>();
    for (GroovyFile file : files) {
      if (file.isScript()) {
        final PsiClass scriptClass = file.getScriptClass();
        if (scriptClass != null && name.equals(scriptClass.getQualifiedName())) {
          result.add(scriptClass);
        }
      }
    }
    return result;
  }

  @NotNull
  public List<PsiClass> getClassesByFQName(String name, GlobalSearchScope scope, boolean inSource) {
    final List<PsiClass> result = ContainerUtil.newArrayList();

    for (PsiElement psiClass : StubIndex.getElements(GrFullClassNameIndex.KEY, name.hashCode(), myProject,
                                                     inSource ? new GrSourceFilterScope(scope) : scope, PsiClass.class)) {
      //hashcode doesn't guarantee equals
      if (name.equals(((PsiClass)psiClass).getQualifiedName())) {
        result.add((PsiClass)psiClass);
      }
    }
    result.addAll(getScriptClassesByFQName(name, scope, inSource));
    return result;
  }

  @Override
  @NotNull
  public String[] getAllClassNames() {
    return ArrayUtil.toStringArray(StubIndex.getInstance().getAllKeys(GrScriptClassNameIndex.KEY, myProject));
  }


  @Override
  public void getAllClassNames(@NotNull HashSet<String> dest) {
    dest.addAll(StubIndex.getInstance().getAllKeys(GrScriptClassNameIndex.KEY, myProject));
  }

  @Override
  @NotNull
  public PsiMethod[] getMethodsByName(@NonNls @NotNull String name, @NotNull GlobalSearchScope scope) {
    final Collection<? extends PsiMethod> methods = StubIndex.getElements(GrMethodNameIndex.KEY, name, myProject,
                                                                          new GrSourceFilterScope(scope), GrMethod.class);
    final Collection<? extends PsiMethod> annMethods = StubIndex.getElements(GrAnnotationMethodNameIndex.KEY, name, myProject,
                                                                             new GrSourceFilterScope(scope),
                                                                             GrAnnotationMethod.class);
    if (methods.isEmpty() && annMethods.isEmpty()) return PsiMethod.EMPTY_ARRAY;
    return ArrayUtil.mergeCollections(annMethods, methods, PsiMethod.ARRAY_FACTORY);
  }

  @Override
  public boolean processMethodsWithName(@NonNls @NotNull String name,
                                        @NotNull GlobalSearchScope scope,
                                        @NotNull Processor<PsiMethod> processor) {
    return processMethodsWithName(name, processor, scope, null);
  }

  @Override
  public boolean processMethodsWithName(@NonNls @NotNull String name,
                                        @NotNull Processor<? super PsiMethod> processor,
                                        @NotNull GlobalSearchScope scope,
                                        @Nullable IdFilter filter) {
    GrSourceFilterScope filterScope = new GrSourceFilterScope(scope);
    return StubIndex.getInstance().processElements(GrMethodNameIndex.KEY, name, myProject, filterScope, filter, GrMethod.class, processor) &&
           StubIndex.getInstance().processElements(GrAnnotationMethodNameIndex.KEY, name, myProject, filterScope, filter,
                                                   GrAnnotationMethod.class, processor);
  }

  @Override
  @NotNull
  public PsiMethod[] getMethodsByNameIfNotMoreThan(@NonNls @NotNull String name, @NotNull GlobalSearchScope scope, int maxCount) {
    return getMethodsByName(name, scope);
  }

  @NotNull
  @Override
  public PsiField[] getFieldsByNameIfNotMoreThan(@NonNls @NotNull String name, @NotNull GlobalSearchScope scope, int maxCount) {
    return getFieldsByName(name, scope);
  }

  @Override
  @NotNull
  public String[] getAllMethodNames() {
    Collection<String> keys = StubIndex.getInstance().getAllKeys(GrMethodNameIndex.KEY, myProject);
    keys.addAll(StubIndex.getInstance().getAllKeys(GrAnnotationMethodNameIndex.KEY, myProject));
    return ArrayUtil.toStringArray(keys);
  }

  @Override
  public void getAllMethodNames(@NotNull HashSet<String> set) {
    set.addAll(StubIndex.getInstance().getAllKeys(GrMethodNameIndex.KEY, myProject));
  }

  @Override
  @NotNull
  public PsiField[] getFieldsByName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope) {
    final Collection<? extends PsiField> fields = StubIndex.getElements(GrFieldNameIndex.KEY, name, myProject,
                                                                        new GrSourceFilterScope(scope), GrField.class);
    if (fields.isEmpty()) return PsiField.EMPTY_ARRAY;
    return fields.toArray(new PsiField[fields.size()]);
  }

  @Override
  @NotNull
  public String[] getAllFieldNames() {
    Collection<String> fields = StubIndex.getInstance().getAllKeys(GrFieldNameIndex.KEY, myProject);
    return ArrayUtil.toStringArray(fields);
  }

  @Override
  public void getAllFieldNames(@NotNull HashSet<String> set) {
    set.addAll(StubIndex.getInstance().getAllKeys(GrFieldNameIndex.KEY, myProject));
  }

  @Override
  public boolean processFieldsWithName(@NotNull String name,
                                       @NotNull Processor<? super PsiField> processor,
                                       @NotNull GlobalSearchScope scope,
                                       @Nullable IdFilter filter) {
    return StubIndex.getInstance().processElements(GrFieldNameIndex.KEY, name, myProject, new GrSourceFilterScope(scope), filter,
                                                   GrField.class, processor);
  }

  @Override
  public boolean processClassesWithName(@NotNull String name,
                                        @NotNull Processor<? super PsiClass> processor,
                                        @NotNull GlobalSearchScope scope,
                                        @Nullable IdFilter filter) {
    for (GroovyFile file : StubIndex.getElements(GrScriptClassNameIndex.KEY, name, myProject, new GrSourceFilterScope(scope), filter,
                                                 GroovyFile.class)) {
      PsiClass aClass = file.getScriptClass();
      if (aClass != null && !processor.process(aClass)) return true;
    }
    return true;
  }
}
