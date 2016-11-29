/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.resolve.GrImportContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ImportType;

import java.util.LinkedHashSet;

/**
 * @author Max Medvedev
 */
public class GroovyImportHelper {

  public enum ImportKind {
    SIMPLE,
    ON_DEMAND,
    ALIAS;
  }

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

    for (GrImportContributor contributor : GrImportContributor.EP_NAME.getExtensions()) {
      result.addAll(ContainerUtil.mapNotNull(
        contributor.getImports(file),
        i -> i.getType() == ImportType.STAR ? i.getName() : null
      ));
    }

    return result;
  }

  public static boolean processImports(@NotNull ResolveState state,
                                       @Nullable PsiElement lastParent,
                                       @NotNull PsiElement place,
                                       @NotNull PsiScopeProcessor importProcessor,
                                       @NotNull GrImportStatement[] importStatements,
                                       @NotNull ImportKind kind,
                                       @Nullable Boolean processStatic) {
    for (int i = importStatements.length - 1; i >= 0; i--) {
      final GrImportStatement imp = importStatements[i];
      if (getImportKind(imp) != kind) continue;
      if (processStatic != null && processStatic != imp.isStatic()) continue;
      if (!imp.processDeclarations(importProcessor, state, lastParent, place)) return false;
    }
    return true;
  }

  @Nullable
  private static ImportKind getImportKind(GrImportStatement statement) {
    if (statement.isOnDemand() && !statement.isAliasedImport()) return ImportKind.ON_DEMAND;
    if (!statement.isOnDemand() && statement.isAliasedImport()) return ImportKind.ALIAS;
    if (!statement.isAliasedImport() && !statement.isOnDemand()) return ImportKind.SIMPLE;
    return null;
  }
}
