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

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.pom.references.PomService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.DomTarget;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 21, 2010
 */
public abstract class AntDomProperty extends AntDomNamedElement implements PropertiesProvider{
  private volatile Map<String, String> myCachedPreperties;

  @Attribute("value")
  @Convert(value = AntDomPropertyValueConverter.class)
  public abstract GenericAttributeValue<Object> getValue();

  @Attribute("location")
  @Convert(value = AntPathConverter.class)
  public abstract GenericAttributeValue<PsiFileSystemItem> getLocation();

  @Attribute("resource")
  public abstract GenericAttributeValue<String> getResource();

  @Attribute("file")
  @Convert(value = AntPathConverter.class)
  public abstract GenericAttributeValue<PsiFileSystemItem> getFile();

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
    final String prefix = getPropertyPrefixValue();
    final Iterator<String> delegate = buildProperties().keySet().iterator();
    if (prefix == null) {
      return delegate;
    }
    return new Iterator<String>() {
      public boolean hasNext() {
        return delegate.hasNext();
      }

      public String next() {
        return prefix + delegate.next();
      }

      public void remove() {
        delegate.remove();
      }
    };
  }

  public PsiElement getNavigationElement(final String propertyName) {
    final DomTarget domTarget = DomTarget.getTarget(this);
    if (domTarget != null) {
      final PsiElement psi = PomService.convertToPsi(domTarget);
      if (psi != null) {
        return psi;
      }
    }

    final PsiFileSystemItem psiFile = getFile().getValue();
    if (psiFile != null) {
      final String prefix = getPropertyPrefixValue();
      String _propertyName = propertyName;
      if (prefix != null) {
        if (!propertyName.startsWith(prefix)) {
          return null;
        }
        _propertyName = propertyName.substring(prefix.length());
      }
      if (psiFile instanceof PropertiesFile) {
        final Property property = ((PropertiesFile)psiFile).findPropertyByKey(_propertyName);
        return property != null? property.getNavigationElement() : null;
      }
    }
    return null;
  }

  @Nullable
  public final String getPropertyValue(String propertyName) {
    final String prefix = getPropertyPrefixValue();
    if (prefix != null) {
      if (!propertyName.startsWith(prefix)) {
        return null;
      }
      propertyName = propertyName.substring(prefix.length());
    }
    return buildProperties().get(propertyName);
  }

  private Map<String, String> buildProperties() {
    Map<String, String> result = myCachedPreperties;
    if (result != null) {
      return result;
    }
    result = Collections.emptyMap();
    final String propertyName = getName().getRawText();
    if (propertyName != null) {
      final String propertyValue = getValue().getRawText();
      if (propertyValue != null) {
        result = Collections.singletonMap(propertyName, propertyValue);
      }
      else {
        String locValue = getLocation().getStringValue();
        if (locValue != null) {
          final File file = new File(locValue);
          if (!file.isAbsolute()) {
            final String baseDir = getContextAntProject().getProjectBasedirPath();
            if (baseDir != null) {
              locValue = PathUtil.getCanonicalPath(new File(baseDir, locValue).getPath());
            }
          }
          result = Collections.singletonMap(propertyName, FileUtil.toSystemDependentName(locValue));
        }
        else {
          // todo: process refid attrib if specified for the value
          final String tagText = getXmlTag().getText();
          result = Collections.singletonMap(propertyName, tagText);
        }
      }
    }
    else { // name attrib is not specified
      final PsiFileSystemItem psiFile = getFile().getValue();
      if (psiFile != null) {
        if (psiFile instanceof PropertiesFile) {
          result = new HashMap<String, String>();
          for (Property property : ((PropertiesFile)psiFile).getProperties()) {
            result.put(property.getKey(), property.getValue());
          }
        }
      }
      else if (getEnvironment().getRawText() != null){
        String prefix = getEnvironment().getRawText();
        if (!prefix.endsWith(".")) {
          prefix += ".";
        }
        result = new HashMap<String, String>();
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
          result.put(prefix + entry.getKey(), entry.getValue());
        }
      }
      else {
        final GenericAttributeValue<String> resourceValue = getResource();
        final GenericAttributeValue<String> prefixValue = getPrefix();
        // todo: try to load the resource from classpath
        // todo: consider Url attribute?
      }
    }
    return (myCachedPreperties = result);
  }

  @Nullable
  private String getPropertyPrefixValue() {
    final GenericAttributeValue<String> prefixValue = getPrefix();
    if (prefixValue == null) {
      return null;
    }
    final String prefix = prefixValue.getRawText();
    if (prefix != null && !prefix.endsWith(".")) {
      return prefix + ".";
    }
    return prefix;
  }
}
