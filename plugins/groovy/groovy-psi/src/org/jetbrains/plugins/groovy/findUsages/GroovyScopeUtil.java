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
package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.plugins.groovy.GroovyFileType;

/**
 * @author Max Medvedev
 */
public class GroovyScopeUtil {
  public static SearchScope restrictScopeToGroovyFiles(SearchScope originalScope) {
    if (originalScope instanceof GlobalSearchScope) {
      return GlobalSearchScope
        .getScopeRestrictedByFileTypes((GlobalSearchScope)originalScope, GroovyFileType.getGroovyEnabledFileTypes());
    }
    return originalScope;
  }

  public static SearchScope restrictScopeToGroovyFiles(SearchScope originalScope, SearchScope effectiveScope) {
    SearchScope restricted = restrictScopeToGroovyFiles(originalScope);
    return restricted.intersectWith(effectiveScope);
  }


  public static SearchScope getEffectiveScope(PsiMethod... methods) {
    SearchScope accessScope = methods[0].getUseScope();
    for (int i = 1; i < methods.length; i++) {
      PsiMethod method1 = methods[i];
      accessScope = accessScope.union(method1.getUseScope());
    }
    return accessScope;
  }
}
