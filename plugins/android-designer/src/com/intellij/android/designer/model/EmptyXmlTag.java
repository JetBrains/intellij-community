/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.android.designer.model;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagChild;
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

/**
 * @author Alexander Lobas
 */
public class EmptyXmlTag implements XmlTag {
  public static XmlTag INSTANCE = new EmptyXmlTag();

  @NotNull
  @Override
  public String getName() {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    return null;  // TODO: Auto-generated method stub
  }

  @NotNull
  @Override
  public String getNamespace() {
    return null;  // TODO: Auto-generated method stub
  }

  @NotNull
  @Override
  public String getLocalName() {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public XmlElementDescriptor getDescriptor() {
    return null;  // TODO: Auto-generated method stub
  }

  @NotNull
  @Override
  public XmlAttribute[] getAttributes() {
    return new XmlAttribute[0];  // TODO: Auto-generated method stub
  }

  @Override
  public XmlAttribute getAttribute(@NonNls String name, @NonNls String namespace) {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public XmlAttribute getAttribute(@NonNls String qname) {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public String getAttributeValue(@NonNls String name, @NonNls String namespace) {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public String getAttributeValue(@NonNls String qname) {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public XmlAttribute setAttribute(@NonNls String name, @NonNls String namespace, @NonNls String value) throws IncorrectOperationException {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public XmlAttribute setAttribute(@NonNls String qname, @NonNls String value) throws IncorrectOperationException {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public XmlTag createChildTag(@NonNls String localName,
                               @NonNls String namespace,
                               @Nullable @NonNls String bodyText,
                               boolean enforceNamespacesDeep) {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public XmlTag addSubTag(XmlTag subTag, boolean first) {
    return null;  // TODO: Auto-generated method stub
  }

  @NotNull
  @Override
  public XmlTag[] getSubTags() {
    return new XmlTag[0];  // TODO: Auto-generated method stub
  }

  @NotNull
  @Override
  public XmlTag[] findSubTags(@NonNls String qname) {
    return new XmlTag[0];  // TODO: Auto-generated method stub
  }

  @NotNull
  @Override
  public XmlTag[] findSubTags(@NonNls String localName, @NonNls String namespace) {
    return new XmlTag[0];  // TODO: Auto-generated method stub
  }

  @Override
  public XmlTag findFirstSubTag(@NonNls String qname) {
    return null;  // TODO: Auto-generated method stub
  }

  @NotNull
  @Override
  public String getNamespacePrefix() {
    return null;  // TODO: Auto-generated method stub
  }

  @NotNull
  @Override
  public String getNamespaceByPrefix(@NonNls String prefix) {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public String getPrefixByNamespace(@NonNls String namespace) {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public String[] knownNamespaces() {
    return new String[0];  // TODO: Auto-generated method stub
  }

  @Override
  public boolean hasNamespaceDeclarations() {
    return false;  // TODO: Auto-generated method stub
  }

  @NotNull
  @Override
  public Map<String, String> getLocalNamespaceDeclarations() {
    return null;  // TODO: Auto-generated method stub
  }

  @NotNull
  @Override
  public XmlTagValue getValue() {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public XmlNSDescriptor getNSDescriptor(@NonNls String namespace, boolean strict) {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public boolean isEmpty() {
    return false;  // TODO: Auto-generated method stub
  }

  @Override
  public void collapseIfEmpty() {
    // TODO: Auto-generated method stub
  }

  @Override
  public String getSubTagText(@NonNls String qname) {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public XmlTag getParentTag() {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public XmlTagChild getNextSiblingInTag() {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public XmlTagChild getPrevSiblingInTag() {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public boolean processElements(PsiElementProcessor processor, PsiElement place) {
    return false;  // TODO: Auto-generated method stub
  }

  @NotNull
  @Override
  public Project getProject() throws PsiInvalidElementAccessException {
    return null;  // TODO: Auto-generated method stub
  }

  @NotNull
  @Override
  public Language getLanguage() {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public PsiManager getManager() {
    return null;  // TODO: Auto-generated method stub
  }

  @NotNull
  @Override
  public PsiElement[] getChildren() {
    return new PsiElement[0];  // TODO: Auto-generated method stub
  }

  @Override
  public PsiElement getParent() {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public PsiElement getFirstChild() {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public PsiElement getLastChild() {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public PsiElement getNextSibling() {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public PsiElement getPrevSibling() {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public PsiFile getContainingFile() throws PsiInvalidElementAccessException {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public TextRange getTextRange() {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public int getStartOffsetInParent() {
    return 0;  // TODO: Auto-generated method stub
  }

  @Override
  public int getTextLength() {
    return 0;  // TODO: Auto-generated method stub
  }

  @Override
  public PsiElement findElementAt(int offset) {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public PsiReference findReferenceAt(int offset) {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public int getTextOffset() {
    return 0;  // TODO: Auto-generated method stub
  }

  @Override
  public String getText() {
    return null;  // TODO: Auto-generated method stub
  }

  @NotNull
  @Override
  public char[] textToCharArray() {
    return new char[0];  // TODO: Auto-generated method stub
  }

  @Override
  public PsiElement getNavigationElement() {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public PsiElement getOriginalElement() {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public boolean textMatches(@NotNull @NonNls CharSequence text) {
    return false;  // TODO: Auto-generated method stub
  }

  @Override
  public boolean textMatches(@NotNull PsiElement element) {
    return false;  // TODO: Auto-generated method stub
  }

  @Override
  public boolean textContains(char c) {
    return false;  // TODO: Auto-generated method stub
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    // TODO: Auto-generated method stub
  }

  @Override
  public void acceptChildren(@NotNull PsiElementVisitor visitor) {
    // TODO: Auto-generated method stub
  }

  @Override
  public PsiElement copy() {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public PsiElement addBefore(@NotNull PsiElement element, @Nullable PsiElement anchor) throws IncorrectOperationException {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public PsiElement addAfter(@NotNull PsiElement element, @Nullable PsiElement anchor) throws IncorrectOperationException {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException {
    // TODO: Auto-generated method stub
  }

  @Override
  public PsiElement addRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public PsiElement addRangeBefore(@NotNull PsiElement first, @NotNull PsiElement last, PsiElement anchor)
    throws IncorrectOperationException {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor) throws IncorrectOperationException {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public void delete() throws IncorrectOperationException {
    // TODO: Auto-generated method stub
  }

  @Override
  public void checkDelete() throws IncorrectOperationException {
    // TODO: Auto-generated method stub
  }

  @Override
  public void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    // TODO: Auto-generated method stub
  }

  @Override
  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public boolean isValid() {
    return false;  // TODO: Auto-generated method stub
  }

  @Override
  public boolean isWritable() {
    return false;  // TODO: Auto-generated method stub
  }

  @Override
  public PsiReference getReference() {
    return null;  // TODO: Auto-generated method stub
  }

  @NotNull
  @Override
  public PsiReference[] getReferences() {
    return new PsiReference[0];  // TODO: Auto-generated method stub
  }

  @Override
  public <T> T getCopyableUserData(Key<T> key) {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public <T> void putCopyableUserData(Key<T> key, @Nullable T value) {
    // TODO: Auto-generated method stub
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @NotNull PsiElement place) {
    return false;  // TODO: Auto-generated method stub
  }

  @Override
  public PsiElement getContext() {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public boolean isPhysical() {
    return false;  // TODO: Auto-generated method stub
  }

  @NotNull
  @Override
  public GlobalSearchScope getResolveScope() {
    return null;  // TODO: Auto-generated method stub
  }

  @NotNull
  @Override
  public SearchScope getUseScope() {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public ASTNode getNode() {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return false;  // TODO: Auto-generated method stub
  }

  @Override
  public Icon getIcon(@IconFlags int flags) {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public PsiMetaData getMetaData() {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    // TODO: Auto-generated method stub
  }
}