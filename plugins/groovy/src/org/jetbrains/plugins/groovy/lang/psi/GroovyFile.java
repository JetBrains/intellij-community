package org.jetbrains.plugins.groovy.lang.psi;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.annotations.NotNull;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;

/**
 * @author ven
 */
public interface GroovyFile extends PsiFile {
  GrTypeDefinition[] getTypeDefinitions();

  @NotNull
  String getPackageName();

  GrPackageDefinition getPackageDefinition();

  GrTopStatement[] getTopStatements();

  GrImportStatement[] getImportStatements();

  void addImportForClass(PsiClass aClass);
}
