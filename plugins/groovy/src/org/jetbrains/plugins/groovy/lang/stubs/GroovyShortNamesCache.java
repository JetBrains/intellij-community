/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.file.impl.JavaFileManagerImpl;
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
import org.jetbrains.plugins.groovy.lang.psi.impl.search.GrSourceFilterScope;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.*;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author ilyas
 */
public class GroovyShortNamesCache extends PsiShortNamesCache {
  private final Project myProject;

  public GroovyShortNamesCache(Project project) {
    myProject = project;
  }

  @NotNull
  public PsiClass[] getClassesByName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope) {
    final Collection<? extends PsiElement> plainClasses = StubIndex.getInstance().get(GrShortClassNameIndex.KEY, name, myProject, new GrSourceFilterScope(scope));
    Collection<PsiClass> allClasses = getAllScriptClasses(name, scope);
    if (allClasses.isEmpty() && plainClasses.isEmpty()) return PsiClass.EMPTY_ARRAY;

    for (PsiElement aClass : plainClasses) {
      if (JavaFileManagerImpl.notClass(aClass)) continue;
      allClasses.add((PsiClass)aClass);
    }
    return allClasses.toArray(new PsiClass[allClasses.size()]);
  }

  @Nullable
  public PsiClass getClassByFQName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope) {
    final Collection<PsiElement> classes = new ArrayList<PsiElement>(StubIndex.getInstance().get(GrFullClassNameIndex.KEY, name.hashCode(), myProject, new GrSourceFilterScope(scope)));
    final Collection<PsiClass> scriptClasses = getScriptClassesByFQName(name, scope);
    classes.addAll(scriptClasses);
    for (PsiElement clazz : classes) {
      if (JavaFileManagerImpl.notClass(clazz)) continue;

      if (name.equals(((PsiClass)clazz).getQualifiedName())) {
        return (PsiClass)clazz;
      }
    }
    return null;
  }

  public Collection<PsiClass> getScriptClassesByFQName(final String name, final GlobalSearchScope scope) {
    Collection<GroovyFile> scripts = StubIndex.getInstance().get(GrFullScriptNameIndex.KEY, name.hashCode(), myProject, new GrSourceFilterScope(scope));
    scripts = ContainerUtil.findAll(scripts, new Condition<GroovyFile>() {
      public boolean value(final GroovyFile groovyFile) {
        final PsiClass clazz = groovyFile.getScriptClass();
        return groovyFile.isScript() && clazz != null && name.equals(clazz.getQualifiedName());
      }
    });
    return ContainerUtil.map(scripts, new Function<GroovyFile, PsiClass>() {
      public PsiClass fun(final GroovyFile groovyFile) {
        return groovyFile.getScriptClass();
      }
    });
  }

  @NotNull
  public PsiClass[] getClassesByFQName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope) {
    final Collection<PsiClass> result = getScriptClassesByFQName(name, scope);

    final Collection<? extends PsiElement> classes = StubIndex.getInstance().get(GrFullClassNameIndex.KEY, name.hashCode(), myProject, new GrSourceFilterScope(scope));
    if (!classes.isEmpty()) {
      //hashcode doesn't guarantee equals
      for (PsiElement psiClass : classes) {
        if (!JavaFileManagerImpl.notClass(psiClass) && name.equals(((PsiClass)psiClass).getQualifiedName())) {
          result.add((PsiClass)psiClass);
        }
      }
    }

    return result.isEmpty() ? PsiClass.EMPTY_ARRAY : result.toArray(new PsiClass[result.size()]);
  }

  private Collection<PsiClass> getAllScriptClasses(String name, GlobalSearchScope scope) {
    Collection<GroovyFile> files = StubIndex.getInstance().get(GrScriptClassNameIndex.KEY, name, myProject, new GrSourceFilterScope(scope));
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
    final Collection<String> classNames = StubIndex.getInstance().getAllKeys(GrShortClassNameIndex.KEY, myProject);
    Collection<String> scriptNames = StubIndex.getInstance().getAllKeys(GrScriptClassNameIndex.KEY, myProject);
    classNames.addAll(scriptNames);
    return ArrayUtil.toStringArray(classNames);
  }


  public void getAllClassNames(@NotNull HashSet<String> dest) {
    final Collection<String> classNames = StubIndex.getInstance().getAllKeys(GrShortClassNameIndex.KEY, myProject);
    Collection<String> scriptNames = StubIndex.getInstance().getAllKeys(GrScriptClassNameIndex.KEY, myProject);
    classNames.addAll(scriptNames);
    dest.addAll(classNames);
  }

  @NotNull
  public PsiMethod[] getMethodsByName(@NonNls @NotNull String name, @NotNull GlobalSearchScope scope) {
    final Collection<? extends PsiMethod> methods = StubIndex.getInstance().get(GrMethodNameIndex.KEY, name, myProject, new GrSourceFilterScope(scope));
    final Collection<? extends PsiMethod> annMethods = StubIndex.getInstance().get(GrAnnotationMethodNameIndex.KEY, name, myProject, new GrSourceFilterScope(scope));
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
    Collection<String> keys = StubIndex.getInstance().getAllKeys(GrMethodNameIndex.KEY, myProject);
    keys.addAll(StubIndex.getInstance().getAllKeys(GrAnnotationMethodNameIndex.KEY, myProject));
    return ArrayUtil.toStringArray(keys);
  }

  public void getAllMethodNames(@NotNull HashSet<String> set) {
    set.addAll(StubIndex.getInstance().getAllKeys(GrMethodNameIndex.KEY, myProject));
  }

  @NotNull
  public PsiField[] getFieldsByName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope) {
    final Collection<? extends PsiField> fields = StubIndex.getInstance().get(GrFieldNameIndex.KEY, name, myProject, new GrSourceFilterScope(scope));
    if (fields.isEmpty()) return PsiField.EMPTY_ARRAY;
    return fields.toArray(new PsiField[fields.size()]);
  }

  @NotNull
  public String[] getAllFieldNames() {
    Collection<String> fields = StubIndex.getInstance().getAllKeys(GrFieldNameIndex.KEY, myProject);
    return ArrayUtil.toStringArray(fields);
  }

  public void getAllFieldNames(@NotNull HashSet<String> set) {
    set.addAll(StubIndex.getInstance().getAllKeys(GrFieldNameIndex.KEY, myProject));
  }

}
