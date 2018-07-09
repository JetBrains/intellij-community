/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.util;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

/**
 * @author swr
 */
public enum ComponentType {
  APPLICATION(ApplicationComponent.class, "application-components", "new.menu.application.component.text"),
  PROJECT(ProjectComponent.class, "project-components", "new.menu.project.component.text"),
  MODULE(ModuleComponent.class, "module-components", "new.menu.module.component.text");

  public final String myClassName;
  public final String myPropertyKey;
  private final String myName;

  public interface Processor {
    boolean process(ComponentType type, XmlTag component, @Nullable XmlTagValue impl, @Nullable XmlTagValue intf);
  }

  ComponentType(Class<? extends BaseComponent> clazz, @NonNls String name,
                @PropertyKey(resourceBundle = "org.jetbrains.idea.devkit.DevKitBundle") String propertyKey)
  {
    myPropertyKey = propertyKey;
    myClassName = clazz.getName();
    myName = name;
  }

  public void patchPluginXml(XmlFile pluginXml, PsiClass klass) throws IncorrectOperationException {
    final XmlTag rootTag = pluginXml.getDocument().getRootTag();
    if (rootTag != null && "idea-plugin".equals(rootTag.getName())) {
      XmlTag components = rootTag.findFirstSubTag(myName);
      if (components == null || !components.isPhysical()) {
        components = (XmlTag)rootTag.add(rootTag.createChildTag(myName, rootTag.getNamespace(), null, false));
      }

      XmlTag cmp = (XmlTag)components.add(components.createChildTag("component", components.getNamespace(), null, false));
      cmp.add(cmp.createChildTag("implementation-class", cmp.getNamespace(), klass.getQualifiedName(), false));

      // some magic to figure out interface-class
      final PsiMethod[] methods = klass.findMethodsByName("getInstance", true);
      for (PsiMethod method : methods) {
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length <= 1) {
          final PsiType returnType = method.getReturnType();
          if (returnType instanceof PsiClassType) {
            final String intf = returnType.getCanonicalText();
            cmp.add(cmp.createChildTag("interface-class", cmp.getNamespace(), intf, false));
            break;
          }
        }
      }
    }
  }

  public void process(XmlTag rootTag, Processor processor) {
    final XmlTag[] compGroup = rootTag.findSubTags(myName);
    for (XmlTag tag : compGroup) {
      if (!tag.isPhysical()) continue; //skip included tags
      final XmlTag[] components = tag.findSubTags("component");
      for (XmlTag component : components) {
        final XmlTag impl = component.findFirstSubTag("implementation-class");
        final XmlTag intf = component.findFirstSubTag("interface-class");
        if (!processor.process(this, component,
                impl != null ? impl.getValue() : null,
                intf != null ? intf.getValue() : null))
        {
          return;
        }
      }
    }
  }
}
