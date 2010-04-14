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
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.reflect.DomExtender;
import com.intellij.util.xml.reflect.DomExtensionsRegistrar;
import com.intellij.util.xml.reflect.DomGenericInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 9, 2010
 */
public class AntDomExtender extends DomExtender<AntDomElement>{
  public void registerExtensions(@NotNull AntDomElement antDomElement, @NotNull DomExtensionsRegistrar registrar) {
    final XmlElement xmlElement = antDomElement.getXmlElement();
    if (xmlElement instanceof XmlTag) {

      //final AntDomProject antProject = antDomElement.getParentOfType(AntDomProject.class, false);
      //assert antProject != null;
      //final ReflectedProject reflected = ReflectedProject.getProject(antProject.getClassLoader());

      final Set<String> names = new HashSet<String>();
      for (XmlTag tag : ((XmlTag)xmlElement).getSubTags()) {
        names.add(tag.getName());
      }
      final DomGenericInfo genericInfo = antDomElement.getGenericInfo();
      for (String name : names) {
        if (genericInfo.getCollectionChildDescription(name) == null) { // not defined yet
          registrar.registerCollectionChildrenExtension(new XmlName(name), AntDomElement.class);
        }
      }
    }
  }


  @Nullable
  private static AntIntrospector getIntrospector(Class c) {
    try {
      return AntIntrospector.getInstance(c);
    }
    catch (Throwable ignored) {
    }
    return null;
  }

}
