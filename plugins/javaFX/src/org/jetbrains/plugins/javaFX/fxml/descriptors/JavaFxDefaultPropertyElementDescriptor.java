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

import com.intellij.codeInsight.daemon.Validator;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlElementsGroup;
import com.intellij.xml.XmlNSDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: anna
 */
public class JavaFxDefaultPropertyElementDescriptor implements XmlElementDescriptor, Validator<XmlTag>{
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
    final JavaFxClassBackedElementDescriptor tagDescriptor = getFxRootTagDescriptor(context);
    if (tagDescriptor != null) {
      return tagDescriptor.getElementsDescriptors(context);
    }
    return XmlElementDescriptor.EMPTY_ARRAY;
  }

  @Nullable
  @Override
  public XmlElementDescriptor getElementDescriptor(XmlTag childTag, XmlTag contextTag) {
    final String name = childTag.getName();
    if (FxmlConstants.FX_DEFINE.equals(myName)) {
      if (FxmlConstants.FX_DEFAULT_ELEMENTS.contains(name)) {
        return new JavaFxDefaultPropertyElementDescriptor(name, childTag);
      }
      return new JavaFxClassBackedElementDescriptor(name, childTag);
    }

    if (FxmlConstants.FX_ROOT.equals(myName)) {
      final JavaFxClassBackedElementDescriptor tagDescriptor = getFxRootTagDescriptor(contextTag);
      if (tagDescriptor != null) {
        return tagDescriptor.getElementDescriptor(childTag, contextTag);
      }
      return new JavaFxClassBackedElementDescriptor(name, childTag);
    }
    return null;
  }

  @Override
  public XmlAttributeDescriptor[] getAttributesDescriptors(@Nullable XmlTag context) {
    final List<String> defaultAttributeList = FxmlConstants.FX_ELEMENT_ATTRIBUTES.get(getName());
    if (defaultAttributeList != null) {
      final List<XmlAttributeDescriptor> descriptors = new ArrayList<XmlAttributeDescriptor>();
      for (String defaultAttrName : defaultAttributeList) {
        descriptors.add(new JavaFxDefaultPropertyAttributeDescriptor(defaultAttrName, getName()));
      }
      JavaFxClassBackedElementDescriptor.collectStaticAttributesDescriptors(context, descriptors);

      final XmlTag referencedTag = getReferencedTag(myXmlTag);
      if (referencedTag != null) {
        final XmlElementDescriptor referencedDescriptor = referencedTag.getDescriptor();
        if (referencedDescriptor != null) {
          final XmlAttributeDescriptor[] attributesDescriptors = referencedDescriptor.getAttributesDescriptors(referencedTag);
          if (attributesDescriptors != null) {
            Collections.addAll(descriptors, attributesDescriptors);
          }
        }
      }

      final JavaFxClassBackedElementDescriptor rootTagDescriptor = getFxRootTagDescriptor(context);
      if (rootTagDescriptor != null) {
        Collections.addAll(descriptors, rootTagDescriptor.getAttributesDescriptors(context));
      }

      final XmlTag includedRoot = getIncludedRoot(context);
      if (includedRoot != null) {
        final XmlElementDescriptor includedRootDescriptor = includedRoot.getDescriptor();
        if (includedRootDescriptor instanceof JavaFxClassBackedElementDescriptor) {
          ((JavaFxClassBackedElementDescriptor)includedRootDescriptor).collectInstanceProperties(descriptors);
        }
        else if (includedRootDescriptor instanceof JavaFxDefaultPropertyElementDescriptor) {
          final JavaFxClassBackedElementDescriptor includedRootTagDescriptor = ((JavaFxDefaultPropertyElementDescriptor)includedRootDescriptor).getFxRootTagDescriptor(includedRoot);
          if (includedRootTagDescriptor != null) {
            includedRootTagDescriptor.collectInstanceProperties(descriptors);
          }
        }
      }
      return descriptors.isEmpty() ? XmlAttributeDescriptor.EMPTY : descriptors.toArray(new XmlAttributeDescriptor[descriptors.size()]);
    }
    return XmlAttributeDescriptor.EMPTY;
  }

  @Nullable
  @Override
  public XmlAttributeDescriptor getAttributeDescriptor(@NonNls String attributeName, @Nullable XmlTag context) {
    final List<String> defaultAttributeList = FxmlConstants.FX_ELEMENT_ATTRIBUTES.get(getName());
    if (defaultAttributeList != null) {
      if (defaultAttributeList.contains(attributeName)) {
        return new JavaFxDefaultPropertyAttributeDescriptor(attributeName, getName());
      }
      final PsiMethod propertySetter = JavaFxPsiUtil.findPropertySetter(attributeName, context);
      if (propertySetter != null) {
        return new JavaFxStaticSetterAttributeDescriptor(propertySetter, attributeName);
      }

      final XmlTag referencedTag = getReferencedTag(myXmlTag);
      if (referencedTag != null) {
        final XmlElementDescriptor referencedDescriptor = referencedTag.getDescriptor();
        if (referencedDescriptor != null) {
          return referencedDescriptor.getAttributeDescriptor(attributeName, referencedTag);
        }
      }

      final JavaFxClassBackedElementDescriptor rootTagDescriptor = getFxRootTagDescriptor(context);
      if (rootTagDescriptor != null) {
        return rootTagDescriptor.getAttributeDescriptor(attributeName, context);
      }

      if (context != null && FxmlConstants.FX_INCLUDE.equals(getName())) {
        final XmlTag includedRoot = getIncludedRoot(context);
        if (includedRoot != null) {
          final XmlElementDescriptor includedRootDescriptor = includedRoot.getDescriptor();
          if (includedRootDescriptor != null) {
            return includedRootDescriptor.getAttributeDescriptor(attributeName, includedRoot);
          }
        }
      }
    }
    return null;
  }

  @Nullable
  public static XmlTag getReferencedTag(XmlTag tag) {
    final String tagName = tag.getName();
    if (FxmlConstants.FX_REFERENCE.equals(tagName) || FxmlConstants.FX_COPY.equals(tagName)) {
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

  public static XmlTag getIncludedRoot(XmlTag context) {
    if (context == null) return null;
    final XmlAttribute xmlAttribute = context.getAttribute(FxmlConstants.FX_ELEMENT_SOURCE);
    if (xmlAttribute != null) {
      final XmlAttributeValue valueElement = xmlAttribute.getValueElement();
      if (valueElement != null) {
        final PsiReference reference = valueElement.getReference();
        if (reference != null) {
          final PsiElement resolve = reference.resolve();
          if (resolve instanceof XmlFile) {
            final XmlTag rootTag = ((XmlFile)resolve).getRootTag();
            if (rootTag != null) {
              return rootTag;
            }
          }
        }
      }
    }
    return null;
  }

  public JavaFxClassBackedElementDescriptor getFxRootTagDescriptor(XmlTag context) {
    if (context != null && FxmlConstants.FX_ROOT.equals(getName())) {
      final XmlAttribute typeAttr = context.getAttribute(FxmlConstants.TYPE);
      if (typeAttr != null) {
        final String rootClassName = typeAttr.getValue();
        final PsiClass rootClass = rootClassName != null ?JavaFxPsiUtil.findPsiClass(rootClassName, context) : null;
        if (rootClass != null) {
          return new JavaFxClassBackedElementDescriptor(getName(), rootClass);
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

  @Override
  public void validate(@NotNull XmlTag context, @NotNull ValidationHost host) {
    final String contextName = context.getName();
    if (FxmlConstants.FX_ROOT.equals(contextName)) {
      if (context.getParentTag() != null) {
        host.addMessage(context.getNavigationElement(), "<fx:root> is valid only as the root node of an FXML document",
                        ValidationHost.ErrorType.ERROR);
      }
    } else {
      final XmlTag referencedTag = getReferencedTag(context);
      if (referencedTag != null) {
        final XmlElementDescriptor descriptor = referencedTag.getDescriptor();
        if (descriptor != null) {
          final PsiElement declaration = descriptor.getDeclaration();
          if (declaration instanceof PsiClass) {
            final PsiClass psiClass = (PsiClass)declaration;
            final String canCoerceError = JavaFxPsiUtil.isClassAcceptable(context.getParentTag(), psiClass);
            if (canCoerceError != null) {
              host.addMessage(context.getNavigationElement(), canCoerceError, ValidationHost.ErrorType.ERROR);
            }
            if (FxmlConstants.FX_COPY.equals(contextName)) {
              boolean copyConstructorFound = false;
              for (PsiMethod constructor : psiClass.getConstructors()) {
                final PsiParameter[] parameters = constructor.getParameterList().getParameters();
                if (parameters.length == 1 && psiClass == PsiUtil.resolveClassInType(parameters[0].getType())) {
                  copyConstructorFound = true;
                  break;
                }
              }
              if (!copyConstructorFound) {
                host.addMessage(context.getNavigationElement(), "Copy constructor not found for \'" + psiClass.getName() + "\'",
                                ValidationHost.ErrorType.ERROR);
              }
            }
          }
        }
      }
    }
  }
}
