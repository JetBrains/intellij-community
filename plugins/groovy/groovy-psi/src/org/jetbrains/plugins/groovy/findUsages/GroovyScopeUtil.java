// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.plugins.groovy.GroovyFileType;

/**
 * @author Max Medvedev
 */
public final class GroovyScopeUtil {
  public static SearchScope restrictScopeToGroovyFiles(SearchScope originalScope) {
    FileType[] groovyEnabledFileTypes = GroovyFileType.getGroovyEnabledFileTypes();
    return PsiSearchScopeUtil.restrictScopeTo(originalScope, groovyEnabledFileTypes);
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
