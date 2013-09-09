/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.icons.AllIcons;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiClass;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileDescription;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder;
import com.intellij.util.xml.highlighting.DomElementsAnnotator;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

import javax.swing.*;

/**
 * @author mike
 */
public class PluginXmlDomFileDescription extends DomFileDescription<IdeaPlugin> {

  private static final DomElementsAnnotator ANNOTATOR = new DomElementsAnnotator() {
    @Override
    public void annotate(DomElement element, DomElementAnnotationHolder holder) {
      if (element instanceof Extension) {
        annotateExtension((Extension)element, holder);
      }
    }

    private void annotateExtension(Extension extension, DomElementAnnotationHolder holder) {
      final ExtensionPoint extensionPoint = extension.getExtensionPoint();
      if (extensionPoint == null) return;
      final GenericAttributeValue<PsiClass> interfaceAttribute = extensionPoint.getInterface();
      if (!DomUtil.hasXml(interfaceAttribute)) return;

      final PsiClass value = interfaceAttribute.getValue();
      if (value != null && value.isDeprecated()) {
        final Annotation annotation = holder.createAnnotation(extension, HighlightSeverity.WARNING, "Deprecated EP");
        annotation.setHighlightType(ProblemHighlightType.LIKE_DEPRECATED);
      }
    }
  };

  public PluginXmlDomFileDescription() {
    super(IdeaPlugin.class, "idea-plugin");
  }

  @Override
  public Icon getFileIcon(@Iconable.IconFlags int flags) {
    return AllIcons.Nodes.Plugin;
  }

  @Nullable
  @Override
  public DomElementsAnnotator createAnnotator() {
    return ANNOTATOR;
  }

  @Override
  public boolean hasStubs() {
    return true;
  }
}
