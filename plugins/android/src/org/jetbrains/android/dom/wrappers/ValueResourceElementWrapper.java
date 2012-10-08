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

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.navigation.PsiElementNavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 6, 2009
 * Time: 7:08:02 PM
 * To change this template use File | Settings | File Templates.
 */
public class ValueResourceElementWrapper implements XmlAttributeValue, ResourceElementWrapper, PsiNamedElement, PsiElementNavigationItem {
  private final XmlAttributeValue myWrappee;
  private final String myFileName;
  private final String myDirName;

  public ValueResourceElementWrapper(@NotNull XmlAttributeValue wrappeeElement) {
    if (!(wrappeeElement instanceof NavigationItem)) {
      throw new IllegalArgumentException();
    }
    if (!(wrappeeElement instanceof PsiMetaOwner)) {
      throw new IllegalArgumentException();
    }
    myWrappee = wrappeeElement;
    final PsiFile file = getContainingFile();
    myFileName = file != null ? file.getName() : null;
    final PsiDirectory dir = file != null ? file.getContainingDirectory() : null;
    myDirName = dir != null ? dir.getName() : null;
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

  public PsiElement getParent() {
    return myWrappee.getParent();
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
  public ASTNode getNode() {
    return myWrappee.getNode();
  }

  @NonNls
  public String toString() {
    return myWrappee.toString();
  }

  public boolean isEquivalentTo(PsiElement another) {
    if (another instanceof ResourceElementWrapper) {
      another = ((ResourceElementWrapper)another).getWrappee();
    }
    return myWrappee == another || myWrappee.isEquivalentTo(another);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ValueResourceElementWrapper that = (ValueResourceElementWrapper)o;

    if (!myWrappee.equals(that.myWrappee)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myWrappee.hashCode();
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

  public String getName() {
    String value = myWrappee.getValue();
    if (value.startsWith(AndroidResourceUtil.NEW_ID_PREFIX)) {
      return AndroidResourceUtil.getResourceNameByReferenceText(value);
    }
    return ((NavigationItem)myWrappee).getName();
  }

  @Nullable
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    if (AndroidResourceUtil.isIdDeclaration(myWrappee)) {
      XmlAttribute attribute = (XmlAttribute)myWrappee.getParent();
      attribute.setValue(name);
    }
    else {
      // then it is a value resource
      XmlTag tag = PsiTreeUtil.getParentOfType(myWrappee, XmlTag.class);
      DomElement domElement = DomManager.getDomManager(getProject()).getDomElement(tag);
      assert domElement instanceof ResourceElement;
      ResourceElement resElement = (ResourceElement)domElement;
      resElement.getName().setValue(name);
    }
    return null;
  }

  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      @Nullable
      public String getPresentableText() {
        String name = ((NavigationItem)myWrappee).getName();
        if (myDirName == null || myFileName == null) {
          return name;
        }
        return name + " (..." + File.separatorChar + myDirName +
               File.separatorChar + myFileName + ')';
      }

      public String getLocationString() {
        return null;
      }

      public Icon getIcon(boolean open) {
        return null;
      }
    };
  }

  public void navigate(boolean requestFocus) {
    ((NavigationItem)myWrappee).navigate(requestFocus);
  }

  public boolean canNavigate() {
    return ((NavigationItem)myWrappee).canNavigate();
  }

  public boolean canNavigateToSource() {
    return ((NavigationItem)myWrappee).canNavigateToSource();
  }

  public String getValue() {
    return myWrappee.getValue();
  }

  public TextRange getValueTextRange() {
    return myWrappee.getValueTextRange();
  }

  public boolean processElements(PsiElementProcessor processor, PsiElement place) {
    return myWrappee.processElements(processor, place);
  }

  @Override
  public PsiElement getTargetElement() {
    return getWrappee();
  }
}
