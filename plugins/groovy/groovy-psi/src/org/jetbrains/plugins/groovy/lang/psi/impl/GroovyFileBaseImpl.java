// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.impl.PsiFileEx;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.reference.SoftReference;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ControlFlowBuilder;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.GroovyControlFlow;
import org.jetbrains.plugins.groovy.lang.resolve.AnnotationHint;
import org.jetbrains.plugins.groovy.lang.resolve.caches.DeclarationHolder;
import org.jetbrains.plugins.groovy.lang.resolve.caches.FileCacheBuilderProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.imports.GroovyFileImports;
import org.jetbrains.plugins.groovy.lang.resolve.processors.MultiProcessor;

import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.*;

/**
 * @author ilyas
 */
public abstract class GroovyFileBaseImpl extends PsiFileBase implements GroovyFileBase, GrControlFlowOwner {

  private final CachedValue<DeclarationHolder> myAnnotationsCache;
  private final CachedValue<DeclarationHolder> myDeclarationsCache;
  private final DeclarationHolder myAllCachedDeclarations;

  protected GroovyFileBaseImpl(FileViewProvider viewProvider, @NotNull Language language) {
    super(viewProvider, language);
    CachedValuesManager cachedValuesManager = CachedValuesManager.getManager(viewProvider.getManager().getProject());
    myAnnotationsCache = cachedValuesManager.createCachedValue(() -> Result.create(
      buildCache(true), this, PsiModificationTracker.MODIFICATION_COUNT
    ), false);
    myDeclarationsCache = cachedValuesManager.createCachedValue(() -> Result.create(
      buildCache(false), this, PsiModificationTracker.MODIFICATION_COUNT
    ), false);
    myAllCachedDeclarations = (processor, state, place) ->
      myAnnotationsCache.getValue().processDeclarations(processor, state, place) &&
      myDeclarationsCache.getValue().processDeclarations(processor, state, place);
  }

  public GroovyFileBaseImpl(IFileElementType root, IFileElementType root1, FileViewProvider provider) {
    this(provider, root.getLanguage());
    init(root, root1);
  }

  @Override
  @NotNull
  public FileType getFileType() {
    return GroovyFileType.GROOVY_FILE_TYPE;
  }

  @Override
  public String toString() {
    return "Groovy script";
  }

  @Override
  public GrTypeDefinition @NotNull [] getTypeDefinitions() {
    final StubElement<?> stub = getGreenStub();
    if (stub != null) {
      return stub.getChildrenByType(TokenSets.TYPE_DEFINITIONS, GrTypeDefinition.ARRAY_FACTORY);
    }

    return calcTreeElement().getChildrenAsPsiElements(TokenSets.TYPE_DEFINITIONS, GrTypeDefinition.ARRAY_FACTORY);
  }

  @Override
  public GrMethod @NotNull [] getMethods() {
    final StubElement<?> stub = getGreenStub();
    if (stub != null) {
      return stub.getChildrenByType(GroovyStubElementTypes.METHOD, GrMethod.ARRAY_FACTORY);
    }

    return calcTreeElement().getChildrenAsPsiElements(GroovyStubElementTypes.METHOD, GrMethod.ARRAY_FACTORY);
  }

  @Override
  public GrTopStatement @NotNull [] getTopStatements() {
    return findChildrenByClass(GrTopStatement.class);
  }

  @Override
  public boolean importClass(@NotNull PsiClass aClass) {
    return addImportForClass(aClass) != null;
  }

  @Override
  public void removeImport(@NotNull GrImportStatement importStatement) throws IncorrectOperationException {
    GroovyCodeStyleManager.getInstance(getProject()).removeImport(this, importStatement);
  }

  @Override
  public void removeElements(PsiElement[] elements) throws IncorrectOperationException {
    for (PsiElement element : elements) {
      if (element.isValid()) {
        if (element.getParent() != this) throw new IncorrectOperationException();
        deleteChildRange(element, element);
      }
    }
  }

  @Override
  public GrStatement @NotNull [] getStatements() {
    return findChildrenByClass(GrStatement.class);
  }

  @Override
  @NotNull
  public GrStatement addStatementBefore(@NotNull GrStatement statement, @Nullable GrStatement anchor) throws IncorrectOperationException {
    final PsiElement result = addBefore(statement, anchor);
    if (anchor != null) {
      getNode().addLeaf(GroovyTokenTypes.mNLS, "\n", anchor.getNode());
    }
    else {
      getNode().addLeaf(GroovyTokenTypes.mNLS, "\n", result.getNode());
    }
    return (GrStatement)result;
  }

  @Override
  public void removeVariable(GrVariable variable) {
    PsiImplUtil.removeVariable(variable);
  }

  @Override
  public GrVariableDeclaration addVariableDeclarationBefore(GrVariableDeclaration declaration, GrStatement anchor) throws IncorrectOperationException {
    GrStatement statement = addStatementBefore(declaration, anchor);
    assert statement instanceof GrVariableDeclaration;
    return ((GrVariableDeclaration) statement);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitFile(this);
  }

  @Override
  public void acceptChildren(@NotNull GroovyElementVisitor visitor) {
    PsiElement child = getFirstChild();
    while (child != null) {
      if (child instanceof GroovyPsiElement) {
        ((GroovyPsiElement) child).accept(visitor);
      }

      child = child.getNextSibling();
    }
  }

  @Override
  public PsiClass @NotNull [] getClasses() {
    return getTypeDefinitions();
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    myControlFlow = null;
  }

  private volatile SoftReference<GroovyControlFlow> myControlFlow;

  @Override
  public Instruction[] getControlFlow() {
    return getGroovyControlFlow().getFlow();
  }

  public GroovyControlFlow getGroovyControlFlow() {
    assert isValid();
    GroovyControlFlow result = SoftReference.dereference(myControlFlow);
    if (result == null) {
      result = ControlFlowBuilder.buildControlFlow(this);
      myControlFlow = new SoftReference<>(result);
    }
    return result;
  }

  @Override
  public boolean isTopControlFlowOwner() {
    return false;
  }

  @Override
  public void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    if (last instanceof GrTopStatement) {
      PsiImplUtil.deleteStatementTail(this, last);
    }
    super.deleteChildRange(first, last);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @NotNull PsiElement place) {
    for (PsiScopeProcessor each : MultiProcessor.allProcessors(processor)) {
      if (!shouldProcessMembers(each)) continue;
      if (!getAppropriateHolder(getAnnotationHint(processor)).processDeclarations(each, state, place)) return false;
    }
    return true;
  }

  @NotNull
  private DeclarationHolder getAppropriateHolder(@Nullable AnnotationHint hint) {
    boolean mayUseCache = useCache();
    if (hint == null) {
      if (mayUseCache || myAnnotationsCache.hasUpToDateValue() && myDeclarationsCache.hasUpToDateValue()) {
        return myAllCachedDeclarations;
      }
    }
    else if (hint.isAnnotationResolve()) {
      if (mayUseCache || myAnnotationsCache.hasUpToDateValue()) {
        return myAnnotationsCache.getValue();
      }
    }
    else {
      if (mayUseCache || myDeclarationsCache.hasUpToDateValue()) {
        return myDeclarationsCache.getValue();
      }
    }
    return this::processDeclarationsNoCache;
  }

  private boolean useCache() {
    if (!isPhysical()) return false;
    if (ApplicationManager.getApplication().isDispatchThread()) return false;
    return getUserData(PsiFileEx.BATCH_REFERENCE_PROCESSING) == Boolean.TRUE;
  }

  @NotNull
  private DeclarationHolder buildCache(boolean annotationCache) {
    FileCacheBuilderProcessor processor = new FileCacheBuilderProcessor(annotationCache);
    processDeclarationsNoCache(processor, ResolveState.initial(), this);
    return processor.buildCache();
  }

  private boolean processDeclarationsNoCache(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, @NotNull PsiElement place) {
    if (!processClassesInFile(this, processor, state)) return false;
    final GroovyFileImports imports = getImports();
    if (!imports.processAllNamedImports(processor, state, place)) return false;
    if (!processClassesInPackage(this, processor, state, place)) return false;
    if (!imports.processAllStarImports(processor, state, place)) return false;
    if (!imports.processDefaultImports(processor, state, place)) return false;
    return true;
  }
}
