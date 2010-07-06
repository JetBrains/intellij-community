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
import com.intellij.pom.PomTarget;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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

      final AntDomProject antProject = antDomElement.getAntProject();
      final ReflectedProject reflected = ReflectedProject.getProject(antProject.getClassLoader());

      final DomGenericInfo genericInfo = antDomElement.getGenericInfo();
      AntIntrospector parentElementIntrospector = null;
      final Hashtable<String,Class> taskDefs = reflected.getTaskDefinitions();
      final Hashtable<String, Class> dataTypeDefs = reflected.getDataTypeDefinitions();
      if ("project".equals(tagName)) {
        parentElementIntrospector = getIntrospector(reflected.getProject().getClass());
      }
      else if ("target".equals(tagName)) {
        parentElementIntrospector = getIntrospector(reflected.getTargetClass());
      }
      else {
        final Class taskClass = taskDefs.get(tagName);
        if (taskClass != null) {
          parentElementIntrospector = getIntrospector(taskClass);
        }
        else {
          final Class dataClass = dataTypeDefs.get(tagName);
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
          for (String nestedName : taskDefs.keySet()) {
            final DomExtension extension = registerChild(registrar, genericInfo, nestedName);
            if (extension != null) {
              extension.putUserData(AntDomElement.ROLE, AntDomElement.Role.TASK);
            }
          }
          for (String nestedTypeDef : dataTypeDefs.keySet()) {
            final DomExtension extension = registerChild(registrar, genericInfo, nestedTypeDef);
            if (extension != null) {
              extension.putUserData(AntDomElement.ROLE, AntDomElement.Role.DATA_TYPE);
            }
          }
          registrar.registerCustomChildrenExtension(AntDomCustomTask.class, new AntCustomTagNameDescriptor());
        }
        else {
          final Enumeration<String> nested = parentElementIntrospector.getNestedElements();
          while (nested.hasMoreElements()) {
            final String nestedElementName = nested.nextElement();
            final DomExtension extension = registerChild(registrar, genericInfo, nestedElementName);
            if (extension != null) {
              AntDomElement.Role role = null;
              if (taskDefs.containsKey(nestedElementName)) {
                role = AntDomElement.Role.TASK;
              }
              else if (dataTypeDefs.containsKey(nestedElementName)) {
                role = AntDomElement.Role.DATA_TYPE;
              }
              if (role != null) {
                extension.putUserData(AntDomElement.ROLE, role);
              }
            }
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
      else if ("typedef".equalsIgnoreCase(childName)) {
        modelClass = AntDomTypeDef.class;
      }
      else if ("taskdef".equalsIgnoreCase(childName)) {
        modelClass = AntDomTypeDef.class;
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

  private static class AntCustomTagNameDescriptor extends CustomDomChildrenDescription.TagNameDescriptor {

    public Set<EvaluatedXmlName> getCompletionVariants(@NotNull DomElement parent) {
      if (!(parent instanceof AntDomElement)) {
        return Collections.emptySet();
      }
      final AntDomElement element = (AntDomElement)parent;
      final CustomAntElementsRegistry registry = CustomAntElementsRegistry.getInstance(element.getAntProject());
      final Set<EvaluatedXmlName> result = new HashSet<EvaluatedXmlName>();
      for (XmlName variant : registry.getCompletionVariants(element)) {
        result.add(new DummyEvaluatedXmlName(variant, null));
      }
      return result;
    }

    @Nullable
    public PomTarget findDeclaration(DomElement parent, @NotNull EvaluatedXmlName name) {
      final XmlName xmlName = name.getXmlName();
      return doFindDeclaration(parent, xmlName);
    }

    @Nullable
    public PomTarget findDeclaration(@NotNull DomElement child) {
      XmlName name = new XmlName(child.getXmlElementName(), child.getXmlElementNamespaceKey());
      return doFindDeclaration(child.getParent(), name);
    }

    @Nullable
    private static PomTarget doFindDeclaration(DomElement parent, XmlName xmlName) {
      if (!(parent instanceof AntDomElement)) {
        return null;
      }
      final AntDomElement element = (AntDomElement)parent;
      final CustomAntElementsRegistry registry = CustomAntElementsRegistry.getInstance(element.getAntProject());
      final AntDomElement declaringElement = registry.findDeclaringElement(element, xmlName);
      if (declaringElement == null) {
        return null;
      }
      return DomTarget.getTarget(element);
    }
  }
}
