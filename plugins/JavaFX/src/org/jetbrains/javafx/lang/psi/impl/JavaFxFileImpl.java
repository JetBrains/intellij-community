package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.JavaFxFileType;
import org.jetbrains.javafx.JavaFxLanguage;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.*;

/**
 * JavaFx file abstraction
 *
 * @author andrey
 */
@SuppressWarnings({"unchecked"})
public class JavaFxFileImpl extends PsiFileBase implements JavaFxFile {
  public JavaFxFileImpl(FileViewProvider viewProvider) {
    super(viewProvider, JavaFxLanguage.INSTANCE);
  }

  @NotNull
  public FileType getFileType() {
    return JavaFxFileType.INSTANCE;
  }

  @Nullable
  public JavaFxPackageDefinition getPackageDefinition() {
    final StubElement stub = getStub();
    if (stub != null) {
      final StubElement child = stub.findChildStubByType(JavaFxElementTypes.PACKAGE_DEFINITION);
      if (child == null) {
        return null;
      }
      return (JavaFxPackageDefinition)child.getPsi();
    }
    else {
      final ASTNode node = getNode().findChildByType(JavaFxElementTypes.PACKAGE_DEFINITION);
      if (node == null) {
        return null;
      }
      return (JavaFxPackageDefinition)node.getPsi();
    }
  }

  // TODO: stubs
  @NotNull
  public JavaFxImportList[] getImportLists() {
    final StubElement stub = getStub();
    if (stub != null) {
      return (JavaFxImportList[])stub.getChildrenByType(JavaFxElementTypes.IMPORT_LIST, JavaFxImportList.EMPTY_ARRAY);
    }
    final ASTNode[] nodes = getNode().getChildren(TokenSet.create(JavaFxElementTypes.IMPORT_LIST));
    if (nodes == null) {
      return JavaFxImportList.EMPTY_ARRAY;
    }
    return JavaFxPsiUtil.nodesToPsi(nodes, JavaFxImportList.EMPTY_ARRAY);
  }

  @NotNull
  @Override
  public JavaFxElement[] getDefinitions() {
    final StubElement stub = getStub();
    if (stub != null) {
      return (JavaFxElement[])stub.getChildrenByType(JavaFxElementTypes.DEFINITIONS, JavaFxElement.EMPTY_ARRAY);
    }
    final ASTNode[] nodes = getNode().getChildren(JavaFxElementTypes.DEFINITIONS);
    if (nodes == null) {
      return JavaFxElement.EMPTY_ARRAY;
    }
    return JavaFxPsiUtil.nodesToPsi(nodes, JavaFxElement.EMPTY_ARRAY);
  }

  @Override
  public boolean processDeclarations(@NotNull final PsiScopeProcessor processor,
                                     @NotNull final ResolveState state,
                                     final PsiElement lastParent,
                                     @NotNull final PsiElement place) {
    for (JavaFxElement child : getDefinitions()) {
      if (child != lastParent) {
        if (!processor.execute(child, state)) {
          return false;
        }
      }
    }
    for (JavaFxImportList child : getImportLists()) {
      if (child != lastParent) {
        if (!child.processDeclarations(processor, state, this, place)) {
          return false;
        }
      }
    }
    if (!findInBuiltins(processor, state, place)) {
      return false;
    }

    if (!processor.execute(this, state)) {
      return false;
    }

    if (!(lastParent instanceof PsiPackage)) {
      return findInPackages(processor, state, place);
    }
    return true;
  }

  private boolean findInBuiltins(PsiScopeProcessor processor, ResolveState state, PsiElement place) {
    final PsiElement builtins = JavaFxPsiManagerImpl.getInstance(getProject()).getElementByQualifiedName("javafx.lang.Builtins");
    if (builtins != null) {
      return builtins.processDeclarations(processor, state, this, place);
    }
    return true;
  }

  private boolean findInPackages(final PsiScopeProcessor processor, final ResolveState state, final PsiElement place) {
    final String packageName = JavaFxPsiUtil.getPackageNameForElement(this);
    final JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(getProject());
    if (packageName.length() != 0) {
      if (!findInPackage(processor, state, place, javaPsiFacade, packageName)) {
        return false;
      }
    }

    if (!findInPackage(processor, state, place, javaPsiFacade, "")) {
      return false;
    }
    if (!findInPackage(processor, state, place, javaPsiFacade, "javafx.lang")) {
      return false;
    }
    if (!findInPackage(processor, state, place, javaPsiFacade, "java.lang")) {
      return false;
    }
    return true;
  }

  private boolean findInPackage(final PsiScopeProcessor processor,
                                final ResolveState state,
                                final PsiElement place,
                                final JavaPsiFacade javaPsiFacade,
                                final String packageName) {
    final PsiPackage psiPackage = javaPsiFacade.findPackage(packageName);
    if (psiPackage != null) {
      if (!psiPackage.processDeclarations(processor, state, this, place)) {
        return false;
      }
      if (!JavaFxPsiManagerImpl.getInstance(getProject()).processPackageFiles(psiPackage, processor, state, this, place)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public String toString() {
    return "JavFxFile:" + getName();
  }
}
