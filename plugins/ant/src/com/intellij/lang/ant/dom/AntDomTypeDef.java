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

import com.intellij.lang.ant.config.impl.AntResourcesClassLoader;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntFilesProvider;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.XmlName;
import org.apache.tools.ant.Task;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 1, 2010
 */
public abstract class AntDomTypeDef extends AntDomNamedElement{
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.dom.AntDomTypeDef");

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
  public abstract GenericAttributeValue<List<File>> getClasspath();

  @Attribute("classpathref")
  @Convert(value = AntDomRefIdConverter.class)
  public abstract GenericAttributeValue<AntDomElement> getClasspathRef();

  @Attribute("loaderref")
  public abstract GenericAttributeValue<String> getLoaderRef();

  @Attribute("uri")
  public abstract GenericAttributeValue<String> getUri();

  @Attribute("adapter")
  public abstract GenericAttributeValue<String> getAdapter();

  @Attribute("adaptto")
  public abstract GenericAttributeValue<String> getAdaptto();


  public void loadCustomDefinitions() {
    final String classname = getClassName().getRawText();
    final String customTagName = getName().getRawText();
    if (classname != null) {

      //loadClass(antFile, classname, customTagName, getUri().getStringValue(),getAntParent());
    }
    else {
      final String resource = getResource().getStringValue();
      if (resource != null) {
        //loadResource(resource);
      }
      else {
        final String file = getFile().getStringValue();
        if (file != null) {
          //loadFile(file);
        }
      }
    }
  }

  private void loadClass(final @Nullable AntFile antFile,
                                          @Nullable final String classname,
                                          @Nullable final String customTagName,
                                          @Nullable final String uri,
                                          final AntStructuredElement parent) {
    if (classname == null || customTagName == null || customTagName.length() == 0) {
      return;
    }
    ProgressManager.checkCanceled();
    final List<URL> urls = getClassPathLocalUrls();
    Class clazz = null;
    final ClassLoader loader = getClassLoader(urls);
    if (loader != null) {
      try {
        clazz = loader.loadClass(classname);
      }
      catch (ClassNotFoundException e) {
        //myLocalizedError = e.getLocalizedMessage();
        clazz = null;
      }
      catch (NoClassDefFoundError e) {
        //myLocalizedError = e.getLocalizedMessage();
        clazz = null;
      }
      catch (UnsupportedClassVersionError e) {
        //myLocalizedError = e.getLocalizedMessage();
        clazz = null;
      }
    }
    final String nsPrefix = (uri == null) ? null : getXmlTag().getPrefixByNamespace(uri);

    final XmlName id = (nsPrefix == null) ? new XmlName(customTagName) : new XmlName(customTagName, nsPrefix);  // todo: namespaceKey?
    final String elementId = (nsPrefix == null) ? customTagName : nsPrefix + ":" + customTagName;


    if (clazz != null) {
      CustomAntElementsRegistry.getInstance(getAntProject()).registerCustomElement(elementId, clazz);
      //final boolean isTask = isTask(clazz);
      //def = (AntTypeDefinitionImpl)AntFileImpl.createTypeDefinition(id, clazz, isTask);
      //if (def == null) { // can be null if failed to introspect the class for some reason
      //  def = new AntTypeDefinitionImpl(id, classname, isTask, isAssignableFrom(TaskContainer.class.getName(), clazz));
      //}
      //else {
      //  fixNestedDefinitions(def);
      //}
      //def.setIsProperty(isAssignableFrom(org.apache.tools.ant.taskdefs.Property.class.getName(), clazz));
    }
  }

  private boolean isTask(final Class clazz) {
    if ("taskdef".equals(getXmlTag().getName())) { // in taskdef, the adapter is always set to Task
      return true;
    }

    final String adaptto = getAdaptto().getStringValue();
    if (adaptto != null && isAssignableFrom(adaptto, clazz)) {
      return isAssignableFrom(Task.class.getName(), clazz);
    }

    final String adapter = getAdapter().getStringValue();
    if (adapter != null) {
      try {
        final Class adapterClass = clazz.getClassLoader().loadClass(adapter);
        return isAssignableFrom(Task.class.getName(), adapterClass);
      }
      catch (ClassNotFoundException ignored) {
      }
      catch (NoClassDefFoundError ignored) {
      }
      catch (UnsupportedClassVersionError ignored) {
      }
    }

    return isAssignableFrom(Task.class.getName(), clazz);
  }

  private static boolean isAssignableFrom(final String baseClassName, final Class clazz) {
    try {
      final ClassLoader loader = clazz.getClassLoader();
      if (loader != null) {
        final Class baseClass = loader.loadClass(baseClassName);
        return baseClass.isAssignableFrom(clazz);
      }
    }
    catch (ClassNotFoundException ignored) {
    }
    return false;
  }

  @Nullable
  private ClassLoader getClassLoader(final List<URL> urls) {
    final ClassLoader parentLoader = getAntProject().getClassLoader();
    if (urls.size() == 0) {
      return parentLoader;
    }

    //final ClassLoader cached = LOADERS_CACHE.getClassLoader(urls);
    //if (cached != null && parentLoader == cached.getParent()) {
    //  return cached;
    //}

    final ClassLoader loader = new AntResourcesClassLoader(urls, parentLoader, false, false);
    //LOADERS_CACHE.setClassLoader(urls, loader);
    return loader;
  }

  private List<URL> getClassPathLocalUrls() {
    final List<URL> urls = new ArrayList<URL>();
    // check classpath attribute
    final List<File> cpFiles = getClasspath().getValue();
    if (cpFiles != null) {
      for (File file : cpFiles) {
        try {
          urls.add(toLocalURL(file));
        }
        catch (MalformedURLException ignored) {
          LOG.info(ignored);
        }
      }
    }

    final HashSet<AntFilesProvider> processed = new HashSet<AntFilesProvider>();
    final AntDomElement referencedPath = getClasspathRef().getValue();
    if (referencedPath instanceof AntFilesProvider) {
      for (File cpFile : ((AntFilesProvider)referencedPath).getFiles(processed)) {
        try {
          urls.add(toLocalURL(cpFile));
        }
        catch (MalformedURLException ignored) {
          LOG.info(ignored);
        }
      }
    }
    // check nested elements
    for (AntDomElement child : getAntChildren()) {
      if (child instanceof AntFilesProvider) {
        for (File cpFile : ((AntFilesProvider)child).getFiles(processed)) {
          try {
            urls.add(toLocalURL(cpFile));
          }
          catch (MalformedURLException ignored) {
            LOG.info(ignored);
          }
        }
      }

    }

    return urls;
  }

  private static URL toLocalURL(final File file) throws MalformedURLException {
    return new URL("file", "", FileUtil.toSystemIndependentName(file.getPath()));
  }

}
