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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlElementsGroup;
import com.intellij.xml.XmlNSDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: anna
 */
public class JavaFxDefaultPropertyElementDescriptor implements XmlElementDescriptor{
  private final String myName;
  private final XmlTag myXmlTag;

  public JavaFxDefaultPropertyElementDescriptor(String name, XmlTag tag) {
    myName = name;
    myXmlTag = tag;
  }

  @Override
  public String getQualifiedName() {
    return getName();
  }

  @Override
  public String getDefaultName() {
    return getName();
  }

  @Override
  public XmlElementDescriptor[] getElementsDescriptors(XmlTag context) {
    return XmlElementDescriptor.EMPTY_ARRAY;
  }

  @Nullable
  @Override
  public XmlElementDescriptor getElementDescriptor(XmlTag childTag, XmlTag contextTag) {
    if (myName.equals(FxmlConstants.FX_DEFINE) || myName.equals(FxmlConstants.FX_ROOT)) {
      final String name = childTag.getName();
      if (JavaFxPsiUtil.isClassTag(name)) {
        return new JavaFxClassBackedElementDescriptor(name, childTag);
      }
    }
    return null;
  }

  @Override
  public XmlAttributeDescriptor[] getAttributesDescriptors(@Nullable XmlTag context) {
    final List<String> defaultAttributeList = FxmlConstants.FX_ELEMENT_ATTRIBUTES.get(getName());
    if (defaultAttributeList != null) {
      final List<XmlAttributeDescriptor> descriptors = new ArrayList<XmlAttributeDescriptor>();
      for (String defaultAttrName : defaultAttributeList) {
        descriptors.add(new JavaFxDefaultAttributeDescriptor(defaultAttrName, getName()));
      }
      JavaFxClassBackedElementDescriptor.collectStaticAttributesDescriptors(context, descriptors);
      final XmlTag referencedTag = getReferencedTag(getName(), myXmlTag);
      if (referencedTag != null) {
        final XmlElementDescriptor referencedDescriptor = referencedTag.getDescriptor();
        if (referencedDescriptor != null) {
          final XmlAttributeDescriptor[] attributesDescriptors = referencedDescriptor.getAttributesDescriptors(referencedTag);
          if (attributesDescriptors != null) {
            Collections.addAll(descriptors, attributesDescriptors);
          }
        }
      }
      return descriptors.toArray(new XmlAttributeDescriptor[descriptors.size()]);
    }
    return XmlAttributeDescriptor.EMPTY;
  }

  @Nullable
  private static XmlTag getReferencedTag(String name, XmlTag tag) {
    if (name.equals(FxmlConstants.FX_REFERENCE)) {
      final XmlAttribute attribute = tag.getAttribute(FxmlConstants.FX_ELEMENT_SOURCE);
      if (attribute != null) {
        final XmlAttributeValue valueElement = attribute.getValueElement();
        if (valueElement != null) {
          final PsiReference reference = valueElement.getReference();
          if (reference != null) {
            final PsiElement resolve = reference.resolve();
            if (resolve instanceof XmlAttributeValue) {
              return PsiTreeUtil.getParentOfType(resolve, XmlTag.class);
            }
          }
        }
      }
    }
    return null;
  }
  
  @Nullable
  @Override
  public XmlAttributeDescriptor getAttributeDescriptor(@NonNls String attributeName, @Nullable XmlTag context) {
    final List<String> defaultAttributeList = FxmlConstants.FX_ELEMENT_ATTRIBUTES.get(getName());
    if (defaultAttributeList != null) {
      if (defaultAttributeList.contains(attributeName)) {
        return new JavaFxDefaultAttributeDescriptor(attributeName, getName());
      }
      final PsiMethod propertySetter = JavaFxPsiUtil.findPropertySetter(attributeName, context);
      if (propertySetter != null) {
        return new JavaFxStaticPropertyAttributeDescriptor(propertySetter, attributeName);
      }
      final XmlTag referencedTag = getReferencedTag(getName(), myXmlTag);
      if (referencedTag != null) {
        final XmlElementDescriptor referencedDescriptor = referencedTag.getDescriptor();
        if (referencedDescriptor != null) {
          return referencedDescriptor.getAttributeDescriptor(attributeName, referencedTag);
        }
      }
    }
    return null;
  }

  @Nullable
  @Override
  public XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute attribute) {
    return getAttributeDescriptor(attribute.getName(), attribute.getParent());
  }

  @Override
  public XmlNSDescriptor getNSDescriptor() {
    return null;
  }

  @Nullable
  @Override
  public XmlElementsGroup getTopGroup() {
    return null;
  }

  @Override
  public int getContentType() {
    return CONTENT_TYPE_UNKNOWN;
  }

  @Nullable
  @Override
  public String getDefaultValue() {
    return null;
  }

  @Override
  public PsiElement getDeclaration() {
    return myXmlTag;
  }

  @Override
  public String getName(PsiElement context) {
    return getName();
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public void init(PsiElement element) {
  }

  @Override
  public Object[] getDependences() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }
}
