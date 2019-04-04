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

import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlAttributeDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxBuiltInAttributeDescriptor;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxPropertyAttributeDescriptor;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxStaticSetterAttributeDescriptor;

public class JavaFxExpandAttributeIntention extends PsiElementBaseIntentionAction{
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
        final String[] vals = value != null ? value.split(",") : ArrayUtil.EMPTY_STRING_ARRAY;
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
            setText("Expand '" + ((XmlAttribute)parent).getName() + "' to tag");
            return true;
          }
        }
      }
    }
    return false;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Expand attribute to tag";
  }
}
