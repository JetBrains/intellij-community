package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.*;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxClassStub;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxFunctionStub;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   16:21:57
 */
public class JavaFxFunctionDefinitionImpl extends JavaFxPresentableElementImpl<JavaFxFunctionStub> implements JavaFxFunctionDefinition {
  public JavaFxFunctionDefinitionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public JavaFxFunctionDefinitionImpl(@NotNull JavaFxFunctionStub stub) {
    super(stub, JavaFxElementTypes.FUNCTION_DEFINITION);
  }

  @Nullable
  @Override
  public JavaFxSignature getSignature() {
    return getStubOrPsiChild(JavaFxElementTypes.FUNCTION_SIGNATURE);
  }

  @Override
  public String getName() {
    final JavaFxFunctionStub stub = getStub();
    if (stub != null) {
      return stub.getName();
    }
    return super.getName();
  }

  @Override
  public PsiType getReturnType() {
    final JavaFxSignature signature = getSignature();
    if (signature != null) {
      final PsiType resultType = signature.getReturnType();
      if (resultType != null) {
        return resultType;
      }
    }
    return getCodeBlock().getType();
  }

  @Nullable
  @Override
  public JavaFxBlockExpression getCodeBlock() {
    return (JavaFxBlockExpression)childToPsi(JavaFxElementTypes.BLOCK_EXPRESSION);
  }

  @Nullable
  public JavaFxClassDefinition getContainingClass() {
    final JavaFxFunctionStub stub = getStub();
    if (stub != null) {
      final StubElement parentStub = stub.getParentStub();
      if (parentStub instanceof JavaFxClassStub) {
        return ((JavaFxClassStub)parentStub).getPsi();
      }
      return null;
    }

    final PsiElement parent = getParent();
    if (parent instanceof JavaFxClassDefinition) {
      return (JavaFxClassDefinition)parent;
    }
    return null;
  }

  public void acceptJavaFxVisitor(@NotNull JavaFxElementVisitor visitor) {
    visitor.visitFunctionDefinition(this);
  }

  @Override
  public Icon getIcon(int flags) {
    return Icons.METHOD_ICON;
  }

  @Override
  protected String getLocationString() {
    final String locationString = super.getLocationString();
    final JavaFxClassDefinition containingClass = getContainingClass();
    if (containingClass != null) {
      return containingClass.getName() + " in " + locationString;
    }
    return locationString;
  }

// TODO: !!!

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  @Nullable
  public JavaFxQualifiedName getQualifiedName() {
    final JavaFxFunctionStub stub = getStub();
    if (stub != null) {
      return stub.getQualifiedName();
    }
    return (getContainingClass() == null) ? JavaFxPsiUtil.getQName(this) : null;
  }

  @Override
  public boolean processDeclarations(@NotNull final PsiScopeProcessor processor,
                                     @NotNull final ResolveState state,
                                     final PsiElement lastParent,
                                     @NotNull final PsiElement place) {
    final JavaFxSignature signature = getSignature();
    if (signature != null && signature != lastParent && !signature.processDeclarations(processor, state, lastParent, place)) {
      return false;
    }
    return processor.execute(this, state);
  }
}
