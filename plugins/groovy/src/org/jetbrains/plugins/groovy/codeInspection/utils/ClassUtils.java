/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.codeInspection.utils;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public class ClassUtils {

  public static boolean isSubclass(@Nullable PsiType type,
                                   @NonNls String ancestorName) {
    if (type == null) {
      return false;
    }
    if (!(type instanceof PsiClassType)) {
      return false;
    }
    PsiClassType classType = (PsiClassType) type;
    final PsiClass aClass = classType.resolve();
    return isSubclass(aClass, ancestorName);
  }

  public static boolean isSubclass(@Nullable PsiClass aClass,
                                   @NonNls String ancestorName) {
    if (aClass == null) {
      return false;
    }
    final Project project = aClass.getProject();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    final PsiManager manager = aClass.getManager();
    final PsiClass ancestorClass =
        manager.findClass(ancestorName, scope);
    return InheritanceUtil.isCorrectDescendant(aClass, ancestorClass, true);
  }
}
