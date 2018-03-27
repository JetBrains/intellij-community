// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportHolder;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrDeclarationHolder;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;

/**
 * @author ilyas
 */
public interface GroovyFileBase extends PsiFile, GrVariableDeclarationOwner, GrStatementOwner, PsiClassOwner, GrControlFlowOwner, PsiImportHolder,
                                        GrDeclarationHolder {
  String[] IMPLICITLY_IMPORTED_PACKAGES = {
      "java.lang",
      "java.util",
      "java.io",
      "java.net",
      "groovy.lang",
      "groovy.util",
  };
  String[] IMPLICITLY_IMPORTED_CLASSES = {
      "java.math.BigInteger",
      "java.math.BigDecimal",
  };

  @Override
  @NotNull
  String getPackageName();

  @NotNull
  GrTypeDefinition[] getTypeDefinitions();

  @NotNull
  GrMethod[] getMethods();

  @NotNull
  GrTopStatement[] getTopStatements();

  @Nullable
  GrImportStatement addImportForClass(@NotNull PsiClass aClass) throws IncorrectOperationException;

  void removeImport(@NotNull GrImportStatement importStatement) throws IncorrectOperationException;

  @NotNull
  GrImportStatement addImport(@NotNull GrImportStatement statement) throws IncorrectOperationException;

  boolean isScript();

  @Nullable
  PsiClass getScriptClass();
}
