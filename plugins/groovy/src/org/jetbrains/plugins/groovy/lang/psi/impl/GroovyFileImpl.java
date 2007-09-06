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

package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTopLevelDefintion;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import javax.swing.*;

/**
 * Implements all abstractions related to Groovy file
 *
 * @author ilyas
 */
public class GroovyFileImpl extends GroovyFileBaseImpl implements GroovyFile {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl");

  private PsiClass myScriptClass;
  private boolean myScriptClassInitialized = false;
  private static final String SYNTHETIC_PARAMETER_NAME = "args";
  private GrParameter mySyntheticArgsParameter = null;

  private GroovyPsiElement myContext;

  public GroovyFileImpl(FileViewProvider viewProvider) {
    super(viewProvider, GroovyFileType.GROOVY_FILE_TYPE.getLanguage());
  }

  @NotNull
  public String getPackageName() {
    GrPackageDefinition packageDef = findChildByClass(GrPackageDefinition.class);
    if (packageDef != null) {
      return packageDef.getPackageName();
    }
    return "";
  }

  public GrPackageDefinition getPackageDefinition() {
    return findChildByClass(GrPackageDefinition.class);
  }

  private GrParameter getSyntheticArgsParameter() {
    if (mySyntheticArgsParameter == null) {
      try {
        mySyntheticArgsParameter = GroovyElementFactory.getInstance(getProject()).createParameter(SYNTHETIC_PARAMETER_NAME, "java.lang.String[]", this);
      } catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
    return mySyntheticArgsParameter;
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull PsiSubstitutor substitutor, PsiElement lastParent, @NotNull PsiElement place) {
    for (final GrTopLevelDefintion definition : getTopLevelDefinitions()) {
      if (!ResolveUtil.processElement(processor, definition)) return false;
    }

    PsiManager manager = getManager();

    if (!(lastParent instanceof GrTypeDefinition)) {
      if (!ResolveUtil.processElement(processor, getSyntheticArgsParameter())) return false;

      GlobalSearchScope resolveScope = getResolveScope();
      PsiClass scriptClass = manager.findClass(SCRIPT_BASE_CLASS_NAME, resolveScope);
      if (scriptClass != null) {
        if (!scriptClass.processDeclarations(processor, substitutor, lastParent, place)) return false;
        PsiClassType scriptType = manager.getElementFactory().createTypeByFQClassName(SCRIPT_BASE_CLASS_NAME, resolveScope);
        if (!ResolveUtil.processDefaultMethods(scriptType, processor, manager.getProject())) return false;
      }
    }

    if (!processChildrenScopes(this, processor, substitutor, lastParent, place)) return false;

    for (final String implicitlyImported : IMPLICITLY_IMPORTED_PACKAGES) {
      PsiPackage aPackage = manager.findPackage(implicitlyImported);
      if (aPackage != null && !aPackage.processDeclarations(processor, substitutor, lastParent, place)) return false;
    }

    for (String implicitlyImportedClass : IMPLICITLY_IMPORTED_CLASSES) {
      PsiClass clazz = manager.findClass(implicitlyImportedClass, getResolveScope());
      if (clazz != null && !ResolveUtil.processElement(processor, clazz)) return false;
    }

    String currentPackageName = getPackageName();
    PsiPackage currentPackage = manager.findPackage(currentPackageName);
    if (currentPackage != null && !currentPackage.processDeclarations(processor, substitutor, lastParent, place))
      return false;

    if (currentPackageName.length() > 0) { //otherwise already processed default package
      PsiPackage defaultPackage = manager.findPackage("");
      if (defaultPackage != null) {
        for (PsiPackage subpackage : defaultPackage.getSubPackages(getResolveScope())) {
          if (!ResolveUtil.processElement(processor, subpackage)) return false;
        }
      }
    }

    return true;
  }

  private static boolean processChildrenScopes(PsiElement element, PsiScopeProcessor processor,
                                               PsiSubstitutor substitutor, PsiElement lastParent, PsiElement place) {
    PsiElement run = lastParent == null ? element.getLastChild() : lastParent.getPrevSibling();
    while (run != null) {
      if (!(run instanceof GrTopLevelDefintion) &&
          !run.processDeclarations(processor, substitutor, null, place)) return false;
      run = run.getPrevSibling();
    }

    return true;
  }

  public GrImportStatement[] getImportStatements() {
    return findChildrenByClass(GrImportStatement.class);
  }

  @Nullable
  public Icon getIcon(int flags) {
    return GroovyFileType.GROOVY_LOGO;
  }

  public GrImportStatement addImportForClass(PsiClass aClass) {
    try {
      // Calculating position
      Project project = aClass.getProject();
      GroovyElementFactory factory = GroovyElementFactory.getInstance(project);
      GrImportStatement importStatement = factory.createImportStatementFromText(aClass.getQualifiedName(), false, false);
      PsiElement anchor = getAnchorToInsertImportAfter();
      return (GrImportStatement) addAfter(importStatement, anchor);
    } catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  private PsiElement getAnchorToInsertImportAfter() {
    GrImportStatement[] importStatements = getImportStatements();
    PsiElement psiElementAfter = null;
    if (importStatements.length > 0) {
      psiElementAfter = importStatements[importStatements.length - 1];
    } else if (getPackageDefinition() != null) {
      psiElementAfter = getPackageDefinition();
    }
    return psiElementAfter;
  }

  public GrImportStatement addImport(GrImportStatement statement) throws IncorrectOperationException {
    PsiElement anchor = getAnchorToInsertImportAfter();
    final PsiElement result = addAfter(statement, anchor);

    boolean isAliasedImport = false;
    if (anchor instanceof GrImportStatement) {
      isAliasedImport = !((GrImportStatement) anchor).isAliasedImport() && statement.isAliasedImport() ||
          ((GrImportStatement) anchor).isAliasedImport() && !statement.isAliasedImport();
    }

    if (anchor != null &&
        (!(anchor instanceof GrImportStatement) || isAliasedImport)) {
      getNode().addLeaf(GroovyTokenTypes.mNLS, "\n", result.getNode());
    }

    GrImportStatement importStatement = (GrImportStatement) result;
    return importStatement;
  }

  public boolean isScript() {
    GrTopStatement[] top = findChildrenByClass(GrTopStatement.class);
    for (GrTopStatement st : top)
      if (!(st instanceof GrTypeDefinition || st instanceof GrImportStatement || st instanceof GrPackageDefinition))
        return true;

    return false;
  }

  public PsiClass getScriptClass() {
    if (!myScriptClassInitialized) {
      if (isScript()) {
        myScriptClass = new GroovyScriptClass(this);
      }

      myScriptClassInitialized = true;
    }
    return myScriptClass;
  }

  public void setPackageDefinition(String packageName) {
    final GrPackageDefinition currentPackage = getPackageDefinition();
    final GrTopStatement newPackage = GroovyElementFactory.getInstance(getProject()).createTopElementFromText("package " + packageName);
    final ASTNode fileNode = getNode();
    assert fileNode != null;
    final ASTNode newNode = newPackage.getNode();
    if (currentPackage != null) {
      final ASTNode currNode = currentPackage.getNode();
      assert currNode != null;
      fileNode.replaceChild(currNode, newNode);
    } else {
      final ASTNode anchor = fileNode.getFirstChildNode();
      fileNode.addChild(newNode, anchor);
      fileNode.addLeaf(GroovyTokenTypes.mNLS, "\n", anchor);
    }
  }

  public void clearCaches() {
    super.clearCaches();
    myScriptClass = null;
    myScriptClassInitialized = false;
    mySyntheticArgsParameter = null;
  }

  public PsiElement getContext() {
    final PsiElement context = super.getContext();
    if (context != null) return context;
    return myContext;
  }

  public void setContext(GroovyPsiElement context) {
    myContext = context;
  }

  @NotNull
  public PsiClass[] getClasses() {
    final PsiClass[] declaredDefs = super.getClasses();
    if (!isScript()) return declaredDefs;
    final PsiClass scriptClass = getScriptClass();
    PsiClass[] result = new PsiClass[declaredDefs.length + 1];
    result[0] = scriptClass;
    System.arraycopy(declaredDefs, 0, result, 1, declaredDefs.length);
    return result;
  }

  public PsiElement getOriginalElement() {
    final PsiClass scriptClass = getScriptClass();
    if (scriptClass != null) {
      return scriptClass.getOriginalElement();
    }
    return this;
  }
}

