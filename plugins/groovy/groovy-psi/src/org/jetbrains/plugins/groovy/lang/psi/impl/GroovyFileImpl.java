/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrFileStub;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrPackageDefinitionStub;
import org.jetbrains.plugins.groovy.lang.resolve.MethodTypeInferencer;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.imports.GroovyFileImports;
import org.jetbrains.plugins.groovy.lang.resolve.imports.GroovyImports;

import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.processLocals;
import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.shouldProcessPackages;
import static org.jetbrains.plugins.groovy.lang.resolve.bindings.BindingsKt.processBindings;

/**
 * Implements all abstractions related to Groovy file
 *
 * @author ilyas
 */
public class GroovyFileImpl extends GroovyFileBaseImpl implements GroovyFile, PsiModifiableCodeBlock {

  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl");

  private static final String SYNTHETIC_PARAMETER_NAME = "args";

  private volatile Boolean myScript;
  private volatile GroovyScriptClass myScriptClass;
  private volatile GrParameter mySyntheticArgsParameter;
  private volatile PsiElement myContext;

  public GroovyFileImpl(FileViewProvider viewProvider) {
    super(viewProvider, GroovyLanguage.INSTANCE);
  }

  @Override
  @NotNull
  public String getPackageName() {
    GrPackageDefinition packageDef = getPackageDefinition();
    if (packageDef != null) {
      final String name = packageDef.getPackageName();
      if (name != null) {
        return name;
      }
    }
    return "";
  }

  @Override
  public GrPackageDefinition getPackageDefinition() {
    final StubElement<?> stub = getStub();
    if (stub != null) {
      for (StubElement element : stub.getChildrenStubs()) {
        if (element instanceof GrPackageDefinitionStub) return (GrPackageDefinition)element.getPsi();
      }
      return null;
    }

    ASTNode node = calcTreeElement().findChildByType(GroovyElementTypes.PACKAGE_DEFINITION);
    return node != null ? (GrPackageDefinition)node.getPsi() : null;
  }

  private GrParameter getSyntheticArgsParameter() {
    GrParameter parameter = mySyntheticArgsParameter;
    if (parameter == null) {
      final PsiType psiType = JavaPsiFacade.getElementFactory(getProject()).createTypeFromText("java.lang.String[]", this);
      parameter = new GrLightParameter(SYNTHETIC_PARAMETER_NAME, psiType, this);
      mySyntheticArgsParameter = parameter;
    }
    return parameter;
  }

  @Override
  public boolean processDeclarations(@NotNull final PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @NotNull PsiElement place) {
    ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);

    if (getStub() == null) {
      if (!(lastParent instanceof GrMember)) {
        // only local usages are traversed here. Having a stub means the clients are outside and won't see our variables
        if (!processLocals(this, processor, state, lastParent, place)) return false;
      }
      if (!processBindings(this, processor, state, lastParent, place)) return false;
    }

    if (myContext != null) {
      return true;
    }

    final GroovyScriptClass scriptClass = getScriptClass();
    if (scriptClass != null && !(lastParent instanceof GrTypeDefinition)) {
      if (!ResolveUtil.processClassDeclarations(scriptClass, processor, state, lastParent, place)) return false;
    }

    if (!super.processDeclarations(processor, state, lastParent, place)) return false;

    if (shouldProcessPackages(processor)) {
      NameHint nameHint = processor.getHint(NameHint.KEY);
      String expectedName = nameHint != null ? nameHint.getName(state) : null;

      final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
      if (expectedName != null) {
        final PsiPackage pkg = facade.findPackage(expectedName);
        if (pkg != null && !processor.execute(pkg, state)) {
          return false;
        }
      }
      else {
        PsiPackage defaultPackage = facade.findPackage("");
        if (defaultPackage != null) {
          for (PsiPackage subPackage : defaultPackage.getSubPackages(getResolveScope())) {
            if (!ResolveUtil.processElement(processor, subPackage, state)) return false;
          }
        }
      }
    }

    if (ResolveUtil.shouldProcessProperties(classHint)) {
      if (lastParent != null && !(lastParent instanceof GrTypeDefinition) && scriptClass != null) {
        if (!ResolveUtil.processElement(processor, getSyntheticArgsParameter(), state)) return false;
      }
    }

    return true;
  }

  @Override
  public boolean isTopControlFlowOwner() {
    return true;
  }

  @Override
  public GrImportStatement[] getImportStatements() {
    final StubElement<?> stub = getStub();
    if (stub != null) {
      return stub.getChildrenByType(GroovyElementTypes.IMPORT_STATEMENT, GrImportStatement.ARRAY_FACTORY);
    }

    return calcTreeElement().getChildrenAsPsiElements(GroovyElementTypes.IMPORT_STATEMENT, GrImportStatement.ARRAY_FACTORY);
  }

  @Override
  public GrImportStatement addImportForClass(@NotNull PsiClass aClass) {
    try {
      // Calculating position
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(getProject());

      String qname = aClass.getQualifiedName();
      if (qname != null) {
        GrImportStatement importStatement = factory.createImportStatementFromText(qname, false, false, null);
        return addImport(importStatement);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return null;
  }


  @NotNull
  @Override
  public GrImportStatement addImport(@NotNull GrImportStatement statement) throws IncorrectOperationException {
    return GroovyCodeStyleManager.getInstance(getProject()).addImport(this, statement);
  }

  @Override
  public boolean isScript() {
    final StubElement stub = getStub();
    if (stub instanceof GrFileStub) {
      return ((GrFileStub)stub).isScript();
    }

    Boolean isScript = myScript;
    if (isScript == null) {
      isScript = checkIsScript();
      myScript = isScript;
    }
    return isScript;
  }

  private boolean checkIsScript() {
    final GrTopStatement[] topStatements = findChildrenByClass(GrTopStatement.class);
    boolean hasClassDefinitions = false;
    boolean hasTopStatements = false;
    for (GrTopStatement st : topStatements) {
      if (st instanceof GrTypeDefinition) {
        hasClassDefinitions = true;
      }
      else if (!(st instanceof GrImportStatement || st instanceof GrPackageDefinition)) {
        hasTopStatements = true;
        break;
      }
    }
    return hasTopStatements || !hasClassDefinitions;
  }

  @Override
  public void subtreeChanged() {
    myScript = null;
    super.subtreeChanged();
  }

  @Override
  public GroovyScriptClass getScriptClass() {
    if (!isScript()) {
      return null;
    }

    GroovyScriptClass aClass = myScriptClass;
    if (aClass == null) {
      aClass = new GroovyScriptClass(this);
      myScriptClass = aClass;
    }

    return aClass;
  }

  @Override
  public void setPackageName(String packageName) {
    final ASTNode fileNode = getNode();
    final GrPackageDefinition currentPackage = getPackageDefinition();
    if (packageName == null || packageName.isEmpty()) {
      if (currentPackage != null) {
        final ASTNode currNode = currentPackage.getNode();
        fileNode.removeChild(currNode);
      }
      return;
    }

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(getProject());
    final GrPackageDefinition newPackage = (GrPackageDefinition)factory.createTopElementFromText("package " + packageName);

    if (currentPackage != null) {
      final GrCodeReferenceElement packageReference = currentPackage.getPackageReference();
      if (packageReference != null) {
        GrCodeReferenceElement ref = newPackage.getPackageReference();
        if (ref != null) {
          packageReference.replace(ref);
        }
        return;
      }
    }

    final ASTNode newNode = newPackage.getNode();
    if (currentPackage != null) {
      final ASTNode currNode = currentPackage.getNode();
      fileNode.replaceChild(currNode, newNode);
    }
    else {
      ASTNode anchor = fileNode.getFirstChildNode();
      if (anchor != null && anchor.getElementType() == GroovyTokenTypes.mSH_COMMENT) {
        anchor = anchor.getTreeNext();
        fileNode.addLeaf(GroovyTokenTypes.mNLS, "\n", anchor);
      }
      fileNode.addChild(newNode, anchor);
      if (anchor != null && !anchor.getText().startsWith("\n\n")) {
        fileNode.addLeaf(GroovyTokenTypes.mNLS, "\n", anchor);
      }
    }
  }

  @Nullable
  @Override
  public GrPackageDefinition setPackage(@Nullable GrPackageDefinition newPackage) {
    final GrPackageDefinition oldPackage = getPackageDefinition();
    if (oldPackage == null) {
      if (newPackage != null) {
        final GrPackageDefinition result = (GrPackageDefinition)addAfter(newPackage, null);
        getNode().addLeaf(GroovyTokenTypes.mNLS, "\n", result.getNode().getTreeNext());
        return result;
      }
    }
    else {
      if (newPackage != null) {
        return (GrPackageDefinition)oldPackage.replace(newPackage);
      }
      else {
        oldPackage.delete();
      }
    }
    return null;
  }

  @Override
  public PsiType getInferredScriptReturnType() {
    return CachedValuesManager.getCachedValue(this, () -> CachedValueProvider.Result
      .create(GroovyPsiManager.inferType(this, new MethodTypeInferencer(this)),
              PsiModificationTracker.MODIFICATION_COUNT));
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    myScriptClass = null;
    mySyntheticArgsParameter = null;
  }

  @Override
  public PsiElement getContext() {
    return myContext != null && myContext.isValid() ? myContext : super.getContext();
  }

  @Override
  @SuppressWarnings({"CloneDoesntDeclareCloneNotSupportedException"})
  protected GroovyFileImpl clone() {
    GroovyFileImpl clone = (GroovyFileImpl)super.clone();
    clone.myContext = myContext;
    return clone;
  }

  public void setContext(PsiElement context) {
    if (context != null) {
      myContext = context;
    }
  }

  public void setContextNullable(PsiElement context) {
    myContext = context;
  }

  @Override
  @NotNull
  public PsiClass[] getClasses() {
    final PsiClass[] declaredDefs = super.getClasses();
    if (!isScript()) return declaredDefs;
    final PsiClass scriptClass = getScriptClass();
    PsiClass[] result = new PsiClass[declaredDefs.length + 1];
    result[result.length - 1] = scriptClass;
    System.arraycopy(declaredDefs, 0, result, 0, declaredDefs.length);
    return result;
  }

  @Override
  public PsiElement getOriginalElement() {
    final PsiClass scriptClass = getScriptClass();
    if (scriptClass != null) {
      final PsiElement originalElement = scriptClass.getOriginalElement();
      if (originalElement != scriptClass && originalElement != null) {
        return originalElement.getContainingFile();
      }
    }
    return this;
  }

  @NotNull
  @Override
  public GrVariableDeclaration[] getScriptDeclarations(boolean topLevelOnly) {
    return PsiImplUtilKt.getScriptDeclarations(this, topLevelOnly);
  }

  @Override
  public boolean shouldChangeModificationCount(PsiElement place) {
    if (!isContentsLoaded()) return true;
    // 1. We actually should never get GrTypeDefinition as a parent, because it is a PsiClass,
    //    and PsiClasses prevent to go up in a tree any further
    // 2. If place is under a variable then @BaseScript or @Field may be changed,
    //    which actually is a change in Java Structure
    return !isScript() || PsiTreeUtil.getParentOfType(place, GrTypeDefinition.class, GrVariableDeclaration.class) != null;
  }

  protected GroovyFileImports getImports() {
    return GroovyImports.getImports(this);
  }

  @Override
  public String toString() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return super.toString();
    }
    return "GroovyFileImpl:" + getName();
  }
}

