/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.NameHint;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import javax.swing.*;

/**
 * Implements all abstractionos related to Groovy file
 *
 * @author ilyas
 */
public class GroovyFile extends PsiFileBase {

  public GroovyFile(FileViewProvider viewProvider) {
    super(viewProvider, GroovyFileType.GROOVY_FILE_TYPE.getLanguage());
  }

  @NotNull
  public FileType getFileType() {
    return GroovyFileType.GROOVY_FILE_TYPE;
  }

  public String toString() {
    return "Groovy script";
  }

  public GrTypeDefinition[] getTypeDefinitions() {
    return findChildrenByClass(GrTypeDefinition.class);
  }

  @NotNull
  public String getPackageName() {
    GrPackageDefinition packageDef = findChildByClass(GrPackageDefinition.class);
    if (packageDef != null) {
      return packageDef.getPackageName();
    }
    return "";
  }

  public GrPackageDefinition getPackageDefinition(){
    return findChildByClass(GrPackageDefinition.class);
  }

  public GrTopStatement[] getTopStatements() {
    return findChildrenByClass(GrTopStatement.class);
  }

  public GrImportStatement[] getImportStatements() {
    return findChildrenByClass(GrImportStatement.class);
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull PsiSubstitutor substitutor, PsiElement lastParent, @NotNull PsiElement place) {
    for (final GrTypeDefinition typeDefinition : getTypeDefinitions()) {
      if (!ResolveUtil.processElement(processor, typeDefinition)) return false;
    }
    
    for (final GrTopStatement topStatement : getTopStatements()) {
      if (!topStatement.processDeclarations(processor, substitutor, lastParent, place)) return false;
    }

    for (final String implicitlyImported : IMPLICITLY_IMPORTED_PACKAGES) {
      PsiPackage aPackage = getManager().findPackage(implicitlyImported);
      if (aPackage != null && !aPackage.processDeclarations(processor, substitutor, lastParent, place)) return false;
    }

    String currentPackageName = getPackageName();
    PsiPackage currentPackage = getManager().findPackage(currentPackageName);
    if (currentPackage != null && !currentPackage.processDeclarations(processor, substitutor, lastParent, place)) {
      return false;
    }

    return true;
  }

  private static final String[] IMPLICITLY_IMPORTED_PACKAGES = new String[] {
      "java.lang",
      "java.util",
      "java.io",
      "java.net",
      "groovy.lang",
      "groovy.util",
  };

  @Nullable
  public Icon getIcon(int flags) {
    return GroovyFileType.GROOVY_LOGO;
  }

  public void insertExternalImportForClass(PsiClass aClass)  {
    try {
      // Calculating position
      Project project = aClass.getProject();
      GroovyElementFactory factory = GroovyElementFactory.getInstance(project);
      GrImportStatement ourImportStatement = factory.createImportStatementFromText(aClass.getQualifiedName());
      PsiElement whiteSpace = factory.createWhiteSpace();
      GrImportStatement[] importStatements = getImportStatements();
      PsiElement psiElementAfter = null;
      if (importStatements.length > 0) {
        psiElementAfter = importStatements[importStatements.length - 1];
      } else if (getPackageDefinition() != null) {
        psiElementAfter = getPackageDefinition();
      }
      if (psiElementAfter != null &&
              psiElementAfter.getNode() != null) {
//        psiElementAfter.getNode().addChild(whiteSpace.getNode());
        addAfter(ourImportStatement, psiElementAfter);
      } else {
        addBefore(ourImportStatement, getFirstChild());
      }
    } catch (IncorrectOperationException e) {
      e.printStackTrace();
    }
  }



}

