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

package com.intellij.execution.junit2.info;

import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;

public class PsiClassLocator implements PsiLocator {
  private final String myName;
  private final String myPackage;

  private PsiClassLocator(final String name, final String aPackage) {
    myName = name;
    myPackage = aPackage;
  }

  public static PsiClassLocator fromQualifiedName(final String name) {
    final int lastDot = name.lastIndexOf('.');
    if (lastDot == -1 || lastDot == name.length())
      return new PsiClassLocator(name, "");
    else
      return new PsiClassLocator(name.substring(lastDot + 1), name.substring(0, lastDot));
  }

  public Location<PsiClass> getLocation(final Project project) {
    final PsiClass psiClass = JavaExecutionUtil.findMainClass(project, getQualifiedName(), GlobalSearchScope.allScope(project));
    return psiClass != null ? new PsiLocation<PsiClass>(project, psiClass) : null;
  }

  public String getPackage() {
    return myPackage;
  }

  public String getName() {
    return myName;
  }

  public String getQualifiedName() {
    return StringUtil.getQualifiedName(myPackage, myName);
  }
}
