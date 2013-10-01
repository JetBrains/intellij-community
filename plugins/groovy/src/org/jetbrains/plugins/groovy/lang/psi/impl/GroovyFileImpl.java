/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.PackageEntry;
import com.intellij.psi.codeStyle.PackageEntryTable;
import com.intellij.psi.impl.ElementBase;
import com.intellij.psi.scope.DelegatingScopeProcessor;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings;
import org.jetbrains.plugins.groovy.editor.GroovyImportHelper;
import org.jetbrains.plugins.groovy.editor.GroovyImportOptimizer;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptTypeDetector;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTopLevelDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrFileStub;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrPackageDefinitionStub;
import org.jetbrains.plugins.groovy.lang.resolve.MethodTypeInferencer;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

import static org.jetbrains.plugins.groovy.editor.GroovyImportHelper.processImplicitImports;

/**
 * Implements all abstractions related to Groovy file
 *
 * @author ilyas
 */
public class GroovyFileImpl extends GroovyFileBaseImpl implements GroovyFile {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl");

  private static final String SYNTHETIC_PARAMETER_NAME = "args";

  private static final CachedValueProvider<ConcurrentMap<String, GrBindingVariable>> BINDING_PROVIDER = new CachedValueProvider<ConcurrentMap<String, GrBindingVariable>>() {
    @Nullable
    @Override
    public Result<ConcurrentMap<String, GrBindingVariable>> compute() {
      final ConcurrentMap<String, GrBindingVariable> map = ContainerUtil.newConcurrentMap();
      return Result.create(map, PsiModificationTracker.MODIFICATION_COUNT);
    }
  };

  private final Object lock = new Object();

  private volatile Boolean myScript;
  private volatile GroovyScriptClass myScriptClass;
  private volatile GrParameter mySyntheticArgsParameter = null;
  private PsiElement myContext;

  public GroovyFileImpl(FileViewProvider viewProvider) {
    super(viewProvider, GroovyFileType.GROOVY_LANGUAGE);
  }

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
    if (mySyntheticArgsParameter == null) {
      final PsiType psiType = JavaPsiFacade.getElementFactory(getProject()).createTypeFromText("java.lang.String[]", this);
      final GrParameter candidate = new GrLightParameter(SYNTHETIC_PARAMETER_NAME, psiType, this);
      synchronized (lock) {
        if (mySyntheticArgsParameter == null) {
          mySyntheticArgsParameter = candidate;
        }
      }
    }
    return mySyntheticArgsParameter;
  }

  public boolean processDeclarations(@NotNull final PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    if (myContext != null) {
      if (!processChildrenScopes(processor, state, lastParent, place)) return false;
      return true;
    }

    GroovyScriptClass scriptClass = getScriptClass();
    if (scriptClass != null && scriptClass.getName() != null) {
      if (!(lastParent instanceof GrTypeDefinition)) {
        if (!scriptClass.processDeclarations(processor, state, lastParent, place)) return false;
      }
      if (!ResolveUtil.processElement(processor, scriptClass, state)) return false;
    }

    for (GrTypeDefinition definition : getTypeDefinitions()) {
      if (!ResolveUtil.processElement(processor, definition, state)) return false;
    }

    if (!processChildrenScopes(processor, state, lastParent, place)) return false;

    final ClassHint classHint = processor.getHint(ClassHint.KEY);
    final boolean processClasses = classHint == null || classHint.shouldProcess(ClassHint.ResolveKind.CLASS);

    final String expectedName = ResolveUtil.getNameHint(processor);

    final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    GrImportStatement[] importStatements = getImportStatements();
    if (!processImports(processor, state, lastParent, place, importStatements, false)) return false;
    if (!processDeclarationsInPackage(processor, state, lastParent, place, facade, getPackageName())) return false;
    if (!processImports(processor, state, lastParent, place, importStatements, true)) return false;

    if (processClasses && !processImplicitImports(processor, state, lastParent, place, this)) {
      return false;
    }

    if (classHint == null || classHint.shouldProcess(ClassHint.ResolveKind.PACKAGE)) {
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

    if (lastParent != null && !(lastParent instanceof GrTypeDefinition) && scriptClass != null) {
      if (!ResolveUtil.processElement(processor, getSyntheticArgsParameter(), state)) return false;
    }

    if (isScript() && !(lastParent instanceof GrTypeDefinition) && PsiTreeUtil.getParentOfType(place, GrTypeDefinition.class, false) == null) {
      if (!processBindings(processor, state, place)) return false;
    }

    return true;
  }

  protected boolean processImports(PsiScopeProcessor processor,
                                   ResolveState state,
                                   PsiElement lastParent,
                                   PsiElement place,
                                   GrImportStatement[] importStatements,
                                   boolean onDemand) {
    return GroovyImportHelper.processImports(state, lastParent, place, processor, importStatements, onDemand);
  }

  private boolean processBindings(@NotNull final PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement place) {
    if (!isPhysical()) return true;

    final NameHint nameHint = processor.getHint(NameHint.KEY);
    if (nameHint == null) return true;

    final String name = nameHint.getName(state);
    if (name == null) return true;

    final ClassHint classHint = processor.getHint(ClassHint.KEY);
    if (classHint != null && !classHint.shouldProcess(ClassHint.ResolveKind.PROPERTY)) return true;


    final ConcurrentMap<String, GrBindingVariable> bindings = getBindings();

    GrBindingVariable variable = bindings.get(name);
    if (variable == null) {
      variable = ConcurrencyUtil.cacheOrGet(bindings, name, new GrBindingVariable(this, name, null));
    }
    variable.updateWriteAccessIfNeeded(place);

    if (!variable.hasWriteAccess()) return true;

    return processor.execute(variable, state);
  }

  private ConcurrentMap<String, GrBindingVariable> getBindings() {
    return CachedValuesManager.getCachedValue(this, BINDING_PROVIDER);
  }

  @Nullable
  private PsiElement getShellComment() {
    final ASTNode node = getNode().findChildByType(GroovyTokenTypes.mSH_COMMENT);
    return node == null ? null : node.getPsi();
  }

  @Override
  public boolean isTopControlFlowOwner() {
    return true;
  }

  private static boolean processDeclarationsInPackage(final PsiScopeProcessor processor,
                                                      ResolveState state,
                                                      PsiElement lastParent,
                                                      PsiElement place, JavaPsiFacade facade,
                                                      String packageName) {
    PsiPackage aPackage = facade.findPackage(packageName);
    if (aPackage != null && !aPackage.processDeclarations(new DelegatingScopeProcessor(processor) {
      @Override
      public boolean execute(@NotNull PsiElement element, ResolveState state) {
        if (element instanceof PsiPackage) return true;
        return super.execute(element, state);
      }
    }, state, lastParent, place)) {
      return false;
    }
    return true;
  }

  private boolean processChildrenScopes(PsiScopeProcessor processor, ResolveState state, PsiElement lastParent, PsiElement place) {
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

  private static boolean shouldProcess(PsiElement lastParent, PsiElement run) {
    return !(run instanceof GrTopLevelDefinition || run instanceof GrImportStatement || lastParent instanceof GrMember);
  }

  public GrImportStatement[] getImportStatements() {
    List<GrImportStatement> result = new ArrayList<GrImportStatement>();
    for (PsiElement child : getChildren()) {
      if (child instanceof GrImportStatement) result.add((GrImportStatement)child);
    }
    return result.toArray(new GrImportStatement[result.size()]);
  }

  @Nullable
  public Icon getIcon(int flags) {
    final Icon baseIcon = isScript() ? GroovyScriptTypeDetector.getScriptType(this).getScriptIcon() : JetgroovyIcons.Groovy.Groovy_16x16;
    return ElementBase.createLayeredIcon(this, baseIcon, ElementBase.transformFlags(this, flags));
  }

  public GrImportStatement addImportForClass(PsiClass aClass) {
    try {
      // Calculating position
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(getProject());
      GrImportStatement importStatement = factory.createImportStatementFromText(aClass.getQualifiedName(), false, false, null);
      return addImport(importStatement);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  @Nullable
  private PsiElement getAnchorToInsertImportAfter(GrImportStatement statement) {
    final GroovyCodeStyleSettings settings = CodeStyleSettingsManager.getInstance(getProject()).getCurrentSettings().getCustomSettings(
      GroovyCodeStyleSettings.class);
    final PackageEntryTable layoutTable = settings.IMPORT_LAYOUT_TABLE;
    final PackageEntry[] entries = layoutTable.getEntries();

    GrImportStatement[] importStatements = getImportStatements();
    if (importStatements.length == 0) {
      final GrPackageDefinition definition = getPackageDefinition();
      if (definition != null) {
        return definition;
      }

      return getShellComment();
    }

    final Comparator<GrImportStatement> comparator = GroovyImportOptimizer.getComparator(settings);

    final int idx = getPackageEntryIdx(entries, statement);

    PsiElement anchor = null;

    for (GrImportStatement importStatement : importStatements) {
      final int i = getPackageEntryIdx(entries, importStatement);
      if (i < idx) {
        anchor = importStatement;
      }
      else if (i > idx) {
        break;
      }
      else if (comparator.compare(statement, importStatement) > 0) {
        anchor = importStatement;
      }
      else {
        break;
      }
    }

    if (anchor == null) anchor = getPackageDefinition();
    if (anchor == null) anchor = getShellComment();
    if (anchor == null && importStatements.length > 0) anchor = importStatements[0].getPrevSibling();
    return anchor;
  }

  public GrImportStatement addImport(GrImportStatement statement) throws IncorrectOperationException {
    PsiElement anchor = getAnchorToInsertImportAfter(statement);
    final PsiElement result = addAfter(statement, anchor);

    final GrImportStatement gImport = (GrImportStatement)result;
    addLineFeedBefore(gImport);
    addLineFeedAfter(gImport);
    return gImport;
  }

  public boolean isScript() {
    final StubElement stub = getStub();
    if (stub instanceof GrFileStub) {
      return ((GrFileStub)stub).isScript();
    }

    if (myScript == null) {
      synchronized (lock) {
        boolean isScript = checkIsScript();
        if (myScript == null) {
          myScript = isScript;
        }
      }
    }
    return myScript;
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
    synchronized (lock) {
      myScript = null;
    }
    super.subtreeChanged();
  }

  public GroovyScriptClass getScriptClass() {
    if (isScript()) {
      if (myScriptClass == null) {
        GroovyScriptClass candidate = new GroovyScriptClass(this);
        synchronized (lock) {
          if (myScriptClass == null) {
            myScriptClass = candidate;
          }
        }
      }
      return myScriptClass;
    }
    else {
      return null;
    }
  }

  public void setPackageName(String packageName) {
    final ASTNode fileNode = getNode();
    assert fileNode != null;
    final GrPackageDefinition currentPackage = getPackageDefinition();
    if (packageName == null || packageName.length() == 0) {
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
    return CachedValuesManager.getCachedValue(this, new CachedValueProvider<PsiType>() {
      @Override
      public Result<PsiType> compute() {
        return Result.create(GroovyPsiManager.inferType(GroovyFileImpl.this, new MethodTypeInferencer(GroovyFileImpl.this)),
                             PsiModificationTracker.MODIFICATION_COUNT);
      }
    });
  }

  public void clearCaches() {
    super.clearCaches();
    synchronized (lock) {
      myScriptClass = null;
      mySyntheticArgsParameter = null;
    }
  }

  public PsiElement getContext() {
    if (myContext != null) {
      return myContext;
    }
    return super.getContext();
  }

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
}

