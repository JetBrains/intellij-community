// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mock;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class MockPsiElement extends UserDataHolderBase implements PsiElement, Navigatable {
  private final String myText;
  private final Project myProject;
  private final List<PsiElement> myDeclarations = new SmartList<>();
  private Ref<PsiElement> myParent;
  private PsiManager myManager;

  public MockPsiElement(@NotNull Disposable parentDisposable) {
    this(null, new MockProjectEx(parentDisposable));
  }

  private MockPsiElement(final String text, @NotNull Project project) {
    myText = text;
    myProject = project;
  }

  @Override
  public void navigate(boolean requestFocus) {
    throw new UnsupportedOperationException("Method navigate is not yet implemented in " + getClass().getName());
  }

  @Override
  public boolean canNavigate() {
    throw new UnsupportedOperationException("Method canNavigate is not yet implemented in " + getClass().getName());
  }

  @Override
  public boolean canNavigateToSource() {
    throw new UnsupportedOperationException("Method canNavigateToSource is not yet implemented in " + getClass().getName());
  }

  public List<PsiElement> getDeclarations() {
    return myDeclarations;
  }

  @Override
  public void accept(final @NotNull PsiElementVisitor visitor) {
    throw new UnsupportedOperationException("Method accept is not yet implemented in " + getClass().getName());
  }

  @Override
  public void acceptChildren(final @NotNull PsiElementVisitor visitor) {
    throw new UnsupportedOperationException("Method acceptChildren is not yet implemented in " + getClass().getName());
  }

  @Override
  public PsiElement add(final @NotNull PsiElement element) throws IncorrectOperationException {
    throw new UnsupportedOperationException("Method add is not yet implemented in " + getClass().getName());
  }

  @Override
  public PsiElement addAfter(final @NotNull PsiElement element, final PsiElement anchor) throws IncorrectOperationException {
    throw new UnsupportedOperationException("Method addAfter is not yet implemented in " + getClass().getName());
  }

  @Override
  public PsiElement addBefore(final @NotNull PsiElement element, final PsiElement anchor) throws IncorrectOperationException {
    throw new UnsupportedOperationException("Method addBefore is not yet implemented in " + getClass().getName());
  }

  @Override
  public PsiElement addRange(final PsiElement first, final PsiElement last) throws IncorrectOperationException {
    throw new UnsupportedOperationException("Method addRange is not yet implemented in " + getClass().getName());
  }

  @Override
  public PsiElement addRangeAfter(final PsiElement first, final PsiElement last, final PsiElement anchor) throws IncorrectOperationException {
    throw new UnsupportedOperationException("Method addRangeAfter is not yet implemented in " + getClass().getName());
  }

  @Override
  public PsiElement addRangeBefore(final @NotNull PsiElement first, final @NotNull PsiElement last, final PsiElement anchor) throws
                                                                                                                             IncorrectOperationException {
    throw new UnsupportedOperationException("Method addRangeBefore is not yet implemented in " + getClass().getName());
  }

  @Override
  public void checkAdd(final @NotNull PsiElement element) throws IncorrectOperationException {
    throw new UnsupportedOperationException("Method checkAdd is not yet implemented in " + getClass().getName());
  }

  @Override
  public void checkDelete() throws IncorrectOperationException {
    throw new UnsupportedOperationException("Method checkDelete is not yet implemented in " + getClass().getName());
  }

  @Override
  public PsiElement copy() {
    throw new UnsupportedOperationException("Method copy is not yet implemented in " + getClass().getName());
  }

  @Override
  public void delete() throws IncorrectOperationException {
    throw new UnsupportedOperationException("Method delete is not yet implemented in " + getClass().getName());
  }

  @Override
  public void deleteChildRange(final PsiElement first, final PsiElement last) throws IncorrectOperationException {
    throw new UnsupportedOperationException("Method deleteChildRange is not yet implemented in " + getClass().getName());
  }

  @Override
  public @Nullable PsiElement findElementAt(final int offset) {
    throw new UnsupportedOperationException("Method findElementAt is not yet implemented in " + getClass().getName());
  }

  @Override
  public @Nullable PsiReference findReferenceAt(final int offset) {
    throw new UnsupportedOperationException("Method findReferenceAt is not yet implemented in " + getClass().getName());
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    throw new UnsupportedOperationException("Method getChildren is not yet implemented in " + getClass().getName());
  }

  @Override
  public PsiFile getContainingFile() throws PsiInvalidElementAccessException {
    throw new UnsupportedOperationException("Method getContainingFile is not yet implemented in " + getClass().getName());
  }

  @Override
  public @Nullable PsiElement getContext() {
    return getParent();
  }

  @Override
  public @Nullable <T> T getCopyableUserData(final @NotNull Key<T> key) {
    throw new UnsupportedOperationException("Method getCopyableUserData is not yet implemented in " + getClass().getName());
  }

  @Override
  public @Nullable PsiElement getFirstChild() {
    throw new UnsupportedOperationException("Method getFirstChild is not yet implemented in " + getClass().getName());
  }

  @Override
  public @NotNull Language getLanguage() {
    throw new UnsupportedOperationException("Method getLanguage is not yet implemented in " + getClass().getName());
  }

  @Override
  public @Nullable PsiElement getLastChild() {
    throw new UnsupportedOperationException("Method getLastChild is not yet implemented in " + getClass().getName());
  }

  @Override
  public PsiManager getManager() {
    return myManager;
  }

  public void setManager(final PsiManager manager) {
    myManager = manager;
  }

  @Override
  public PsiElement getNavigationElement() {
    throw new UnsupportedOperationException("Method getNavigationElement is not yet implemented in " + getClass().getName());
  }

  @Override
  public @Nullable PsiElement getNextSibling() {
    throw new UnsupportedOperationException("Method getNextSibling is not yet implemented in " + getClass().getName());
  }

  @Override
  public @Nullable ASTNode getNode() {
    throw new UnsupportedOperationException("Method getNode is not yet implemented in " + getClass().getName());
  }

  @Override
  public PsiElement getOriginalElement() {
    throw new UnsupportedOperationException("Method getOriginalElement is not yet implemented in " + getClass().getName());
  }

  public void setParent(PsiElement parent) {
    myParent = Ref.create(parent);
  }

  @Override
  public PsiElement getParent() {
    if (myParent == null) {
      throw new UnsupportedOperationException("Method getParent is not yet implemented in " + getClass().getName());
    }
    return myParent.get();
  }

  @Override
  public @Nullable PsiElement getPrevSibling() {
    throw new UnsupportedOperationException("Method getPrevSibling is not yet implemented in " + getClass().getName());
  }

  @Override
  public @NotNull Project getProject() {
    return myProject;
  }

  @Override
  public @Nullable PsiReference getReference() {
    throw new UnsupportedOperationException("Method getReference is not yet implemented in " + getClass().getName());
  }

  @Override
  public PsiReference @NotNull [] getReferences() {
    throw new UnsupportedOperationException("Method getReferences is not yet implemented in " + getClass().getName());
  }

  @Override
  public @NotNull GlobalSearchScope getResolveScope() {
    throw new UnsupportedOperationException("Method getResolveScope is not yet implemented in " + getClass().getName());
  }

  @Override
  public int getStartOffsetInParent() {
    throw new UnsupportedOperationException("Method getStartOffsetInParent is not yet implemented in " + getClass().getName());
  }

  @Override
  public @NonNls String getText() {
    return myText;
  }

  @Override
  public int getTextLength() {
    return myText.length();
  }

  @Override
  public int getTextOffset() {
    return getTextRange().getStartOffset();
  }

  @Override
  public TextRange getTextRange() {
    throw new UnsupportedOperationException("Method getTextRange is not yet implemented in " + getClass().getName());
  }

  @Override
  public @NotNull SearchScope getUseScope() {
    throw new UnsupportedOperationException("Method getUseScope is not yet implemented in " + getClass().getName());
  }

  @Override
  public boolean isPhysical() {
    return false;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public boolean isWritable() {
    throw new UnsupportedOperationException("Method isWritable is not yet implemented in " + getClass().getName());
  }

  protected @Nullable <T extends PsiNamedElement> T findDeclaration(String name, Class<T> aClass) {
    for (final PsiElement declaration : myDeclarations) {
      if (declaration instanceof PsiNamedElement psiNamedElement) {
        if (name.equals(psiNamedElement.getName()) && aClass.isInstance(psiNamedElement)) {
          return (T)psiNamedElement;
        }
      }
    }
    return null;
  }

  public void addDeclaration(PsiElement declaration) {
    if (myDeclarations.contains(declaration)) {
      myDeclarations.remove(declaration);
    }
    else if (declaration instanceof PsiNamedElement) {
      myDeclarations.remove(findDeclaration(((PsiNamedElement)declaration).getName(), ((PsiNamedElement)declaration).getClass()));
    }
    myDeclarations.add(declaration);
  }

  @Override
  public boolean processDeclarations(final @NotNull PsiScopeProcessor processor,
                                     final @NotNull ResolveState state, final PsiElement lastParent, final @NotNull PsiElement place) {
    for (final PsiElement declaration : myDeclarations) {
      if (!processor.execute(declaration, state)) return false;
    }

    return true;
  }

  @Override
  public <T> void putCopyableUserData(final @NotNull Key<T> key, final T value) {
    throw new UnsupportedOperationException("Method putCopyableUserData is not yet implemented in " + getClass().getName());
  }

  @Override
  public PsiElement replace(final @NotNull PsiElement newElement) throws IncorrectOperationException {
    throw new UnsupportedOperationException("Method replace is not yet implemented in " + getClass().getName());
  }

  @Override
  public boolean textContains(final char c) {
    throw new UnsupportedOperationException("Method textContains is not yet implemented in " + getClass().getName());
  }

  @Override
  public boolean textMatches(final @NotNull PsiElement element) {
    throw new UnsupportedOperationException("Method textMatches is not yet implemented in " + getClass().getName());
  }

  @Override
  public boolean textMatches(final @NotNull CharSequence text) {
    throw new UnsupportedOperationException("Method textMatches is not yet implemented in " + getClass().getName());
  }

  @Override
  public char @NotNull [] textToCharArray() {
    throw new UnsupportedOperationException("Method textToCharArray is not yet implemented in " + getClass().getName());
  }

  @Override
  public Icon getIcon(final int flags) {
    return null;
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {
    return this == another;
  }  
}
