/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.android.dom.wrappers;

import com.intellij.lang.FileASTNode;
import com.intellij.lang.Language;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 7, 2009
 * Time: 10:06:22 PM
 * To change this template use File | Settings | File Templates.
 */
public class FileResourceElementWrapper implements PsiFile, ResourceElementWrapper {
  private final PsiFile myWrappee;
  private final PsiDirectory myResourceDir;

  public FileResourceElementWrapper(@NotNull PsiFile wrappeeElement) {
    myWrappee = wrappeeElement;
    myResourceDir = getContainingFile().getContainingDirectory();
  }

  public PsiElement getWrappee() {
    return myWrappee;
  }

  @NotNull
  public Project getProject() throws PsiInvalidElementAccessException {
    return myWrappee.getProject();
  }

  @NotNull
  public Language getLanguage() {
    return myWrappee.getLanguage();
  }

  public PsiManager getManager() {
    return myWrappee.getManager();
  }

  @NotNull
  public PsiElement[] getChildren() {
    return myWrappee.getChildren();
  }

  public VirtualFile getVirtualFile() {
    return myWrappee.getVirtualFile();
  }

  public PsiDirectory getContainingDirectory() {
    return myWrappee.getContainingDirectory();
  }

  public boolean isDirectory() {
    return myWrappee.isDirectory();
  }

  public PsiDirectory getParent() {
    return myWrappee.getParent();
  }

  public long getModificationStamp() {
    return myWrappee.getModificationStamp();
  }

  @NotNull
  public PsiFile getOriginalFile() {
    return myWrappee.getOriginalFile();
  }

  @NotNull
  public FileType getFileType() {
    return myWrappee.getFileType();
  }

  @NotNull
  public PsiFile[] getPsiRoots() {
    return myWrappee.getPsiRoots();
  }

  @NotNull
  public FileViewProvider getViewProvider() {
    return myWrappee.getViewProvider();
  }

  @Nullable
  public PsiElement getFirstChild() {
    return myWrappee.getFirstChild();
  }

  @Nullable
  public PsiElement getLastChild() {
    return myWrappee.getLastChild();
  }

  @Nullable
  public PsiElement getNextSibling() {
    return myWrappee.getNextSibling();
  }

  @Nullable
  public PsiElement getPrevSibling() {
    return myWrappee.getPrevSibling();
  }

  public PsiFile getContainingFile() throws PsiInvalidElementAccessException {
    return myWrappee.getContainingFile();
  }

  public TextRange getTextRange() {
    return myWrappee.getTextRange();
  }

  public int getStartOffsetInParent() {
    return myWrappee.getStartOffsetInParent();
  }

  public int getTextLength() {
    return myWrappee.getTextLength();
  }

  @Nullable
  public PsiElement findElementAt(int offset) {
    return myWrappee.findElementAt(offset);
  }

  @Nullable
  public PsiReference findReferenceAt(int offset) {
    return myWrappee.findReferenceAt(offset);
  }

  public int getTextOffset() {
    return myWrappee.getTextOffset();
  }

  @NonNls
  public String getText() {
    return myWrappee.getText();
  }

  @NotNull
  public char[] textToCharArray() {
    return myWrappee.textToCharArray();
  }

  public PsiElement getNavigationElement() {
    return myWrappee.getNavigationElement();
  }

  public PsiElement getOriginalElement() {
    return myWrappee.getOriginalElement();
  }

  public boolean textMatches(@NotNull @NonNls CharSequence text) {
    return myWrappee.textMatches(text);
  }

  public boolean textMatches(@NotNull PsiElement element) {
    return myWrappee.textMatches(element);
  }

  public boolean textContains(char c) {
    return myWrappee.textContains(c);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    myWrappee.accept(visitor);
  }

  public void acceptChildren(@NotNull PsiElementVisitor visitor) {
    myWrappee.acceptChildren(visitor);
  }

  public PsiElement copy() {
    return myWrappee.copy();
  }

  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    return myWrappee.add(element);
  }

  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    return myWrappee.addBefore(element, anchor);
  }

  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    return myWrappee.addAfter(element, anchor);
  }

  public void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException {
    myWrappee.checkAdd(element);
  }

  public PsiElement addRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    return myWrappee.addRange(first, last);
  }

  public PsiElement addRangeBefore(@NotNull PsiElement first, @NotNull PsiElement last, PsiElement anchor)
    throws IncorrectOperationException {
    return myWrappee.addRangeBefore(first, last, anchor);
  }

  public PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor) throws IncorrectOperationException {
    return myWrappee.addRangeAfter(first, last, anchor);
  }

  public void delete() throws IncorrectOperationException {
    myWrappee.delete();
  }

  public void checkDelete() throws IncorrectOperationException {
    myWrappee.checkDelete();
  }

  public void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    myWrappee.deleteChildRange(first, last);
  }

  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    return myWrappee.replace(newElement);
  }

  public boolean isValid() {
    return myWrappee.isValid();
  }

  public boolean isWritable() {
    return myWrappee.isWritable();
  }

  @Nullable
  public PsiReference getReference() {
    return myWrappee.getReference();
  }

  @NotNull
  public PsiReference[] getReferences() {
    return myWrappee.getReferences();
  }

  @Nullable
  public <T> T getCopyableUserData(Key<T> key) {
    return myWrappee.getCopyableUserData(key);
  }

  public <T> void putCopyableUserData(Key<T> key, T value) {
    myWrappee.putCopyableUserData(key, value);
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @NotNull PsiElement place) {
    return myWrappee.processDeclarations(processor, state, lastParent, place);
  }

  @Nullable
  public PsiElement getContext() {
    return myWrappee.getContext();
  }

  public boolean isPhysical() {
    return myWrappee.isPhysical();
  }

  @NotNull
  public GlobalSearchScope getResolveScope() {
    return myWrappee.getResolveScope();
  }

  @NotNull
  public SearchScope getUseScope() {
    return myWrappee.getUseScope();
  }

  @Nullable
  public FileASTNode getNode() {
    return myWrappee.getNode();
  }

  public void subtreeChanged() {
    myWrappee.subtreeChanged();
  }

  @NonNls
  public String toString() {
    return myWrappee.toString();
  }

  public boolean isEquivalentTo(PsiElement another) {
    if (another instanceof FileResourceElementWrapper) {
      another = ((FileResourceElementWrapper)another).getWrappee();
    }
    return myWrappee == another || myWrappee.isEquivalentTo(another);
  }

  public <T> T getUserData(@NotNull Key<T> key) {
    return myWrappee.getUserData(key);
  }

  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    myWrappee.putUserData(key, value);
  }

  public Icon getIcon(int flags) {
    return myWrappee.getIcon(flags);
  }

  @NotNull
  public String getName() {
    return myWrappee.getName();
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    return myWrappee.setName(name);
  }

  public boolean processChildren(PsiElementProcessor<PsiFileSystemItem> processor) {
    return myWrappee.processChildren(processor);
  }

  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      public String getPresentableText() {
        String name = myWrappee.getName();
        if (myResourceDir == null) {
          return name;
        }
        return name + " (" + myResourceDir.getName() + ')';
      }

      public String getLocationString() {
        return null;
      }

      public Icon getIcon(boolean open) {
        return null;
      }

      public TextAttributesKey getTextAttributesKey() {
        return null;
      }
    };
  }

  public FileStatus getFileStatus() {
    return myWrappee.getFileStatus();
  }

  public void navigate(boolean requestFocus) {
    myWrappee.navigate(requestFocus);
  }

  public boolean canNavigate() {
    return myWrappee.canNavigate();
  }

  public boolean canNavigateToSource() {
    return myWrappee.canNavigateToSource();
  }

  public void checkSetName(String name) throws IncorrectOperationException {
    myWrappee.checkSetName(name);
  }
}
