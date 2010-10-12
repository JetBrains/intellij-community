package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.JavaFxLanguage;
import org.jetbrains.javafx.lang.lexer.JavaFxTokenTypes;
import org.jetbrains.javafx.lang.psi.JavaFxElement;
import org.jetbrains.javafx.lang.psi.JavaFxElementVisitor;
import org.jetbrains.javafx.lang.psi.JavaFxPsiUtil;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   16:26:11
 */
public abstract class JavaFxBaseElementImpl<T extends StubElement> extends StubBasedPsiElementBase<T> implements JavaFxElement {
  public JavaFxBaseElementImpl(@NotNull T stub, @NotNull IStubElementType nodeType) {
    super(stub, nodeType);
  }

  public JavaFxBaseElementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @NotNull
  public JavaFxLanguage getLanguage() {
    return JavaFxLanguage.getInstance();
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaFxElementVisitor) {
      acceptJavaFxVisitor((JavaFxElementVisitor)visitor);
    }
    else {
      visitor.visitElement(this);
    }
  }

  protected void acceptJavaFxVisitor(@NotNull JavaFxElementVisitor visitor) {
    visitor.visitElement(this);
  }

  @Override
  public String getName() {
    final ASTNode nameNode = getNameNode();
    if (nameNode != null) {
      return nameNode.getText();
    }
    return super.getName();
  }

  // TODO: better place?
  @Nullable
  public ASTNode getNameNode() {
    return getNode().findChildByType(JavaFxTokenTypes.NAME);
  }

  @NotNull
  protected <T extends JavaFxElement> T[] childrenToPsi(TokenSet filterSet, T[] array) {
    final ASTNode[] nodes = getNode().getChildren(filterSet);
    return JavaFxPsiUtil.nodesToPsi(nodes, array);
  }

  @Nullable
  protected <T extends JavaFxElement> T childToPsi(TokenSet filterSet, int index) {
    final ASTNode[] nodes = getNode().getChildren(filterSet);
    if (nodes.length <= index) {
      return null;
    }
    //noinspection unchecked
    return (T)nodes[index].getPsi();
  }

  @Nullable
  protected <T extends JavaFxElement> T childToPsi(TokenSet filterSet) {
    final ASTNode[] nodes = getNode().getChildren(filterSet);
    if (nodes.length != 1) {
      return null;
    }
    //noinspection unchecked
    return (T)nodes[0].getPsi();
  }

  @Nullable
  protected <T extends JavaFxElement> T childToPsi(IElementType elType) {
    final ASTNode node = getNode().findChildByType(elType);
    if (node == null) {
      return null;
    }

    //noinspection unchecked
    return (T)node.getPsi();
  }

  @Override
  public String toString() {
    String className = getClass().getName();
    int pos = className.lastIndexOf('.');
    if (pos >= 0) {
      className = className.substring(pos + 1);
    }
    if (className.endsWith("Impl")) {
      className = className.substring(0, className.length() - 4);
    }
    return className;
  }
}
