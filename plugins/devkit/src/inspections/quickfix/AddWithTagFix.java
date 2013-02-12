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
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.With;
import org.jetbrains.idea.devkit.dom.impl.PluginFieldNameConverter;
import org.jetbrains.idea.devkit.inspections.ExtensionPointBeanClassInspection;

import java.util.List;

/**
 * @author yole
 */
public class AddWithTagFix implements LocalQuickFix {
  @NotNull
  @Override
  public String getName() {
    return "Add <with> tag";
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getName();
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    DomElement element = DomUtil.getDomElement(descriptor.getPsiElement());
    if (!(element instanceof ExtensionPoint)) {
      return;
    }
    ExtensionPoint extensionPoint = (ExtensionPoint)element;
    List<PsiField> fields = ExtensionPointBeanClassInspection.collectMissingWithTags(extensionPoint);
    PsiElement navTarget = null;
    for (PsiField field : fields) {
      String attributeName = PluginFieldNameConverter.getAttributeAnnotationValue(field);
      if (attributeName == null) {
        attributeName = field.getName();
      }
      With with = extensionPoint.addWith();
      with.getAttribute().setStringValue(attributeName);
      with.getImplements().setStringValue("");
      if (navTarget == null) {
        navTarget = with.getImplements().getXmlAttributeValue();
      }
    }
    if (navTarget != null) {
      PsiNavigateUtil.navigate(navTarget);
    }
  }
}
