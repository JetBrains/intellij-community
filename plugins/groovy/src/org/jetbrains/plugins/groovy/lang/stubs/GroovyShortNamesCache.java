/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.*;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author ilyas
 */
public class GroovyShortNamesCache implements PsiShortNamesCache {

  Project myProject;

  public GroovyShortNamesCache(Project project) {
    myProject = project;
  }

  public void runStartupActivity() {
  }

  @NotNull
  public PsiFile[] getFilesByName(@NotNull String name) {
    return PsiFile.EMPTY_ARRAY;
  }

  @NotNull
  public String[] getAllFileNames() {
    return FilenameIndex.getAllFilenames();
  }

  @NotNull
  public PsiClass[] getClassesByName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope) {
    final Collection<? extends PsiClass> plainClasses = StubIndex.getInstance().get(GrShortClassNameIndex.KEY, name, myProject, scope);
    Collection<PsiClass> allClasses = getAllScriptClasses(name, scope);
    allClasses.addAll(plainClasses);
    if (allClasses.isEmpty()) return PsiClass.EMPTY_ARRAY;
    return allClasses.toArray(new PsiClass[plainClasses.size()]);
  }

  @Nullable
  public PsiClass getClassByFQName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope) {
    //todo implement 4 script classes
    final Collection<? extends PsiClass> classes = StubIndex.getInstance().get(GrFullClassNameIndex.KEY, name.hashCode(), myProject, scope);
    for (PsiClass clazz : classes) {
      if (name.equals(clazz.getQualifiedName())) return clazz;
    }
    return null;
  }

  @NotNull
  public PsiClass[] getClassesByFQName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope) {
    //todo implement 4 script classes
    final Collection<PsiClass> classes = StubIndex.getInstance().get(GrFullClassNameIndex.KEY, name.hashCode(), myProject, scope);
    ArrayList<PsiClass> list = new ArrayList<PsiClass>();
    for (PsiClass psiClass : classes) {
      if (name.equals(psiClass.getQualifiedName())) {
        list.add(psiClass);
      }
    }
    return list.toArray(new PsiClass[classes.size()]);
  }

  private Collection<PsiClass> getAllScriptClasses(String name, GlobalSearchScope scope) {
    Collection<GroovyFile> files = StubIndex.getInstance().get(GrScriptClassNameIndex.KEY, name, myProject, scope);
    files = ContainerUtil.findAll(files, new Condition<GroovyFile>() {
      public boolean value(GroovyFile groovyFile) {
        return groovyFile.isScript();
      }
    });
    return ContainerUtil.map(files, new Function<GroovyFile, PsiClass>() {
      public PsiClass fun(GroovyFile groovyFile) {
        assert groovyFile.isScript();
        return groovyFile.getScriptClass();
      }
    });
  }

  @NotNull
  public String[] getAllClassNames() {
    final Collection<String> classNames = StubIndex.getInstance().getAllKeys(GrShortClassNameIndex.KEY);
    Collection<String> scriptNames = StubIndex.getInstance().getAllKeys(GrScriptClassNameIndex.KEY);
    classNames.addAll(scriptNames);
    return classNames.toArray(new String[classNames.size()]);
  }


  public void getAllClassNames(@NotNull HashSet<String> dest) {
    final Collection<String> classNames = StubIndex.getInstance().getAllKeys(GrShortClassNameIndex.KEY);
    Collection<String> scriptNames = StubIndex.getInstance().getAllKeys(GrScriptClassNameIndex.KEY);
    classNames.addAll(scriptNames);
    dest.addAll(classNames);
  }

  @NotNull
  public PsiMethod[] getMethodsByName(@NonNls @NotNull String name, @NotNull GlobalSearchScope scope) {
    final Collection<? extends PsiMethod> methods = StubIndex.getInstance().get(GrMethodNameIndex.KEY, name, myProject, scope);
    final Collection<? extends PsiMethod> annMethods = StubIndex.getInstance().get(GrAnnotationMethodNameIndex.KEY, name, myProject, scope);
    if (methods.isEmpty() && annMethods.isEmpty()) return PsiMethod.EMPTY_ARRAY;
    return ArrayUtil
        .mergeArrays(annMethods.toArray(new PsiMethod[annMethods.size()]), methods.toArray(new PsiMethod[methods.size()]), PsiMethod.class);
  }

  @NotNull
  public PsiMethod[] getMethodsByNameIfNotMoreThan(@NonNls @NotNull String name, @NotNull GlobalSearchScope scope, int maxCount) {
    return getMethodsByName(name, scope);
  }

  @NotNull
  public String[] getAllMethodNames() {
    Collection<String> keys = StubIndex.getInstance().getAllKeys(GrMethodNameIndex.KEY);
    keys.addAll(StubIndex.getInstance().getAllKeys(GrAnnotationMethodNameIndex.KEY));
    return keys.toArray(new String[keys.size()]);
  }

  public void getAllMethodNames(@NotNull HashSet<String> set) {
    set.addAll(StubIndex.getInstance().getAllKeys(GrMethodNameIndex.KEY));
  }

  @NotNull
  public PsiField[] getFieldsByName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope) {
    final Collection<? extends PsiField> fields = StubIndex.getInstance().get(GrFieldNameIndex.KEY, name, myProject, scope);
    if (fields.isEmpty()) return PsiField.EMPTY_ARRAY;
    return fields.toArray(new PsiField[fields.size()]);
  }

  @NotNull
  public String[] getAllFieldNames() {
    Collection<String> fields = StubIndex.getInstance().getAllKeys(GrFieldNameIndex.KEY);
    return fields.toArray(new String[fields.size()]);
  }

  public void getAllFieldNames(@NotNull HashSet<String> set) {
    set.addAll(StubIndex.getInstance().getAllKeys(GrFieldNameIndex.KEY));
  }

}