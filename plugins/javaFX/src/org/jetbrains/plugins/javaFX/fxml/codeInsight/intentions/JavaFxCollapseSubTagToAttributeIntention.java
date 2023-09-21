// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml.codeInsight.intentions;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.JavaFXBundle;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxClassTagDescriptorBase;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxPropertyTagDescriptor;

public final class JavaFxCollapseSubTagToAttributeIntention extends PsiElementBaseIntentionAction{
  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    final XmlTag tag = (XmlTag)element.getParent();
    final String value;
    if (tag.getSubTags().length == 0) {
      value = tag.getValue().getText().trim();
    }
    else {
      value = StringUtil.join(tag.getSubTags(), childTag -> {
        final XmlAttribute valueAttr = childTag.getAttribute(FxmlConstants.FX_VALUE);
        if (valueAttr != null) {
          return valueAttr.getValue();
        }
        return "";
      }, ", ");
    }
    final XmlAttribute attribute = XmlElementFactory.getInstance(project).createXmlAttribute(tag.getName(), value);
    final XmlTag parentTag = tag.getParentTag();
    if (parentTag != null) {
      parentTag.add(attribute);
      tag.delete();
    }
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (element instanceof XmlToken && ((XmlToken)element).getTokenType() == XmlTokenType.XML_NAME &&
        element.getParent() instanceof XmlTag tag) {
      for (XmlTag xmlTag : tag.getSubTags()) {
        if (xmlTag.getAttribute(FxmlConstants.FX_VALUE) == null) return false;
      }
      final XmlTag parentTag = tag.getParentTag();
      if (parentTag != null &&
          tag.getDescriptor() instanceof JavaFxPropertyTagDescriptor &&
          parentTag.getDescriptor() instanceof JavaFxClassTagDescriptorBase) {

        setText(JavaFXBundle.message("javafx.collapse.subtag.to.attribute.intention",tag.getName()));
        return true;
      }
    }
    return false;
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaFXBundle.message("javafx.collapse.subtag.to.attribute.intention.family.name");
  }
}
