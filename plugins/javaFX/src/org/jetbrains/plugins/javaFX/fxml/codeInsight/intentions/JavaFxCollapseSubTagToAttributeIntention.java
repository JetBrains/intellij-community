/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.javaFX.fxml.codeInsight.intentions;

import com.intellij.codeInsight.FileModificationService;
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
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxClassBackedElementDescriptor;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxPropertyElementDescriptor;

/**
 * User: anna
 * Date: 2/22/13
 */
public class JavaFxCollapseSubTagToAttributeIntention extends PsiElementBaseIntentionAction{
  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(element)) return;
    final XmlTag tag = (XmlTag)element.getParent();
    final String value;
    if (tag.getSubTags().length == 0) {
      value = tag.getValue().getText().trim();
    }
    else {
      value = StringUtil.join(tag.getSubTags(), new Function<XmlTag, String>() {
        @Override
        public String fun(XmlTag childTag) {
          final XmlAttribute valueAttr = childTag.getAttribute(FxmlConstants.FX_VALUE);
          if (valueAttr != null) {
            return valueAttr.getValue();
          }
          return "";
        }
      }, ", ");
    }
    final XmlAttribute attribute = XmlElementFactory.getInstance(project).createXmlAttribute(tag.getName(), value);
    final XmlTag parentTag = tag.getParentTag();
    parentTag.add(attribute);
    tag.delete();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (element instanceof XmlToken && ((XmlToken)element).getTokenType() == XmlTokenType.XML_NAME && element.getParent() instanceof XmlTag) {
      final XmlTag tag = (XmlTag)element.getParent();
      for (XmlTag xmlTag : tag.getSubTags()) {
        if (xmlTag.getAttribute(FxmlConstants.FX_VALUE) == null) return false;
      }
      final XmlTag parentTag = tag.getParentTag();
      if (parentTag != null &&
          tag.getDescriptor() instanceof JavaFxPropertyElementDescriptor &&
          parentTag.getDescriptor() instanceof JavaFxClassBackedElementDescriptor) {

        setText("Collapse tag '" + tag.getName() + "' to attribute");
        return true;
      }
    }
    return false;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Collapse tag to attribute";
  }
}
