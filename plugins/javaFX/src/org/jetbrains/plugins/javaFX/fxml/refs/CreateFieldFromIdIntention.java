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
package org.jetbrains.plugins.javaFX.fxml.refs;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypeInfoImpl;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFieldFromUsageFix;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFieldFromUsageHelper;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxClassBackedElementDescriptor;

/**
 * User: anna
 * Date: 3/20/13
 */
public class CreateFieldFromIdIntention extends PsiElementBaseIntentionAction {
  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    final XmlAttributeValue attrValue = PsiTreeUtil.getParentOfType(element, XmlAttributeValue.class);
    if (attrValue == null) return false;
    final PsiReference reference = attrValue.getReference();
    if (reference instanceof JavaFxFieldIdReferenceProvider.JavaFxControllerFieldRef && reference.resolve() == null) {
      final PsiClass fieldClass = checkContext(((JavaFxFieldIdReferenceProvider.JavaFxControllerFieldRef)reference).getXmlAttributeValue());
      if (fieldClass != null) {
        setText(QuickFixBundle.message("create.field.from.usage.text", reference.getCanonicalText()));
        return true;
      }
    }
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    final XmlAttributeValue attrValue = PsiTreeUtil.getParentOfType(element, XmlAttributeValue.class);
    assert attrValue != null;

    final JavaFxFieldIdReferenceProvider.JavaFxControllerFieldRef reference =
      (JavaFxFieldIdReferenceProvider.JavaFxControllerFieldRef)attrValue.getReference();
    assert reference != null;

    final PsiClass targetClass = reference.getAClass();
    if (!CodeInsightUtilBase.prepareFileForWrite(targetClass.getContainingFile())) {
      return;
    }
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiField field = factory.createField(reference.getCanonicalText(), PsiType.INT);
    VisibilityUtil.setVisibility(field.getModifierList(), PsiModifier.PUBLIC);

    field = CreateFieldFromUsageHelper.insertField(targetClass, field, element);

    final PsiClassType fieldType = factory.createType(checkContext(reference.getXmlAttributeValue()));
    final ExpectedTypeInfo[] types = {new ExpectedTypeInfoImpl(fieldType, ExpectedTypeInfo.TYPE_OR_SUBTYPE, 0, fieldType, TailType.NONE)};
    CreateFieldFromUsageFix.createFieldFromUsageTemplate(targetClass, project, types, field, false, element);
  }

  protected static PsiClass checkContext(final XmlAttributeValue attributeValue) {
    if (attributeValue == null) return null;
    final PsiElement parent = attributeValue.getParent();
    if (parent instanceof XmlAttribute) {
      final XmlTag tag = ((XmlAttribute)parent).getParent();
      if (tag != null) {
        final XmlElementDescriptor descriptor = tag.getDescriptor();
        if (descriptor instanceof JavaFxClassBackedElementDescriptor) {
          final PsiElement declaration = descriptor.getDeclaration();
          if (declaration instanceof PsiClass) {
            return (PsiClass)declaration;
          }
        }
      }
    }
    return null;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("create.field.from.usage.family");
  }
}