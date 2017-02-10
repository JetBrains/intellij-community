/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

/**
 * @author peter
 */
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
  public void accept(@NotNull final PsiElementVisitor visitor) {
    throw new UnsupportedOperationException("Method accept is not yet implemented in " + getClass().getName());
  }

  @Override
  public void acceptChildren(@NotNull final PsiElementVisitor visitor) {
    throw new UnsupportedOperationException("Method acceptChildren is not yet implemented in " + getClass().getName());
  }

  @Override
  public PsiElement add(@NotNull final PsiElement element) throws IncorrectOperationException {
    throw new UnsupportedOperationException("Method add is not yet implemented in " + getClass().getName());
  }

  @Override
  public PsiElement addAfter(@NotNull final PsiElement element, final PsiElement anchor) throws IncorrectOperationException {
    throw new UnsupportedOperationException("Method addAfter is not yet implemented in " + getClass().getName());
  }

  @Override
  public PsiElement addBefore(@NotNull final PsiElement element, final PsiElement anchor) throws IncorrectOperationException {
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
  public PsiElement addRangeBefore(@NotNull final PsiElement first, @NotNull final PsiElement last, final PsiElement anchor) throws
                                                                                                                             IncorrectOperationException {
    throw new UnsupportedOperationException("Method addRangeBefore is not yet implemented in " + getClass().getName());
  }

  @Override
  public void checkAdd(@NotNull final PsiElement element) throws IncorrectOperationException {
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
  @Nullable
  public PsiElement findElementAt(final int offset) {
    throw new UnsupportedOperationException("Method findElementAt is not yet implemented in " + getClass().getName());
  }

  @Override
  @Nullable
  public PsiReference findReferenceAt(final int offset) {
    throw new UnsupportedOperationException("Method findReferenceAt is not yet implemented in " + getClass().getName());
  }

  @Override
  @NotNull
  public PsiElement[] getChildren() {
    throw new UnsupportedOperationException("Method getChildren is not yet implemented in " + getClass().getName());
  }

  @Override
  public PsiFile getContainingFile() throws PsiInvalidElementAccessException {
    throw new UnsupportedOperationException("Method getContainingFile is not yet implemented in " + getClass().getName());
  }

  @Override
  @Nullable
  public PsiElement getContext() {
    return getParent();
  }

  @Override
  @Nullable
  public <T> T getCopyableUserData(@NotNull final Key<T> key) {
    throw new UnsupportedOperationException("Method getCopyableUserData is not yet implemented in " + getClass().getName());
  }

  @Override
  @Nullable
  public PsiElement getFirstChild() {
    throw new UnsupportedOperationException("Method getFirstChild is not yet implemented in " + getClass().getName());
  }

  @Override
  @NotNull
  public Language getLanguage() {
    throw new UnsupportedOperationException("Method getLanguage is not yet implemented in " + getClass().getName());
  }

  @Override
  @Nullable
  public PsiElement getLastChild() {
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
  @Nullable
  public PsiElement getNextSibling() {
    throw new UnsupportedOperationException("Method getNextSibling is not yet implemented in " + getClass().getName());
  }

  @Override
  @Nullable
  public ASTNode getNode() {
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
  @Nullable
  public PsiElement getPrevSibling() {
    throw new UnsupportedOperationException("Method getPrevSibling is not yet implemented in " + getClass().getName());
  }

  @Override
  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Override
  @Nullable
  public PsiReference getReference() {
    throw new UnsupportedOperationException("Method getReference is not yet implemented in " + getClass().getName());
  }

  @Override
  @NotNull
  public PsiReference[] getReferences() {
    throw new UnsupportedOperationException("Method getReferences is not yet implemented in " + getClass().getName());
  }

  @Override
  @NotNull
  public GlobalSearchScope getResolveScope() {
    throw new UnsupportedOperationException("Method getResolveScope is not yet implemented in " + getClass().getName());
  }

  @Override
  public int getStartOffsetInParent() {
    throw new UnsupportedOperationException("Method getStartOffsetInParent is not yet implemented in " + getClass().getName());
  }

  @Override
  @NonNls
  public String getText() {
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
  @NotNull
  public SearchScope getUseScope() {
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

  @Nullable
  protected <T extends PsiNamedElement> T findDeclaration(String name, Class<T> aClass) {
    for (final PsiElement declaration : myDeclarations) {
      if (declaration instanceof PsiNamedElement) {
        final PsiNamedElement psiNamedElement = (PsiNamedElement)declaration;
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
  public boolean processDeclarations(@NotNull final PsiScopeProcessor processor,
                                     @NotNull final ResolveState state, final PsiElement lastParent, @NotNull final PsiElement place) {
    for (final PsiElement declaration : myDeclarations) {
      if (!processor.execute(declaration, state)) return false;
    }

    return true;
  }

  @Override
  public <T> void putCopyableUserData(@NotNull final Key<T> key, final T value) {
    throw new UnsupportedOperationException("Method putCopyableUserData is not yet implemented in " + getClass().getName());
  }

  @Override
  public PsiElement replace(@NotNull final PsiElement newElement) throws IncorrectOperationException {
    throw new UnsupportedOperationException("Method replace is not yet implemented in " + getClass().getName());
  }

  @Override
  public boolean textContains(final char c) {
    throw new UnsupportedOperationException("Method textContains is not yet implemented in " + getClass().getName());
  }

  @Override
  public boolean textMatches(@NotNull final PsiElement element) {
    throw new UnsupportedOperationException("Method textMatches is not yet implemented in " + getClass().getName());
  }

  @Override
  public boolean textMatches(@NotNull final CharSequence text) {
    throw new UnsupportedOperationException("Method textMatches is not yet implemented in " + getClass().getName());
  }

  @Override
  @NotNull
  public char[] textToCharArray() {
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
