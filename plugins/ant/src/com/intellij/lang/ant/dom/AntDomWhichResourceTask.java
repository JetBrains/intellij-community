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

import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.GenericAttributeValue;

import java.net.URL;

/**
 * @author Eugene Zhuravlev
 */
public abstract class AntDomWhichResourceTask extends AntDomPropertyDefiningTask implements AntDomClasspathElement{

  private volatile ClassLoader myCachedLoader;

  @Attribute("class")
  public abstract GenericAttributeValue<String> getClassName();

  @Attribute("resource")
  public abstract GenericAttributeValue<String> getResourceName();

  protected String calcPropertyValue(String propertyName) {
    String resName = getClassName().getStringValue();
    if (resName == null) {
      resName = getResourceName().getStringValue();
    }
    else {
      resName = resName.replace(".", "/") + ".class";
    }
    if (resName != null) {
      final ClassLoader loader = getClassLoader();
      if (loader != null) {
        final URL resource = loader.getResource(resName);
        if (resource != null) {
          return resource.toExternalForm();
        }
      }
    }
    return "";
  }

  private ClassLoader getClassLoader() {
    ClassLoader loader = myCachedLoader;
    if (loader == null) {
      myCachedLoader = loader = CustomAntElementsRegistry.createClassLoader(CustomAntElementsRegistry.collectUrls(this), getContextAntProject());
    }
    return loader;
  }
}
