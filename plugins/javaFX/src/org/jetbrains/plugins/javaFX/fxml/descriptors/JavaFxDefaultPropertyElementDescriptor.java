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
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlElementsGroup;
import com.intellij.xml.XmlNSDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 */
public class JavaFxDefaultPropertyElementDescriptor implements XmlElementDescriptor{
  private final String myName;
  private PsiElement myElement;

  public JavaFxDefaultPropertyElementDescriptor(String name, XmlTag tag) {
    myName = name;
    myElement = tag;
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
    return new XmlElementDescriptor[0];
  }

  @Nullable
  @Override
  public XmlElementDescriptor getElementDescriptor(XmlTag childTag, XmlTag contextTag) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public XmlAttributeDescriptor[] getAttributesDescriptors(@Nullable XmlTag context) {
    return new XmlAttributeDescriptor[0];  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Nullable
  @Override
  public XmlAttributeDescriptor getAttributeDescriptor(@NonNls String attributeName, @Nullable XmlTag context) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Nullable
  @Override
  public XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute attribute) {
    return null;
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
    return myElement;
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
