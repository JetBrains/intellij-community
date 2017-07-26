/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.actions.service;

import com.intellij.psi.*;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.actions.GenerateClassAndPatchPluginXmlActionBase;

import javax.swing.*;

public abstract class GenerateServiceClassAndPatchPluginXmlActionBase extends GenerateClassAndPatchPluginXmlActionBase {
  public GenerateServiceClassAndPatchPluginXmlActionBase(String text, String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  @Override
  public void patchPluginXml(XmlFile pluginXml, PsiClass klass) throws IncorrectOperationException {
    XmlDocument document = pluginXml.getDocument();
    if (document == null) {
      // shouldn't happen (actions won't be visible when there's no plugin.xml)
      return;
    }

    XmlTag rootTag = document.getRootTag();
    if (rootTag != null && "idea-plugin".equals(rootTag.getName())) {
      XmlTag extensions = rootTag.findFirstSubTag("extensions");
      if (extensions == null || !extensions.isPhysical()) {
        extensions = (XmlTag)rootTag.add(rootTag.createChildTag("extensions", rootTag.getNamespace(), null, false));
        extensions.setAttribute("defaultExtensionNs", "com.intellij");
      }

      XmlTag service = (XmlTag)extensions.add(extensions.createChildTag(getTagName(), extensions.getNamespace(), null, false));
      service.setAttribute("serviceInterface", klass.getQualifiedName());
      service.setAttribute("serviceImplementation", klass.getQualifiedName());
    }
  }

  protected abstract String getTagName();
}
