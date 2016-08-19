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

import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiFileEx;
import com.intellij.psi.impl.source.resolve.SymbolCollectingProcessor.ResultWithContext;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MostlySingularMultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTopLevelDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyImportHelper.ImportKind;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrFileStub;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrPackageDefinitionStub;
import org.jetbrains.plugins.groovy.lang.resolve.ImplicitImportsKt;
import org.jetbrains.plugins.groovy.lang.resolve.MethodTypeInferencer;
import org.jetbrains.plugins.groovy.lang.resolve.PackageSkippingProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;

import java.util.concurrent.ConcurrentMap;

/**
 * Implements all abstractions related to Groovy file
 *
 * @author ilyas
 */
public class GroovyFileImpl extends GroovyFileBaseImpl implements GroovyFile {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl");

  private static final String SYNTHETIC_PARAMETER_NAME = "args";

  private static final CachedValueProvider<ConcurrentMap<String, GrBindingVariable>> BINDING_PROVIDER = () -> {
    final ConcurrentMap<String, GrBindingVariable> map = ContainerUtil.newConcurrentMap();
    return CachedValueProvider.Result.create(map, PsiModificationTracker.MODIFICATION_COUNT);
  };

  private volatile Boolean myScript;
  private volatile GroovyScriptClass myScriptClass;
  private volatile GrParameter mySyntheticArgsParameter;
  private volatile PsiElement myContext;
  private final CachedValue<MostlySingularMultiMap<String, ResultWithContext>> myResolveCache;

  public GroovyFileImpl(FileViewProvider viewProvider) {
    super(viewProvider, GroovyLanguage.INSTANCE);
    myResolveCache = CachedValuesManager.getManager(myManager.getProject()).createCachedValue(
      () -> CachedValueProvider.Result.create(buildDeclarationCache(), PsiModificationTracker.MODIFICATION_COUNT, this), false);
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

    if (isPhysical() && !isScript() &&
        (getUserData(PsiFileEx.BATCH_REFERENCE_PROCESSING) == Boolean.TRUE || myResolveCache.hasUpToDateValue())) {
      return processCachedDeclarations(processor, state, myResolveCache.getValue());
    }

    return processDeclarationsNoGuess(processor, state, lastParent, place);
  }

  private static boolean processCachedDeclarations(@NotNull PsiScopeProcessor processor,
                                                   @NotNull ResolveState state,
                                                   MostlySingularMultiMap<String, ResultWithContext> cache) {
    String name = ResolveUtil.getNameHint(processor);
    Processor<ResultWithContext> cacheProcessor = res ->
      processor.execute(res.getElement(), state.put(ClassHint.RESOLVE_CONTEXT, res.getFileContext()));
    return name != null ? cache.processForKey(name, cacheProcessor) : cache.processAllValues(cacheProcessor);
  }

  @NotNull
  private MostlySingularMultiMap<String, ResultWithContext> buildDeclarationCache() {
    MostlySingularMultiMap<String, ResultWithContext> results = new MostlySingularMultiMap<>();
    processDeclarationsNoGuess(new BaseScopeProcessor() {
      @Override
      public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
        if (element instanceof PsiNamedElement) {
          PsiElement context = state.get(ClassHint.RESOLVE_CONTEXT);
          String name = getDeclarationName((PsiNamedElement)element, context);
          if (name != null) {
            results.add(name, new ResultWithContext((PsiNamedElement)element, context));
          }
        }
        return true;
      }

      private String getDeclarationName(@NotNull PsiNamedElement element, @Nullable PsiElement context) {
        String name = context instanceof GrImportStatement ? ((GrImportStatement)context).getImportedName() : null;
        return name != null ? name : element.getName();
      }
    }, ResolveState.initial(), null, this);
    return results;
  }

  private boolean processDeclarationsNoGuess(@NotNull PsiScopeProcessor processor,
                                             @NotNull ResolveState state,
                                             @Nullable PsiElement lastParent, @NotNull PsiElement place) {
    ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);

    if (myContext != null) {
      if (ResolveUtil.shouldProcessProperties(classHint)) {
        if (!processChildrenScopes(processor, state, lastParent, place)) return false;
      }
      return true;
    }

    boolean processClasses = ResolveUtil.shouldProcessClasses(classHint);

    GrImportStatement[] importStatements = getImportStatements();
    if (!processImports(processor, state, lastParent, place, importStatements, ImportKind.ALIAS, false)) return false;

    GroovyScriptClass scriptClass = getScriptClass();
    if (scriptClass != null && StringUtil.isJavaIdentifier(scriptClass.getName())) {

      if (!(lastParent instanceof GrTypeDefinition)) {
        if (!ResolveUtil.processClassDeclarations(scriptClass, processor, state, lastParent, place)) return false;
      }

      if (processClasses) {
        if (!ResolveUtil.processElement(processor, scriptClass, state)) return false;
      }
    }

    if (processClasses) {
      for (GrTypeDefinition definition : getTypeDefinitions()) {
        if (!ResolveUtil.processElement(processor, definition, state)) return false;
      }
    }

    if (ResolveUtil.shouldProcessProperties(classHint)) {
      if (!processChildrenScopes(processor, state, lastParent, place)) return false;
    }

    if (!processImports(processor, state, lastParent, place, importStatements, ImportKind.ALIAS, true)) return false;
    if (!processImports(processor, state, lastParent, place, importStatements, ImportKind.SIMPLE, null)) return false;
    if (!processDeclarationsInPackage(processor, state, lastParent, place)) return false;
    if (!processImports(processor, state, lastParent, place, importStatements, ImportKind.ON_DEMAND, null)) return false;
    if (!ImplicitImportsKt.processImplicitImports(processor, state, lastParent, place, this)) return false;

    if (ResolveUtil.shouldProcessPackages(classHint)) {

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

  public boolean isInScriptBody(PsiElement lastParent, PsiElement place) {
    return isScript() &&
        !(lastParent instanceof GrTypeDefinition) &&
        PsiTreeUtil.getParentOfType(place, GrTypeDefinition.class, false) == null;
  }

  protected boolean processImports(PsiScopeProcessor processor,
                                   @NotNull ResolveState state,
                                   @Nullable PsiElement lastParent,
                                   @NotNull PsiElement place,
                                   @NotNull GrImportStatement[] importStatements,
                                   @NotNull ImportKind kind,
                                   @Nullable Boolean processStatic) {
    return GroovyImportHelper.processImports(state, lastParent, place, processor, importStatements, kind, processStatic);
  }

  @NotNull
  public ConcurrentMap<String, GrBindingVariable> getBindings() {
    return CachedValuesManager.getCachedValue(this, BINDING_PROVIDER);
  }

  @Override
  public boolean isTopControlFlowOwner() {
    return true;
  }

  private boolean processDeclarationsInPackage(@NotNull PsiScopeProcessor processor,
                                               @NotNull ResolveState state,
                                               @Nullable PsiElement lastParent,
                                               @NotNull PsiElement place) {
    if (ResolveUtil.shouldProcessClasses(processor.getHint(ElementClassHint.KEY))) {
      PsiPackage aPackage = JavaPsiFacade.getInstance(getProject()).findPackage(getPackageName());
      if (aPackage != null) {
        return aPackage.processDeclarations(new PackageSkippingProcessor(processor), state, lastParent, place);
      }
    }
    return true;
  }

  private boolean processChildrenScopes(@NotNull PsiScopeProcessor processor,
                                        @NotNull ResolveState state,
                                        @Nullable PsiElement lastParent,
                                        @NotNull PsiElement place) {
    final StubElement<?> stub = getStub();
    if (stub != null) {
      return true; // only local usages are traversed here. Having a stub means the clients are outside and won't see our variables
    }

    PsiElement run = lastParent == null ? getLastChild() : lastParent.getPrevSibling();
    while (run != null) {
      if (shouldProcess(lastParent, run) &&
          !run.processDeclarations(processor, state, null, place)) {
        return false;
      }
      run = run.getPrevSibling();
    }

    return true;
  }

  private static boolean shouldProcess(@Nullable PsiElement lastParent, @NotNull PsiElement run) {
    return run instanceof GrAssignmentExpression // binding variables
           || !(run instanceof GrTopLevelDefinition || run instanceof GrImportStatement || lastParent instanceof GrMember);
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
    } else {
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

  @Override
  public String toString() {
    if (ApplicationManager.getApplication().isUnitTestMode()){
      return super.toString();
    }
    return "GroovyFileImpl:" + getName();
  }
}

