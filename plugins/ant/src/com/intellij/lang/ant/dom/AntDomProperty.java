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

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 21, 2010
 */
public abstract class AntDomProperty extends AntDomPropertyDefiningElement{
  private Map<String, String> myCachedPreperties;

  @Attribute("name")
  @Convert(value = AntDomAttributeValueConverter.class)
  public abstract GenericAttributeValue<String> getName();

  @Attribute("value")
  public abstract GenericAttributeValue<String> getValue();

  @Attribute("location")
  public abstract GenericAttributeValue<String> getLocation();

  @Attribute("resource")
  public abstract GenericAttributeValue<String> getResource();

  @Attribute("file")
  public abstract GenericAttributeValue<String> getFile();

  @Attribute("url")
  public abstract GenericAttributeValue<String> getUrl();

  @Attribute("environment")
  public abstract GenericAttributeValue<String> getEnvironment();

  @Attribute("classpath")
  public abstract GenericAttributeValue<String> getClasspath();

  @Attribute("classpathref")
  public abstract GenericAttributeValue<String> getClasspathRef();

  @Attribute("prefix")
  public abstract GenericAttributeValue<String> getPrefix();

  @Attribute("relative")
  public abstract GenericAttributeValue<String> getRelative();

  @Attribute("basedir")
  public abstract GenericAttributeValue<String> getbasedir();

  @NotNull
  public final Iterator<String> getNamesIterator() {
    return buildProperties().keySet().iterator();
  }

  public PsiElement getNavigationElement(String propertyName) {
    final GenericAttributeValue<String> name = getName();
    if (name != null) {
      return name.getXmlAttributeValue();
    }
    // todo: process property files
    return null;
  }

  @Nullable
  public final String getPropertyValue(String propertyName) {
    return buildProperties().get(propertyName);
  }

  private Map<String, String> buildProperties() {
    Map<String, String> result = myCachedPreperties;
    if (result != null) {
      return result;
    }
    result = Collections.emptyMap();
    final GenericAttributeValue<String> name = getName();
    if (name != null) {
      final GenericAttributeValue<String> value = getValue();
      if (value != null) {
        result = Collections.singletonMap(name.getRawText(), value.getRawText());
      }
      else {
        final GenericAttributeValue<String> location = getLocation();
        if (location != null) {
          String locValue = location.getStringValue();
          locValue = FileUtil.toSystemDependentName(locValue);
          // todo: if the path is relative, resolve it against project basedir (see ant docs)
          result = Collections.singletonMap(name.getRawText(), locValue);
        }
        else {
          // todo: process refid attrib if specifiedfor the value
          final String tagText = getXmlTag().getText();
          result = Collections.singletonMap(name.getRawText(), tagText);
        }
      }
    }
    else { // name attrib is not specified
      final GenericAttributeValue<String> resourceValue = getResource();
      if (resourceValue != null) {
        final GenericAttributeValue<String> prefixValue = getPrefix();
        // todo
      }
      else {
        final GenericAttributeValue<String> fileValue = getFile();
        if (fileValue != null) {
          final String pathToPropFile = fileValue.getStringValue();
          final GenericAttributeValue<String> prefixValue = getPrefix();
          // todo: load properties from the file
        }
        else {
          // todo: consider Url attribute?
          final GenericAttributeValue<String> envValue = getEnvironment();
          if (envValue != null) {
            String prefix = envValue.getStringValue();
            if (!prefix.endsWith(".")) {
              prefix = prefix + ".";
            }
            result = new HashMap<String, String>();
            for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
              result.put(prefix + entry.getKey(), entry.getValue());
            }
          }
        }
      }
    }
    return (myCachedPreperties = result);
  }
}
