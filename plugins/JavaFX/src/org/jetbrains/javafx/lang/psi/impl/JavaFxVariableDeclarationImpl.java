package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.lexer.JavaFxTokenTypes;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.*;
import org.jetbrains.javafx.lang.psi.impl.types.JavaFxTypeUtil;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxClassStub;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxVariableStub;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   16:21:57
 */
public class JavaFxVariableDeclarationImpl extends JavaFxPresentableElementImpl<JavaFxVariableStub> implements JavaFxVariableDeclaration {
  public JavaFxVariableDeclarationImpl(@NotNull ASTNode node) {
    super(node);
  }

  public JavaFxVariableDeclarationImpl(JavaFxVariableStub stub) {
    super(stub, JavaFxElementTypes.VARIABLE_DECLARATION);
  }

  @Override
  public String getName() {
    final JavaFxVariableStub stub = getStub();
    if (stub != null) {
      return stub.getName();
    }
    return super.getName();
  }

  @Override
  protected void acceptJavaFxVisitor(@NotNull JavaFxElementVisitor visitor) {
    visitor.visitVariableDeclaration(this);
  }

  @Override
  public Icon getIcon(int flags) {
    return Icons.VARIABLE_ICON;
  }

  @Nullable
  public JavaFxExpression getInitializer() {
    final ASTNode child = getNode().findChildByType(JavaFxTokenTypes.EQ);
    if (child != null) {
      final JavaFxExpression element = PsiTreeUtil.getNextSiblingOfType(child.getPsi(), JavaFxExpression.class);
      if (element != null) {
        return element;
      }
    }
    return null;
  }

  @Override
  public JavaFxTypeElement getTypeElement() {
    return childToPsi(JavaFxElementTypes.TYPE_ELEMENTS);
  }

  @Override
  protected String getLocationString() {
    final String locationString = super.getLocationString();
    final JavaFxVariableStub stub = getStub();
    if (stub != null) {
      final StubElement parentStub = stub.getParentStub();
      if (parentStub instanceof JavaFxClassStub) {
        return ((JavaFxClassStub)parentStub).getName() + " in " + locationString;
      }
    }
    else {
      final PsiElement parent = getParent();
      if (parent instanceof JavaFxClassDefinition) {
        return ((JavaFxClassDefinition)parent).getName() + " in " + locationString;
      }
    }
    return locationString;
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public JavaFxQualifiedName getQualifiedName() {
    final JavaFxVariableStub stub = getStub();
    if (stub != null) {
      return stub.getQualifiedName();
    }
    return (getParent() instanceof JavaFxFile) ? JavaFxPsiUtil.getQName(this) : null;
  }

  @Override
  public PsiType getType() {
    final JavaFxTypeElement typeElement = getTypeElement();
    if (typeElement != null) {
      return typeElement.getType();
    }
    final JavaFxExpression initializer = getInitializer();
    if (initializer != null) {
      return initializer.getType();
    }
    return JavaFxTypeUtil.getObjectClassType(getProject());
  }

  @Override
  public boolean processDeclarations(@NotNull final PsiScopeProcessor processor,
                                     @NotNull final ResolveState state,
                                     final PsiElement lastParent,
                                     @NotNull final PsiElement place) {
    return processor.execute(this, state);
  }
}
