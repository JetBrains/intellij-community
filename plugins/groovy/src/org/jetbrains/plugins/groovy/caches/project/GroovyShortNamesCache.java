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

package org.jetbrains.plugins.groovy.caches.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.caches.module.GroovyModuleCache;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author ilyas
 */
class GroovyShortNamesCache implements PsiShortNamesCache {

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
    ArrayList<String> acc = new ArrayList<String>();
    GroovyCachesManager manager = GroovyCachesManager.getInstance(myProject);
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      GroovyModuleCache caches = manager.getModuleFilesCache(module);
      acc.addAll(caches.getAllFileNames());
    }
    return acc.toArray(new String[0]);
  }

  @NotNull
  public PsiClass[] getClassesByName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope) {
    ArrayList<PsiClass> acc = new ArrayList<PsiClass>();
    GroovyCachesManager manager = GroovyCachesManager.getInstance(myProject);
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      GroovyModuleCache caches = manager.getModuleFilesCache(module);
      acc.addAll(Arrays.asList(caches.getClassesByShortClassName(name)));
    }
    return acc.toArray(PsiClass.EMPTY_ARRAY);
  }

  @NotNull
  public String[] getAllClassNames(boolean searchInLibraries) {
    ArrayList<String> acc = new ArrayList<String>();
    GroovyCachesManager manager = GroovyCachesManager.getInstance(myProject);
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      GroovyModuleCache caches = manager.getModuleFilesCache(module);
      acc.addAll(caches.getAllClassShortNames());
    }
    return acc.toArray(new String[0]);
  }


  public void getAllClassNames(boolean searchInLibraries, @NotNull HashSet<String> dest) {
    ArrayList<String> acc = new ArrayList<String>();
    GroovyCachesManager manager = GroovyCachesManager.getInstance(myProject);
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      GroovyModuleCache caches = manager.getModuleFilesCache(module);
      acc.addAll(caches.getAllClassShortNames());
    }
    dest.addAll(acc);
  }


  @NotNull
  public PsiMethod[] getMethodsByName(@NonNls @NotNull String name, @NotNull GlobalSearchScope scope) {
    return new PsiMethod[0];
  }

  @NotNull
  public PsiMethod[] getMethodsByNameIfNotMoreThan(@NonNls @NotNull String name, @NotNull GlobalSearchScope scope, int maxCount) {
    return new PsiMethod[0];
  }

  @NotNull
  public String[] getAllMethodNames(boolean searchInLibraries) {
    return new String[0];
  }

  public void getAllMethodNames(boolean searchInLibraries, @NotNull HashSet<String> set) {

  }

  @NotNull
  public PsiField[] getFieldsByName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope) {
    return new PsiField[0];
  }

  @NotNull
  public String[] getAllFieldNames(boolean searchInLibraries) {
    return new String[0];
  }

  public void getAllFieldNames(boolean searchInLibraries, @NotNull HashSet<String> set) {

  }
}