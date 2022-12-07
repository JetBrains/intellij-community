// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.jarFinder;

import com.intellij.jarFinder.FindJarFix;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiQualifiedReferenceElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Sergey Evdokimov
 */
class GroovyFindJarFix extends FindJarFix {
  GroovyFindJarFix(@NotNull GrReferenceElement<?> ref) {
    super(ref);
  }

  @Override
  protected Collection<String> getFqns(@NotNull PsiQualifiedReferenceElement ref) {
    GrImportStatement importStatement = PsiTreeUtil.getParentOfType(ref.getElement(), GrImportStatement.class);

    //from static imports
    if (importStatement != null) {
      String fqn = importStatement.getImportFqn();
      return fqn == null ? Collections.emptyList() : Collections.singleton(fqn);
    }

    if (ref.getQualifier() != null) return Collections.emptyList();

    final String className = ref.getReferenceName();
    if (className == null) return Collections.emptyList();

    PsiFile file = ref.getContainingFile().getOriginalFile();
    if (!(file instanceof GroovyFile)) return Collections.emptyList();

    GrImportStatement[] importList = ((GroovyFile)file).getImportStatements();

    for (GrImportStatement imp : importList) {
      if (className.equals(imp.getImportedName())) {
        String fqn = imp.getImportFqn();
        return fqn == null ? Collections.emptyList() : Collections.singleton(fqn);
      }
    }

    return Collections.emptyList();
  }
}
