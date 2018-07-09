// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;

/**
 * @author ven
 */
public interface GroovyFile extends GroovyFileBase {
  GroovyFile[] EMPTY_ARRAY = new GroovyFile[0];

  GrImportStatement[] getImportStatements();

  @Nullable
  GrPackageDefinition getPackageDefinition();

  @Override
  void setPackageName(String packageName);

  @Nullable
  GrPackageDefinition setPackage(GrPackageDefinition newPackage);

  @Nullable
  PsiType getInferredScriptReturnType();

  /**
   * Top level script variable declarations are declarations within script body.
   * <pre>
   *   def a // top level
   *   if (condition) {
   *     def b // top level
   *   }
   *   def foo() {
   *     def c // script declaration, but not in script body.
   *   }
   *   class SomeClass {
   *     def var() {
   *       def d // not script declaration, it appears within SomeClass
   *     }
   *   }
   * </pre>
   * @param topLevelOnly whether script body declarations are needed only
   * @return script variable declarations which have at least one annotation.
   * @see org.jetbrains.plugins.groovy.transformations.impl.BaseScriptTransformationSupport
   * @see org.jetbrains.plugins.groovy.transformations.impl.FieldScriptTransformationSupport
   */
  @NotNull
  GrVariableDeclaration[] getScriptDeclarations(boolean topLevelOnly);
}
