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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonClassNames;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxPropertyAttributeDescriptor;

/**
 * User: anna
 * Date: 1/16/13
 */
class JavaFxEventHandlerReferenceProvider extends JavaFxControllerBasedReferenceProvider {
  private static final Logger LOG = Logger.getInstance("#" + JavaFxEventHandlerReferenceProvider.class.getName());

  @Override
  protected PsiReference[] getReferencesByElement(@NotNull PsiClass controllerClass,
                                                  XmlAttributeValue xmlAttributeValue,
                                                  ProcessingContext context) {
    final String attValueString = xmlAttributeValue.getValue();
    LOG.assertTrue(attValueString.startsWith("#"));

    final XmlAttribute attribute = (XmlAttribute)xmlAttributeValue.getContext();
    if (attribute == null) return PsiReference.EMPTY_ARRAY;
    final String attributeName = attribute.getName();
    final XmlTag xmlTag = attribute.getParent();
    final XmlElementDescriptor descriptor = xmlTag.getDescriptor();
    if (descriptor == null) return PsiReference.EMPTY_ARRAY;
    final PsiElement currentTagClass = descriptor.getDeclaration();
    if (!(currentTagClass instanceof PsiClass)) return PsiReference.EMPTY_ARRAY;
    final PsiField handlerField = ((PsiClass)currentTagClass).findFieldByName(attributeName, true);
    if (handlerField == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    final PsiClass objectPropertyClass = JavaFxPropertyAttributeDescriptor.getPropertyClass(handlerField);
    if (objectPropertyClass == null || !InheritanceUtil.isInheritor(objectPropertyClass, JavaFxCommonClassNames.JAVAFX_EVENT_EVENT_HANDLER)) {
      return PsiReference.EMPTY_ARRAY;
    }
    final String eventHandlerName = attValueString.substring(1);
    final PsiMethod[] methods = controllerClass.findMethodsByName(eventHandlerName, true);

    PsiMethod handlerMethod = null;
    for (PsiMethod psiMethod : methods) {
      if (JavaFxEventHandlerReference.isHandlerMethod(psiMethod)) {
        handlerMethod = psiMethod;
        break;
      }
    }
    return new PsiReference[]{new JavaFxEventHandlerReference(xmlAttributeValue, handlerMethod, controllerClass)};
  }
}
