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
package org.jetbrains.plugins.gradle.execution.test.runner;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testIntegration.TestLocationProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 2/24/14
 */
public class GradleUrlProvider implements TestLocationProvider {
  public static final String PROTOCOL_ID = "gradle";
  public static final String METHOD_PREF = "methodName";
  public static final String CLASS_PREF = "className";

  @NotNull
  public List<Location> getLocation(@NotNull String protocolId, @NotNull String locationData, Project project) {
    if (!PROTOCOL_ID.equals(protocolId)) return Collections.emptyList();
    if (DumbService.isDumb(project)) return Collections.emptyList();

    final String className = extractFullClassName(locationData);
    if (className == null) return Collections.emptyList();
    final PsiClass testClass = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project));
    if (testClass == null) return Collections.emptyList();

    final String methodName = extractMethodName(locationData);
    if (methodName == null) {
      return Collections.<Location>singletonList(new PsiLocation<PsiClass>(project, testClass));
    }

    final PsiMethod[] methods = testClass.findMethodsByName(methodName, true);

    List<Location> list = new ArrayList<Location>(methods.length);
    for (PsiMethod method : methods) {
      list.add(new PsiLocation<PsiMethod>(project, method));
    }
    return list;
  }

  @Nullable
  private static String extractFullClassName(String locationData) {
    final int i = locationData.indexOf("::");
    final String pref = locationData.substring(0, i);
    final String qualifiedName = locationData.substring(i + 2);
    if (METHOD_PREF.equals(pref)) {
      final int dot = qualifiedName.lastIndexOf('.');
      return qualifiedName.substring(0, dot);
    }
    else if (CLASS_PREF.equals(pref)) {
      return qualifiedName;
    }
    return null;
  }

  @Nullable
  private static String extractMethodName(String locationData) {
    final int i = locationData.indexOf("::");
    final String pref = locationData.substring(0, i);
    final String qualifiedName = locationData.substring(i + 2);
    if (METHOD_PREF.equals(pref)) {
      final int dot = qualifiedName.lastIndexOf('.');
      return qualifiedName.substring(dot+1);
    }
    return null;
  }

}
