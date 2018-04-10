// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.resolve.imports.GroovyFileImports;
import org.jetbrains.plugins.groovy.lang.resolve.imports.GroovyImports;
import org.jetbrains.plugins.groovy.lang.resolve.imports.StarImport;

import java.util.LinkedHashSet;

/**
 * @author Max Medvedev
 */
public class GroovyImportHelper {

  public static boolean isImplicitlyImported(PsiElement element, String expectedName, GroovyFile file) {
    if (!(element instanceof PsiClass)) return false;

    final PsiClass psiClass = (PsiClass)element;
    if (!expectedName.equals(psiClass.getName())) return false;

    final String qname = psiClass.getQualifiedName();
    if (qname == null) return false;

    for (String importedClass : GroovyFileBase.IMPLICITLY_IMPORTED_CLASSES) {
      if (qname.equals(importedClass)) {
        return true;
      }
    }
    for (String pkg : getImplicitlyImportedPackages(file)) {
      if (qname.equals(pkg + "." + expectedName) || pkg.isEmpty() && qname.equals(expectedName)) {
        return true;
      }
    }
    return false;
  }

  public static LinkedHashSet<String> getImplicitlyImportedPackages(@NotNull GroovyFile file) {
    final LinkedHashSet<String> result = new LinkedHashSet<>();
    ContainerUtil.addAll(result, GroovyFileBase.IMPLICITLY_IMPORTED_PACKAGES);

    final GroovyFileImports fileImports = GroovyImports.getImports(file);
    for (StarImport starImport : fileImports.getStarImports()) {
      if (!fileImports.isImplicit(starImport)) continue;
      result.add(starImport.getPackageFqn());
    }

    return result;
  }
}
