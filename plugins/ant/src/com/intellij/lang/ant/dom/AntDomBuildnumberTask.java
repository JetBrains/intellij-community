/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.ant.dom;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Iterator;

/**
 * @author Eugene Zhuravlev
 */
public abstract class AntDomBuildnumberTask extends AntDomElement implements PropertiesProvider{
  public static final String PROPERTY_NAME = "build.number";

  @Attribute("file")
  @Convert(value = AntPathConverter.class)
  public abstract GenericAttributeValue<PsiFileSystemItem> getFile();
  
  @NotNull 
  public Iterator<String> getNamesIterator() {
    return Collections.singletonList(PROPERTY_NAME).iterator();
  }

  public String getPropertyValue(String propertyName) {
    return PROPERTY_NAME.equals(propertyName)? "" : null;
  }

  public PsiElement getNavigationElement(String propertyName) {
    return PROPERTY_NAME.equals(propertyName)? getXmlElement() : null;
  }
}
