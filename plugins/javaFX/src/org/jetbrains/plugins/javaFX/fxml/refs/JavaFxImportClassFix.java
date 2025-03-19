// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml.refs;

import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFixBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

abstract class JavaFxImportClassFix extends ImportClassFixBase<XmlTag, JavaFxTagNameReference> {

  JavaFxImportClassFix(@NotNull JavaFxTagNameReference ref, @NotNull XmlTag element) {
    super(element, ref);
  }

  abstract XmlTag getTagElement(@NotNull JavaFxTagNameReference ref);

  @Override
  protected @Nullable String getReferenceName(@NotNull JavaFxTagNameReference reference) {
    final XmlTag tagElement = getTagElement(reference);
    return tagElement != null ? tagElement.getName() : null;
  }

  @Override
  protected PsiElement getReferenceNameElement(@NotNull JavaFxTagNameReference reference) {
    final XmlTag tagElement = getTagElement(reference);
    return tagElement != null ? tagElement.getNavigationElement() : null;
  }

  @Override
  protected void bindReference(@NotNull PsiReference reference, @NotNull PsiClass targetClass) {
    final PsiFile file = reference.getElement().getContainingFile();
    super.bindReference(reference, targetClass);
    final String qualifiedName = targetClass.getQualifiedName();
    if (qualifiedName != null) {
      final String shortName = StringUtil.getShortName(qualifiedName);
      JavaFxPsiUtil.insertImportWhenNeeded((XmlFile)file, shortName, qualifiedName);
    }
  }

  @Override
  protected boolean hasTypeParameters(@NotNull JavaFxTagNameReference reference) {
    return false;
  }

  @Override
  protected boolean isAccessible(@NotNull PsiMember member, @NotNull XmlTag referenceElement) {
    return member instanceof PsiClass && JavaFxPsiUtil.isClassAcceptable(referenceElement.getParentTag(), (PsiClass)member);
  }

  @Override
  protected String getQualifiedName(@NotNull XmlTag referenceElement) {
    final XmlElementDescriptor descriptor = referenceElement.getDescriptor();
    return descriptor != null ? descriptor.getQualifiedName() : referenceElement.getName();
  }

  @Override
  protected boolean isQualified(@NotNull JavaFxTagNameReference reference) {
    return false;
  }

  @Override
  protected boolean hasUnresolvedImportWhichCanImport(@NotNull PsiFile psiFile, @NotNull String name) {
    return false;   //todo
  }

  @Override
  protected int getStartOffset(@NotNull XmlTag element, @NotNull JavaFxTagNameReference ref) {
    return element.getTextOffset() + ref.getRangeInElement().getStartOffset();
  }

  @Override
  protected int getEndOffset(@NotNull XmlTag element, @NotNull JavaFxTagNameReference ref) {
    return element.getTextOffset() + ref.getRangeInElement().getEndOffset();
  }
}
