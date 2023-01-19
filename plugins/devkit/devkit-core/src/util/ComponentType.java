// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.util;

import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PsiNavigateUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

public enum ComponentType {

  MODULE(ModuleComponent.class, "module-components", "new.menu.module.component.text"),
  @SuppressWarnings("deprecation")
  PROJECT(ProjectComponent.class, "project-components", "new.menu.project.component.text"),
  @SuppressWarnings("deprecation")
  APPLICATION(BaseComponent.class, "application-components", "new.menu.application.component.text");

  public final String myClassName;
  @PropertyKey(resourceBundle = DevKitBundle.BUNDLE)
  public final String myPropertyKey;
  private final String myName;

  public interface Processor {
    boolean process(ComponentType type, XmlTag component, @Nullable XmlTagValue impl, @Nullable XmlTagValue intf);
  }

  ComponentType(@SuppressWarnings("deprecation") Class<? extends BaseComponent> clazz, @NonNls String name,
                @PropertyKey(resourceBundle = DevKitBundle.BUNDLE) String propertyKey) {
    myPropertyKey = propertyKey;
    myClassName = clazz.getName();
    myName = name;
  }

  public void patchPluginXml(XmlFile pluginXml, PsiClass klass) throws IncorrectOperationException {
    final XmlTag rootTag = pluginXml.getDocument().getRootTag();
    if (rootTag != null && IdeaPlugin.TAG_NAME.equals(rootTag.getName())) {
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

      PsiNavigateUtil.navigate(cmp);
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
                               intf != null ? intf.getValue() : null)) {
          return;
        }
      }
    }
  }
}
