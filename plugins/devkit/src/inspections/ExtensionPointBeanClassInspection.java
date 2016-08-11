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
package org.jetbrains.idea.devkit.inspections;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.highlighting.BasicDomElementsInspection;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder;
import com.intellij.util.xml.highlighting.DomHighlightingHelper;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.dom.impl.ExtensionDomExtender;
import org.jetbrains.idea.devkit.inspections.quickfix.AddWithTagFix;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class ExtensionPointBeanClassInspection extends BasicDomElementsInspection<IdeaPlugin> {

  public ExtensionPointBeanClassInspection() {
    super(IdeaPlugin.class);
  }

  @Override
  protected void checkDomElement(DomElement element, DomElementAnnotationHolder holder, DomHighlightingHelper helper) {
    if (element instanceof ExtensionPoint) {
      ExtensionPoint extensionPoint = (ExtensionPoint)element;
      if (extensionPoint.getWithElements().isEmpty() && !collectMissingWithTags(extensionPoint).isEmpty()) {
        holder.createProblem(extensionPoint,
                             "<extensionPoint> does not have <with> tags to specify the types of class fields",
                             new AddWithTagFix());
      }
    }
  }

  public static List<PsiField> collectMissingWithTags(ExtensionPoint element) {
    final List<PsiField> result = new ArrayList<>();
    PsiClass beanClass = element.getBeanClass().getValue();
    if (beanClass != null) {
      for (PsiField field : beanClass.getAllFields()) {
        if (ExtensionDomExtender.isClassField(field.getName()) &&
            ExtensionDomExtender.findWithElement(element.getWithElements(), field) == null) {
          result.add(field);
        }
      }
    }
    return result;
  }
}
