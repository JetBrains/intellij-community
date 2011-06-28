/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataCache;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.ElementBase;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.impl.FileManagerImpl;
import com.intellij.psi.scope.DelegatingScopeProcessor;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptTypeDetector;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTopLevelDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrFileStub;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements all abstractions related to Groovy file
 *
 * @author ilyas
 */
public class GroovyFileImpl extends GroovyFileBaseImpl implements GroovyFile {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl");
  private static final Object lock = new Object();

  private volatile Boolean myScript;
  private GroovyScriptClass myScriptClass;
  private static final String SYNTHETIC_PARAMETER_NAME = "args";
  private GrParameter mySyntheticArgsParameter = null;
  private PsiElement myContext;

  public GroovyFileImpl(FileViewProvider viewProvider) {
    super(viewProvider, GroovyFileType.GROOVY_FILE_TYPE.getLanguage());
  }

  @NotNull
  public String getPackageName() {
    final StubElement stub = getStub();
    if (stub instanceof GrFileStub) {
      return ((GrFileStub)stub).getPackageName().toString();
    }
    GrPackageDefinition packageDef = getPackageDefinition();
    if (packageDef != null) {
      return packageDef.getPackageName();
    }
    return "";
  }

  public GrPackageDefinition getPackageDefinition() {
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
      return true;
    }

    PsiClass scriptClass = getScriptClass();
    if (scriptClass != null) {
      if (!scriptClass.processDeclarations(processor, state, lastParent, place)) return false;
      if (!ResolveUtil.processElement(processor, scriptClass, state)) return false;
    }

    for (GrTypeDefinition definition : getTypeDefinitions()) {
      if (!ResolveUtil.processElement(processor, definition, state)) return false;
    }

    if (!processChildrenScopes(processor, state, lastParent, place)) return false;

    final ClassHint classHint = processor.getHint(ClassHint.KEY);
    final boolean processClasses = classHint == null || classHint.shouldProcess(ClassHint.ResolveKind.CLASS);

    final String expectedName = ResolveUtil.getNameHint(processor);

    PsiScopeProcessor importProcessor = !processClasses || expectedName == null ? processor : new DelegatingScopeProcessor(processor) {
      public boolean execute(PsiElement element, ResolveState state) {
        return isImplicitlyImported(element, expectedName) || super.execute(element, state);
      }
    };
    GrImportStatement[] importStatements = getImportStatements();
    if (!processImports(state, lastParent, place, importProcessor, importStatements, false)) return false;

    if (processClasses && !processImplicitImports(processor, state, lastParent, place)) {
      return false;
    }

    if (classHint == null || classHint.shouldProcess(ClassHint.ResolveKind.PACKAGE)) {
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

    if (!processImports(state, lastParent, place, importProcessor, importStatements, true)) return false;

    if (lastParent != null && !(lastParent instanceof GrTypeDefinition) && scriptClass != null) {
      if (!ResolveUtil.processElement(processor, getSyntheticArgsParameter(), state)) return false;
    }

    return true;
  }

  @Nullable
  private PsiElement getShellComment() {
    final ASTNode node = getNode().findChildByType(GroovyTokenTypes.mSH_COMMENT);
    return node == null ? null : node.getPsi();
  }

  private static boolean processImports(ResolveState state,
                                        PsiElement lastParent,
                                        PsiElement place,
                                        PsiScopeProcessor importProcessor,
                                        GrImportStatement[] importStatements, boolean shouldProcessOnDemand) {
    for (int i = importStatements.length - 1; i >= 0; i--) {
      final GrImportStatement imp = importStatements[i];
      if (shouldProcessOnDemand != imp.isOnDemand()) continue;
      if (!imp.processDeclarations(importProcessor, state, lastParent, place)) {
        return false;
      }
    }
    return true;
  }

  private boolean isImplicitlyImported(PsiElement element, String expectedName) {
    if (!(element instanceof PsiClass)) return false;

    final PsiClass psiClass = (PsiClass)element;
    if (!expectedName.equals(psiClass.getName())) return false;

    final String qname = psiClass.getQualifiedName();
    if (qname == null) return false;

    for (String importedClass : IMPLICITLY_IMPORTED_CLASSES) {
      if (qname.equals(importedClass)) {
        return true;
      }
    }
    for (String pkg : getImplicitlyImportedPackages()) {
      if (qname.equals(pkg + "." + expectedName) || pkg.length() == 0 && qname.equals(expectedName)) {
        return true;
      }
    }
    return false;
  }


  private List<String> getImplicitlyImportedPackages() {
    final ArrayList<String> result = new ArrayList<String>();
    result.add(getPackageName());
    ContainerUtil.addAll(result, IMPLICITLY_IMPORTED_PACKAGES);
    if (isScript()) {
      result.addAll(GroovyScriptTypeDetector.getScriptType(this).appendImplicitImports(this));
    }
    return result;
  }

  private boolean processImplicitImports(PsiScopeProcessor processor, ResolveState state, PsiElement lastParent,
                                         PsiElement place) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());

    for (final String implicitlyImported : getImplicitlyImportedPackages()) {
      PsiPackage aPackage = facade.findPackage(implicitlyImported);
      if (aPackage != null && !aPackage.processDeclarations(new DelegatingScopeProcessor(processor) {
        @Override
        public boolean execute(PsiElement element, ResolveState state) {
          if (element instanceof PsiPackage) return true;
          return super.execute(element, state);
        }
      }, state, lastParent, place)) {
        return false;
      }
    }

    for (String implicitlyImportedClass : IMPLICITLY_IMPORTED_CLASSES) {
      PsiClass clazz = facade.findClass(implicitlyImportedClass, getResolveScope());
      if (clazz != null && !ResolveUtil.processElement(processor, clazz, state)) return false;
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
      if (!(run instanceof GrTopLevelDefinition) &&
          !(run instanceof GrImportStatement) &&
          isDeclarationVisible(lastParent, run) &&
          !run.processDeclarations(processor, state, null, place)) {
        return false;
      }
      run = run.getPrevSibling();
    }

    return true;
  }

  private static boolean isDeclarationVisible(PsiElement lastParent, PsiElement decl) {
    if (lastParent instanceof GrMethod && decl instanceof GrVariableDeclaration) {
      return false;
    }
    return true;
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
    final Icon baseIcon = isScript() ? GroovyScriptTypeDetector.getScriptType(this).getScriptIcon() : GroovyIcons.GROOVY_ICON_16x16;
    return ElementBase.createLayeredIcon(baseIcon, ElementBase.transformFlags(this, flags));
  }

  public GrImportStatement addImportForClass(PsiClass aClass) {
    try {
      // Calculating position
      Project project = aClass.getProject();
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
      GrImportStatement importStatement = factory.createImportStatementFromText(aClass.getQualifiedName(), false, false, null);
      PsiElement anchor = getAnchorToInsertImportAfter();
      return (GrImportStatement)addAfter(importStatement, anchor);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  @Nullable
  private PsiElement getAnchorToInsertImportAfter() {
    GrImportStatement[] importStatements = getImportStatements();
    if (importStatements.length > 0) {
      return importStatements[importStatements.length - 1];
    }
    else if (getPackageDefinition() != null) {
      return getPackageDefinition();
    }
    else if (getShellComment() != null) {
      return getShellComment();
    }

    return null;
  }


  public GrImportStatement addImport(GrImportStatement statement) throws IncorrectOperationException {
    PsiElement anchor = getAnchorToInsertImportAfter();
    final PsiElement result = addAfter(statement, anchor);

    boolean isAliasedImport = false;
    if (anchor instanceof GrImportStatement) {
      isAliasedImport = !((GrImportStatement)anchor).isAliasedImport() && statement.isAliasedImport() ||
                        ((GrImportStatement)anchor).isAliasedImport() && !statement.isAliasedImport();
    }

    if (anchor != null) {
      int lineFeedCount = 0;
      if (!(anchor instanceof GrImportStatement) || isAliasedImport) {
        lineFeedCount++;
      }
      final PsiElement prev = result.getPrevSibling();
      if (prev instanceof PsiWhiteSpace) {
        lineFeedCount += StringUtil.getOccurenceCount(prev.getText(), '\n');
      }
      if (lineFeedCount > 0) {
        getNode().addLeaf(GroovyTokenTypes.mNLS, StringUtil.repeatSymbol('\n', lineFeedCount), result.getNode());
      }
      if (prev instanceof PsiWhiteSpace) {
        prev.delete();
      }
    }

    GrImportStatement importStatement = (GrImportStatement)result;
    PsiElement next = importStatement.getNextSibling();
    if (next != null) {
      ASTNode node = next.getNode();
      if (node != null && GroovyTokenTypes.mNLS == node.getElementType()) {
        next.replace(GroovyPsiElementFactory.getInstance(statement.getProject()).createLineTerminator(2));
      }
    }
    return importStatement;
  }

  public boolean isScript() {
    final StubElement stub = getStub();
    if (stub instanceof GrFileStub) {
      return ((GrFileStub)stub).isScript();
    }

    if (myScript == null) {
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
      myScript = hasTopStatements || !hasClassDefinitions;
    }
    return myScript;
  }

  @Override
  public void subtreeChanged() {
    myScript = null;
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
        packageReference.replace(newPackage.getPackageReference());
        return;
      }
    }

    final ASTNode newNode = newPackage.getNode();
    if (currentPackage != null) {
      final ASTNode currNode = currentPackage.getNode();
      fileNode.replaceChild(currNode, newNode);
    } else {
      final ASTNode anchor = fileNode.getFirstChildNode();
      fileNode.addChild(newNode, anchor);
      fileNode.addLeaf(GroovyTokenTypes.mNLS, "\n", anchor);
    }
  }

  public <T extends GrMembersDeclaration> T addMemberDeclaration(@NotNull T decl, PsiElement anchorBefore)
    throws IncorrectOperationException {
    T result = (T)addBefore(decl, anchorBefore);
    CodeStyleManager styleManager = getManager().getCodeStyleManager();
    PsiElement parent = result.getContainingFile();
    TextRange range = result.getTextRange();
    styleManager.reformatRange(parent, range.getEndOffset() - 1, range.getEndOffset() + 1);
    styleManager.reformatRange(parent, range.getStartOffset() - 1, range.getStartOffset() + 1);

    return result;
  }

  public void removeMemberDeclaration(GrMembersDeclaration decl) {
    try {
      deleteChildRange(decl, decl);
    }
    catch (IncorrectOperationException e) {
      throw new RuntimeException(e);
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

  public void clearCaches() {
    super.clearCaches();
//    myScriptClass = null;
//    myScriptClassInitialized = false;
    synchronized (lock) {
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
      if (originalElement != scriptClass) {
        return originalElement.getContainingFile();
      }
    }
    return this;
  }

  private static final UserDataCache<CachedValue<GlobalSearchScope>, GroovyFile, GlobalSearchScope> RESOLVE_SCOPE_CACHE = new UserDataCache<CachedValue<GlobalSearchScope>, GroovyFile, GlobalSearchScope>("RESOLVE_SCOPE_CACHE") {
    @Override
    protected CachedValue<GlobalSearchScope> compute(final GroovyFile file, final GlobalSearchScope baseScope) {
      return CachedValuesManager.getManager(file.getProject()).createCachedValue(new CachedValueProvider<GlobalSearchScope>() {
        public Result<GlobalSearchScope> compute() {
          GlobalSearchScope scope = GroovyScriptTypeDetector.getScriptType(file).patchResolveScope(file, baseScope);
          return Result.create(scope, file, ProjectRootManager.getInstance(file.getProject()));
        }
      }, false);
    }
  };
  public GlobalSearchScope getFileResolveScope() {
    final PsiElement context = getContext();
    if (context instanceof GroovyFile) {
      return context.getResolveScope();
    }

    final VirtualFile vFile = getOriginalFile().getVirtualFile();                                        
    if (vFile == null) {
      return GlobalSearchScope.allScope(getProject());
    }

    final GlobalSearchScope baseScope = ((FileManagerImpl)((PsiManagerEx)getManager()).getFileManager()).getDefaultResolveScope(vFile);
    if (isScript()) {
      return RESOLVE_SCOPE_CACHE.get(this, baseScope).getValue();
    }
    return baseScope;
  }
}

