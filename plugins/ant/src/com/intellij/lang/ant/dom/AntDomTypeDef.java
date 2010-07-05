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

import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericAttributeValue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 1, 2010
 */
public abstract class AntDomTypeDef extends AntDomNamedElement{

  @Attribute("classname")
  public abstract GenericAttributeValue<String> getClassName();

  @Attribute("file")
  @Convert(value = AntPathConverter.class)
  public abstract GenericAttributeValue<PsiFileSystemItem> getFile();

  @Attribute("resource")
  @Convert(value = AntPathConverter.class)
  public abstract GenericAttributeValue<PsiFileSystemItem> getResource();

  @Attribute("format")
  public abstract GenericAttributeValue<String> getFormat();

  @Attribute("classpath")
  @Convert(value = AntMultiPathStringConverter.class)
  public abstract GenericAttributeValue<List<PsiFileSystemItem>> getClasspathString();

  public final List<File> getClasspath() {
    // todo: calc classpath basing on the corresponding attribute and nested elements 
    return new ArrayList<File>();
  }

}
