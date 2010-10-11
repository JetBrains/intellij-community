package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.*;
import org.jetbrains.javafx.lang.psi.impl.resolve.JavaFxResolveUtil;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxClassStub;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   16:21:57
 */
public class JavaFxClassDefinitionImpl extends JavaFxPresentableElementImpl<JavaFxClassStub> implements JavaFxClassDefinition {
  public JavaFxClassDefinitionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public JavaFxClassDefinitionImpl(@NotNull JavaFxClassStub stub) {
    super(stub, JavaFxElementTypes.CLASS_DEFINITION);
  }

  @Override
  public String getName() {
    final JavaFxClassStub classStub = getStub();
    if (classStub != null) {
      return classStub.getName();
    }
    return super.getName();
  }

  @Nullable
  public JavaFxQualifiedName getQualifiedName() {
    final JavaFxClassStub stub = getStub();
    if (stub != null) {
      return stub.getQualifiedName();
    }
    else {
      return JavaFxPsiUtil.getQName(this);
    }
  }

  @Override
  public JavaFxElement[] getMembers() {
    return getStubOrPsiChildren(JavaFxElementTypes.CLASS_MEMBERS, JavaFxElement.EMPTY_ARRAY);
  }

  @Override
  public JavaFxReferenceElement[] getSuperClassElements() {
    final JavaFxReferenceList referenceList = childToPsi(JavaFxElementTypes.REFERENCE_LIST);
    assert referenceList != null;
    return referenceList.getReferenceElements();
  }

  @Override
  protected void acceptJavaFxVisitor(@NotNull JavaFxElementVisitor visitor) {
    visitor.visitClassDefinition(this);
  }

  @Override
  public Icon getIcon(int flags) {
    return Icons.CLASS_ICON;
  }

// TODO: !!!

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public boolean processDeclarations(@NotNull final PsiScopeProcessor processor,
                                     @NotNull final ResolveState state,
                                     final PsiElement lastParent,
                                     @NotNull final PsiElement place) {
    if (!JavaFxResolveUtil.processElements(getMembers(), lastParent, processor, state)) {
      return false;
    }
    final JavaFxReferenceElement[] superClassElements = getSuperClassElements();
    for (JavaFxReferenceElement referenceElement : superClassElements) {
      final PsiReference reference = referenceElement.getReference();
      if (reference != null) {
        final PsiElement resolveResult = reference.resolve();
        if (resolveResult != null) {
          if (!resolveResult.processDeclarations(processor, state, lastParent, place)) {
            return false;
          }
        }
      }
    }
    return processor.execute(this, state);
  }
}
