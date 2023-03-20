// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.PsiSearchHelper;
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
    SearchScope accessScope = getExtendedUseScope(methods[0]);
    for (int i = 1; i < methods.length; i++) {
      PsiMethod method1 = methods[i];
      accessScope = accessScope.union(getExtendedUseScope(method1));
    }
    return accessScope;
  }

  private static SearchScope getExtendedUseScope(PsiMethod method) {
    return PsiSearchHelper.getInstance(method.getProject()).getUseScope(method);
  }
}
