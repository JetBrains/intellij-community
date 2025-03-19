// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml.codeInsight.intentions;

import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlAttributeDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.JavaFXBundle;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxBuiltInAttributeDescriptor;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxPropertyAttributeDescriptor;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxStaticSetterAttributeDescriptor;

public final class JavaFxExpandAttributeIntention extends PsiElementBaseIntentionAction{
  private static final Logger LOG = Logger.getInstance(JavaFxExpandAttributeIntention.class);

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    final XmlAttribute attr = (XmlAttribute)element.getParent();
    final String name = attr.getName();
    final XmlAttributeDescriptor descriptor = attr.getDescriptor();
    LOG.assertTrue(descriptor != null);
    String value = attr.getValue();
    final PsiElement declaration = descriptor.getDeclaration();
    if (declaration instanceof PsiMember) {
      final PsiType propertyType = PropertyUtilBase.getPropertyType((PsiMember)declaration);
      final PsiType itemType = JavaGenericsUtil.getCollectionItemType(propertyType, declaration.getResolveScope());
      if (itemType != null) {
        final String typeNode = itemType.getPresentableText();
        JavaFxPsiUtil.insertImportWhenNeeded((XmlFile)attr.getContainingFile(), typeNode, itemType.getCanonicalText());
        final String[] vals = value != null ? value.split(",") : ArrayUtilRt.EMPTY_STRING_ARRAY;
        value = StringUtil.join(vals, s -> "<" + typeNode + " " + FxmlConstants.FX_VALUE + "=\"" + s.trim() + "\"/>", "\n");
      }
    }
    final XmlTag childTag = XmlElementFactory.getInstance(project).createTagFromText("<" + name + ">" + value + "</" + name + ">");
    attr.getParent().add(childTag);
    attr.delete();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (element instanceof XmlToken && ((XmlToken)element).getTokenType() == XmlTokenType.XML_NAME) {
      final PsiElement parent = element.getParent();
      if (parent instanceof XmlAttribute) {
        final XmlAttributeDescriptor descriptor = ((XmlAttribute)parent).getDescriptor();
        if (descriptor instanceof JavaFxPropertyAttributeDescriptor && !(descriptor instanceof JavaFxBuiltInAttributeDescriptor)) {

          PsiType tagType = null;
          final PsiElement declaration = descriptor.getDeclaration();
          if (declaration instanceof PsiMember) {
            tagType = PropertyUtilBase.getPropertyType((PsiMember)declaration);
          }
          PsiClass tagClass = PsiUtil.resolveClassInType(tagType instanceof PsiPrimitiveType ? ((PsiPrimitiveType)tagType).getBoxedType(parent) : tagType);
          if ((tagClass != null && JavaFxPsiUtil.isAbleToInstantiate(tagClass)) || descriptor instanceof JavaFxStaticSetterAttributeDescriptor) {
            setText(JavaFXBundle.message("javafx.expand.attribute.to.tag.intention", ((XmlAttribute)parent).getName()));
            return true;
          }
        }
      }
    }
    return false;
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaFXBundle.message("javafx.expand.attribute.to.tag.intention.family.name");
  }
}
