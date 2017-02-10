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

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.pom.references.PomService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
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
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 21, 2010
 */
public abstract class AntDomProperty extends AntDomClasspathComponent implements PropertiesProvider{
  private volatile Map<String, String> myCachedProperties;
  private volatile ClassLoader myCachedLoader;


  @Attribute("value")
  @Convert(value = AntDomPropertyValueConverter.class)
  public abstract GenericAttributeValue<Object> getValue();

  @Attribute("location")
  @Convert(value = AntPathConverter.class)
  public abstract GenericAttributeValue<PsiFileSystemItem> getLocation();

  @Attribute("resource")
  public abstract GenericAttributeValue<String> getResource();

  @Attribute("file")
  @Convert(value = AntPathValidatingConverter.class)
  public abstract GenericAttributeValue<PsiFileSystemItem> getFile();

  @Attribute("url")
  public abstract GenericAttributeValue<String> getUrl();

  @Attribute("environment")
  public abstract GenericAttributeValue<String> getEnvironment();

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
    DomTarget domTarget = DomTarget.getTarget(this);
    if (domTarget == null) {
      final GenericAttributeValue<String> environment = getEnvironment();
      if (environment.getRawText() != null) {
        domTarget = DomTarget.getTarget(this, environment);
      }
      if (domTarget == null) {
        final GenericAttributeValue<String> resource = getResource();
        if (resource.getRawText() != null) {
          domTarget = DomTarget.getTarget(this, resource);
        }
      }
    }
    
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
      final PropertiesFile pf = toPropertiesFile(psiFile);
      if (pf != null) {
        final IProperty property = pf.findPropertyByKey(_propertyName);
        return property != null? property.getPsiElement() : null;
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
    Map<String, String> result = myCachedProperties;
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
        final PropertiesFile file = toPropertiesFile(psiFile);
        if (file != null) {
          result = new HashMap<>();
          for (final IProperty property : file.getProperties()) {
            result.put(property.getUnescapedKey(), property.getUnescapedValue());
          }
        }
      }
      else if (getEnvironment().getRawText() != null) {
        String prefix = getEnvironment().getRawText();
        if (!prefix.endsWith(".")) {
          prefix += ".";
        }
        result = new HashMap<>();
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
          result.put(prefix + entry.getKey(), entry.getValue());
        }
      }
      else {
        // todo: consider Url attribute?

        final String resource = getResource().getStringValue();
        if (resource != null) {
          final ClassLoader loader = getClassLoader();
          if (loader != null) {
            final InputStream stream = loader.getResourceAsStream(resource);
            if (stream != null) {
              try {
                // todo: Remote file can be XmlPropertiesFile
                final PropertiesFile propFile = (PropertiesFile)CustomAntElementsRegistry.loadContentAsFile(getXmlTag().getProject(), stream, StdFileTypes.PROPERTIES);
                result = new HashMap<>();
                for (final IProperty property : propFile.getProperties()) {
                  result.put(property.getUnescapedKey(), property.getUnescapedValue());
                }
              }
              catch (IOException ignored) {
              }
            }
          }
        }
      }
    }
    return (myCachedProperties = result);
  }

  @Nullable
  public String getPropertyPrefixValue() {
    final GenericAttributeValue<String> prefixValue = getPrefix();
    if (prefixValue == null) {
      return null;
    }
    // prefix is only valid when loading from an url, file or resource
    final boolean prefixIsApplicable = (getName().getRawText() == null) && (getUrl().getRawText() != null || getFile().getRawText() != null || getResource().getRawText() != null);
    if (!prefixIsApplicable) {
      return null;
    }
    final String prefix = prefixValue.getRawText();
    if (prefix != null && !prefix.endsWith(".")) {
      return prefix + ".";
    }
    return prefix;
  }

  @Nullable
  private ClassLoader getClassLoader() {
    ClassLoader loader = myCachedLoader;
    if (loader == null) {
      myCachedLoader = loader = CustomAntElementsRegistry.createClassLoader(CustomAntElementsRegistry.collectUrls(this), getContextAntProject());
    }
    return loader;
  }

  @Nullable
  public PropertiesFile getPropertiesFile() {
    return toPropertiesFile(getFile().getValue());
  }

  @Nullable
  private static PropertiesFile toPropertiesFile(@Nullable final PsiFileSystemItem item) {
    if (item instanceof PropertiesFile) {
      return (PropertiesFile)item;
    }
    // Sometimes XmlPropertiesFile is just XmlFile, sao we should ask PropertiesImplUtil about that.
    return item instanceof PsiFile? PropertiesImplUtil.getPropertiesFile(((PsiFile)item)) : null;
  }
}
