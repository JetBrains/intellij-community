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
package com.intellij.lang.ant.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Make use of com.intellij.lang.ant.psi.impl.AntFileImpl
 */
public class AntCreatePropertyFix implements LocalQuickFix {
  @NonNls public static final String PROPERTY = "property";
  @NonNls public static final String NAME_ATTR = "name";
  @NonNls public static final String VALUE_ATTR = "value";
  private final String canonicalText;
  private final PsiFileSystemItem propFile;

  public AntCreatePropertyFix(String canonicalText, PsiFileSystemItem propertiesFile) {
    this.canonicalText = canonicalText;
    this.propFile = propertiesFile;
  }

  @NotNull
  public String getName() {
    StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append(getFamilyName());
      builder.append(" '");
      builder.append(canonicalText);
      builder.append("'");
      if (propFile != null) {
        builder.append(' ');
        builder.append(AntBundle.message("text.in.the.file", propFile.getName()));
      }
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  @NotNull
  public String getFamilyName() {
    final String i18nName = AntBundle.message("ant.intention.create.property.family.name");
    return (i18nName == null) ? "Create property" : i18nName;
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement psiElement = descriptor.getPsiElement();
    final PsiFile containingFile = psiElement.getContainingFile();

    DomElement domElement = DomUtil.getDomElement(descriptor.getPsiElement());
    Navigatable result;

    if (propFile != null) {
      PropertiesFile propertiesFile = (PropertiesFile)propFile;
      result = propertiesFile.addProperty(canonicalText, "");
    }
    else {
      final XmlFile xmlFile = (XmlFile)containingFile;
      XmlTag rootTag = xmlFile.getRootTag();
      XmlTag propTag = rootTag.createChildTag(PROPERTY, rootTag.getNamespace(), null, false);
      propTag.setAttribute(NAME_ATTR, canonicalText);
      propTag.setAttribute(VALUE_ATTR, "");
      DomElement anchor = domElement.getParent();
      if (anchor == null) {
        result = (Navigatable)rootTag.add(propTag);
      }
      else {
        result = (Navigatable)anchor.getXmlTag().getParent().addBefore(propTag, anchor.getXmlElement());
      }
    }
    if (result != null) {
      result.navigate(true);
    }
  }
}
