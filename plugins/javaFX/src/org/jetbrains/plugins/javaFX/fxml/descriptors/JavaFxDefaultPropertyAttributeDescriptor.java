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
package org.jetbrains.plugins.javaFX.fxml.descriptors;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

import java.util.List;

/**
 * User: anna
 * Date: 1/10/13
 */
public class JavaFxDefaultPropertyAttributeDescriptor extends JavaFxPropertyAttributeDescriptor {
  private static final Logger LOG = Logger.getInstance("#" + JavaFxDefaultPropertyAttributeDescriptor.class.getName());

  private String myDefaultPropertyName = null;
  public JavaFxDefaultPropertyAttributeDescriptor(String name, PsiClass psiClass) {
    super(name, psiClass);
  }

  public JavaFxDefaultPropertyAttributeDescriptor(String name, String defaultPropertyName) {
    super(name, null);
    myDefaultPropertyName = defaultPropertyName;
  }

  @Override
  public boolean hasIdType() {
    return getName().equals(FxmlConstants.FX_ID);
  }

  @Override
  public boolean isEnumerated() {
    return getName().equals("fx:constant");
  }

  @Override
  public boolean isRequired() {
    if (myDefaultPropertyName != null) {
      final List<String> requiredAttrs = FxmlConstants.FX_REQUIRED_ELEMENT_ATTRIBUTES.get(myDefaultPropertyName);
      if (requiredAttrs != null && requiredAttrs.contains(getName())) return true;
    }
    return false;
  }

  @Override
  protected PsiClass getEnum() {
    return isEnumerated() ? getPsiClass() : null ;
  }

  protected boolean isConstant(PsiField field) {
    return field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.FINAL) && field.hasModifierProperty(PsiModifier.PUBLIC);
  }

  @Nullable
  @Override
  public String validateValue(XmlElement context, String value) {
    if (context instanceof XmlAttributeValue) {
      final PsiElement parent = context.getParent();
      if (parent instanceof XmlAttribute) {
        final XmlAttribute attribute = (XmlAttribute)parent;
        final String attributeName = attribute.getName();
        if (FxmlConstants.FX_VALUE.equals(attributeName)) {
          final PsiClass tagClass = JavaFxPsiUtil.getTagClass((XmlAttributeValue)context);
          if (tagClass != null) {
            final PsiMethod method = JavaFxPsiUtil.findValueOfMethod(tagClass);
            if (method == null) {
              return "Unable to coerce '" + value + "' to " + tagClass.getQualifiedName() + ".";
            }
          }
        } else if (FxmlConstants.TYPE.equals(attributeName)) {
          final PsiReference[] references = context.getReferences();
          if (references.length == 0 || references[references.length - 1].resolve() == null) {
            return "Cannot resolve class " + value;
          }
        }
      }
    }
    return super.validateValue(context, value);
  }
}
