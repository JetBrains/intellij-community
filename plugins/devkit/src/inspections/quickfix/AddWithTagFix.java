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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.With;
import org.jetbrains.idea.devkit.dom.impl.PluginFieldNameConverter;

import java.util.List;

/**
 * @author yole
 */
public class AddWithTagFix implements LocalQuickFix {
  @NotNull
  @Override
  public String getFamilyName() {
    return "Add <with> tag";
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    DomElement element = DomUtil.getDomElement(descriptor.getPsiElement());
    if (!(element instanceof ExtensionPoint)) {
      return;
    }
    ExtensionPoint extensionPoint = (ExtensionPoint)element;
    List<PsiField> fields = extensionPoint.collectMissingWithTags();
    PsiElement navTarget = null;
    for (PsiField field : fields) {
      With with = extensionPoint.addWith();
      String tagName = PluginFieldNameConverter.getAnnotationValue(field, Tag.class);
      if (tagName != null) {
        with.getTag().setStringValue(tagName);
      }
      else {
        String attributeName = PluginFieldNameConverter.getAttributeAnnotationValue(field);
        if (attributeName == null) {
          attributeName = field.getName();
        }
        if (attributeName.equals("forClass")) {
          continue;
        }
        with.getAttribute().setStringValue(attributeName);
      }
      String epName = extensionPoint.getName().getStringValue();
      String className = "";
      if (epName != null) {
        int pos = epName.lastIndexOf('.');
        epName = StringUtil.capitalize(pos >= 0 ? epName.substring(pos+1) : epName);
        PsiClass[] classesByName = PsiShortNamesCache.getInstance(project).getClassesByName(epName,
                                                                                            ProjectScope.getAllScope(project));
        if (classesByName.length == 1) {
          className = classesByName[0].getQualifiedName();
        }
      }
      with.getImplements().setStringValue(className);
      if (navTarget == null) {
        navTarget = with.getImplements().getXmlAttributeValue();
      }
    }
    if (navTarget != null) {
      PsiNavigateUtil.navigate(navTarget);
    }
  }
}
