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
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.impl.FileManagerImpl;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.dsl.GroovyDslFileIndex;
import org.jetbrains.plugins.groovy.dsl.GroovyScriptDescriptor;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTopLevelDefintion;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Implements all abstractions related to Groovy file
 *
 * @author ilyas
 */
public class GroovyFileImpl extends GroovyFileBaseImpl implements GroovyFile {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl");
  private static final Object lock = new Object();

  private GroovyScriptClass myScriptClass;
  private static final String SYNTHETIC_PARAMETER_NAME = "args";
  private GrParameter mySyntheticArgsParameter = null;


  private PsiElement myContext;

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
      final GrParameter candidate =
        GroovyPsiElementFactory.getInstance(getProject()).createParameter(SYNTHETIC_PARAMETER_NAME, "java.lang.String[]", this);
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
    PsiClass scriptClass = getScriptClass();
    if (scriptClass != null) {
      if (!scriptClass.processDeclarations(processor, state, lastParent, place)) return false;
      if (!ResolveUtil.processElement(processor, scriptClass)) return false;
    }

    for (GrTypeDefinition definition : getTypeDefinitions()) {
      if (!ResolveUtil.processElement(processor, definition)) return false;
    }

    if (lastParent != null && !(lastParent instanceof GrTypeDefinition) && scriptClass != null) {
      if (!ResolveUtil.processElement(processor, getSyntheticArgsParameter())) return false;
      // This case is processed by NonCodeMemberProcessor implementations
      //if (!processScriptEnhancements(place, processor)) return false;
    }

    if (!processChildrenScopes(this, processor, state, lastParent, place)) return false;

    JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());

    final GrImportStatement[] imports = getImportStatements();

    for (GrImportStatement importStatement : imports) {
      if (importStatement.isAliasedImport() && !importStatement.processDeclarations(processor, state, lastParent, place)) {
        return false;
      }
    }

    final String className = getWantedClassName(processor, state);
    if (className != null) {
      final Set<String> maybeImplicit = getImplicitlyImportableClasses(facade, className);
      if (!maybeImplicit.isEmpty()) {
        if (isResolvableViaImplictImports(processor, state, lastParent, place, className, maybeImplicit, imports)) {
          if (!processImplicitImports(processor, state, lastParent, place, facade)) {
            return false;
          }
        }
      }
    }

    for (GrImportStatement importStatement : imports) {
      if (!importStatement.isAliasedImport() && !importStatement.processDeclarations(processor, state, lastParent, place)) {
        return false;
      }
    }

    if (!processImplicitImports(processor, state, lastParent, place, facade)) return false;

    if (StringUtil.isNotEmpty(getPackageName())) { //otherwise already processed default package
      PsiPackage defaultPackage = facade.findPackage("");
      if (defaultPackage != null) {
        for (PsiPackage subpackage : defaultPackage.getSubPackages(getResolveScope())) {
          if (!ResolveUtil.processElement(processor, subpackage)) return false;
        }
      }
    }

    return true;
  }


  private static boolean isResolvableViaImplictImports(final PsiScopeProcessor processor, ResolveState state, PsiElement lastParent,
                                                PsiElement place,
                                                @NotNull final String className,
                                                final Set<String> maybeImplicit, GrImportStatement[] imports) {
    boolean isImplicitlyImported = true;
    for (GrImportStatement importStatement : imports) {
      if (!importStatement.processDeclarations(new BaseScopeProcessor() {
        public boolean execute(PsiElement element, ResolveState state) {
          if (element instanceof PsiClass) {
            final PsiClass psiClass = (PsiClass)element;
            final String qname = psiClass.getQualifiedName();
            if (qname != null && className.equals(psiClass.getName()) && !maybeImplicit.contains(qname)) {
              return false;
            }
          }
          return true;
        }

        @Override
        public <T> T getHint(Key<T> hintKey) {
          return processor.getHint(hintKey);
        }
      }, state, lastParent, place)) {
        isImplicitlyImported = false;
        break;
      }
    }
    return isImplicitlyImported;
  }

  private Set<String> getImplicitlyImportableClasses(JavaPsiFacade facade, @NotNull String className) {
    final Set<String> maybeImplicit = new THashSet<String>();
    final String suffix = "." + className;
    for (String implicitlyImportedClass : IMPLICITLY_IMPORTED_CLASSES) {
      if (implicitlyImportedClass.endsWith(suffix)) {
        maybeImplicit.add(implicitlyImportedClass);
      }
    }

    for (final String implicitlyImported : getImplicitlyImportedPackages()) {
      final String candidate = implicitlyImported + suffix;
      if (facade.findClass(candidate, getResolveScope()) != null) {
        maybeImplicit.add(candidate);
      }
    }

    final String pkg = getPackageName();
    final String candidate = StringUtil.isEmpty(pkg) ? className : pkg + suffix;
    if (facade.findClass(candidate, getResolveScope()) != null) {
      maybeImplicit.add(candidate);
    }

    return maybeImplicit;
  }

  private List<String> getImplicitlyImportedPackages() {
    final ArrayList<String> result = new ArrayList<String>(Arrays.asList(IMPLICITLY_IMPORTED_PACKAGES));
    if (isScript()) {
      result.addAll(GroovyScriptType.getScriptType(this).appendImplicitImports(this));
    }
    return result;
  }

  private boolean processImplicitImports(PsiScopeProcessor processor, ResolveState state, PsiElement lastParent,
                                         PsiElement place,
                                         JavaPsiFacade facade) {
    String currentPackageName = getPackageName();
    PsiPackage currentPackage = facade.findPackage(currentPackageName);

    if (currentPackage != null && !currentPackage.processDeclarations(processor, state, lastParent, place)) return false;

    for (final String implicitlyImported : getImplicitlyImportedPackages()) {
      PsiPackage aPackage = facade.findPackage(implicitlyImported);
      if (aPackage != null && !aPackage.processDeclarations(processor, state, lastParent, place)) return false;
    }

    for (String implicitlyImportedClass : IMPLICITLY_IMPORTED_CLASSES) {
      PsiClass clazz = facade.findClass(implicitlyImportedClass, getResolveScope());
      if (clazz != null && !ResolveUtil.processElement(processor, clazz)) return false;
    }
    return true;
  }

  @Nullable
  private static String getWantedClassName(PsiScopeProcessor processor, ResolveState state) {
    final ClassHint hint = processor.getHint(ClassHint.KEY);
    final NameHint nameHint = processor.getHint(NameHint.KEY);
    if (hint != null && hint.shouldProcess(ClassHint.ResolveKind.CLASS) && nameHint != null) {
      return nameHint.getName(state);
    }
    return null;
  }

  private boolean processScriptEnhancements(final PsiElement place, final PsiScopeProcessor processor) {
    final GroovyScriptClass scriptClass = getScriptClass();
    if (scriptClass == null) {
      return true;
    }

    return GroovyDslFileIndex.processExecutors(getProject(), new GroovyScriptDescriptor(this, scriptClass, place), processor);
  }


  private static boolean processChildrenScopes(PsiElement element,
                                               PsiScopeProcessor processor,
                                               ResolveState state,
                                               PsiElement lastParent,
                                               PsiElement place) {
    PsiElement run = lastParent == null ? element.getLastChild() : lastParent.getPrevSibling();
    while (run != null) {
      if (!(run instanceof GrTopLevelDefintion) &&
          !(run instanceof GrImportStatement) &&
          !run.processDeclarations(processor, state, null, place)) {
        return false;
      }
      run = run.getPrevSibling();
    }

    return true;
  }

  public GrImportStatement[] getImportStatements() {
    return findChildrenByClass(GrImportStatement.class);
  }

  @Nullable
  public Icon getIcon(int flags) {
    if (isScript()) {
      return GroovyScriptType.getScriptType(this).getScriptIcon();
    }
    return GroovyIcons.GROOVY_ICON_16x16;
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
    } else if (getPackageDefinition() != null) {
      return getPackageDefinition();
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
    GrTopStatement[] top = findChildrenByClass(GrTopStatement.class);
    for (GrTopStatement st : top) {
      if (!(st instanceof GrTypeDefinition || st instanceof GrImportStatement || st instanceof GrPackageDefinition)) return true;
    }

    return false;
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
        assert currNode != null;
        fileNode.removeChild(currNode);
      }
      return;
    }
    final GrTopStatement newPackage = GroovyPsiElementFactory.getInstance(getProject()).createTopElementFromText("package " + packageName);
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
      final PsiElement originalElement = scriptClass.getOriginalElement();
      if (originalElement != scriptClass) {
        return originalElement.getContainingFile();
      }
    }
    return this;
  }

  public GlobalSearchScope getFileResolveScope() {
    final VirtualFile vFile = getOriginalFile().getVirtualFile();
    if (vFile == null) {
      return GlobalSearchScope.allScope(getProject());
    }

    final GlobalSearchScope baseScope = ((FileManagerImpl)((PsiManagerEx)getManager()).getFileManager()).getDefaultResolveScope(vFile);
    if (isScript()) {
      return GroovyScriptType.getScriptType(this).patchResolveScope(this, baseScope);
    }
    return baseScope;
  }
}

