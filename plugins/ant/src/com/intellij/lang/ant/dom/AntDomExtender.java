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

import com.intellij.lang.ant.psi.impl.AntIntrospector;
import com.intellij.lang.ant.psi.impl.ReflectedProject;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.reflect.DomExtender;
import com.intellij.util.xml.reflect.DomExtension;
import com.intellij.util.xml.reflect.DomExtensionsRegistrar;
import com.intellij.util.xml.reflect.DomGenericInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Enumeration;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 9, 2010
 */
public class AntDomExtender extends DomExtender<AntDomElement>{
  public void registerExtensions(@NotNull AntDomElement antDomElement, @NotNull DomExtensionsRegistrar registrar) {
    final XmlElement xmlElement = antDomElement.getXmlElement();
    if (xmlElement instanceof XmlTag) {
      final XmlTag xmlTag = (XmlTag)xmlElement;
      final String tagName = xmlTag.getName(); // todo: support namespaces

      final ReflectedProject reflected = ReflectedProject.getProject(antDomElement.getAntProject().getClassLoader());

      final DomGenericInfo genericInfo = antDomElement.getGenericInfo();
      AntIntrospector parentElementIntrospector = null;
      if ("project".equals(tagName)) {
        parentElementIntrospector = getIntrospector(reflected.getProject().getClass());
      }
      else if ("target".equals(tagName)) {
        parentElementIntrospector = getIntrospector(reflected.getTargetClass());
      }
      else {
        final Map<String, Class> tasks = reflected.getTaskDefinitions();
        final Class taskClass = tasks.get(tagName);
        if (taskClass != null) {
          parentElementIntrospector = getIntrospector(taskClass);
        }
        else {
          final Map<String, Class> dataTypes = reflected.getDataTypeDefinitions();
          final Class dataClass = dataTypes.get(tagName);
          if (dataClass != null) {
            parentElementIntrospector = getIntrospector(dataClass);
          }
        }
      }

      if (parentElementIntrospector != null) {

        final Enumeration attributes = parentElementIntrospector.getAttributes();
        while (attributes.hasMoreElements()) {
          registerAttribute(registrar, genericInfo, (String)attributes.nextElement());
        }

        // todo: handle custom tasks registered in typedefs
        if ("project".equals(tagName) || parentElementIntrospector.isContainer()) { // can contain any task or/and type definition
          for (Object nestedName : reflected.getTaskDefinitions().keySet()) {
            registerChild(registrar, genericInfo, (String)nestedName);
          }
          for (Object nestedTypeDef : reflected.getDataTypeDefinitions().keySet()) {
            registerChild(registrar, genericInfo, (String)nestedTypeDef);
          }
        }
        else {
          final Enumeration<String> nested = parentElementIntrospector.getNestedElements();
          while (nested.hasMoreElements()) {
            registerChild(registrar, genericInfo, nested.nextElement());
          }
        }
      }
    }
  }

  @Nullable
  private static DomExtension registerAttribute(DomExtensionsRegistrar registrar, DomGenericInfo genericInfo, String attrib) {
    if (genericInfo.getAttributeChildDescription(attrib) == null) { // register if not yet defined statically
      return registrar.registerGenericAttributeValueChildExtension(new XmlName(attrib), String.class);
    }
    return null;
  }

  @Nullable
  private static DomExtension registerChild(DomExtensionsRegistrar registrar, DomGenericInfo elementInfo, String childName) {
    if (elementInfo.getCollectionChildDescription(childName) == null) { // register if not yet defined statically
      Class<? extends AntDomElement> modelClass = AntDomElement.class;
      if ("property".equalsIgnoreCase(childName)) {
        modelClass = AntDomProperty.class;
      }
      else if ("fileset".equalsIgnoreCase(childName)) {
        modelClass = AntDomFileSet.class;
      }
      else if ("dirset".equalsIgnoreCase(childName)) {
        modelClass = AntDomDirSet.class;
      }
      else if ("path".equalsIgnoreCase(childName)) {
        modelClass = AntDomPath.class;
      }
      else if ("filelist".equalsIgnoreCase(childName)) {
        modelClass = AntDomFileList.class;
      }
      return registrar.registerCollectionChildrenExtension(new XmlName(childName), modelClass);
    }
    return null;
  }

  @Nullable
  public static AntIntrospector getIntrospector(Class c) {
    try {
      return AntIntrospector.getInstance(c);
    }
    catch (Throwable ignored) {
    }
    return null;
  }

}
