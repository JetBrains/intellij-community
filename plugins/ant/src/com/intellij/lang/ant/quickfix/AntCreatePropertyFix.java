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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.dom.AntDomTarget;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

public class AntCreatePropertyFix implements LocalQuickFix {
  private static final String PROPERTY = "property";
  private static final String NAME_ATTR = "name";
  private static final String VALUE_ATTR = "value";
  private final String myCanonicalText;
  @Nullable
  private final PropertiesFile myPropFile;

  public AntCreatePropertyFix(String canonicalText, @Nullable PropertiesFile propertiesFile) {
    myCanonicalText = canonicalText;
    myPropFile = propertiesFile;
  }

  @NotNull
  public String getName() {
    if (myPropFile != null) {
      return AntBundle.message("create.property.in.file.quickfix.name", myCanonicalText, myPropFile.getName());
    }
    return AntBundle.message("create.property.quickfix.name", myCanonicalText);
  }

  @NotNull
  public String getFamilyName() {
    final String i18nName = AntBundle.message("ant.intention.create.property.family.name");
    return (i18nName == null) ? "Create property" : i18nName;
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement psiElement = descriptor.getPsiElement();
    final PsiFile containingFile = psiElement.getContainingFile();

    final FileModificationService modificationService = FileModificationService.getInstance();
    Navigatable result = null;
    if (myPropFile != null) {
      final VirtualFile vFile = myPropFile.getVirtualFile();

      boolean canModify = true;
      if (myPropFile instanceof PsiFile) {
        canModify = modificationService.prepareFileForWrite((PsiFile)myPropFile);
      }
      else if (vFile != null) {
        canModify = modificationService.prepareVirtualFilesForWrite(project, Collections.singleton(vFile));
      }

      if (canModify) {
        final IProperty generatedProperty = myPropFile.addProperty(myCanonicalText, "");
        result = vFile != null? new OpenFileDescriptor(project, vFile, generatedProperty.getPsiElement().getTextRange().getEndOffset()) : generatedProperty;
      }
    }
    else {
      if (containingFile instanceof XmlFile) {
        final XmlFile xmlFile = (XmlFile)containingFile;
        final XmlTag rootTag = xmlFile.getRootTag();
        if (rootTag != null && modificationService.prepareFileForWrite(xmlFile)) {
          final XmlTag propTag = rootTag.createChildTag(PROPERTY, rootTag.getNamespace(), null, false);
          propTag.setAttribute(NAME_ATTR, myCanonicalText);
          propTag.setAttribute(VALUE_ATTR, "");
          final DomElement contextElement = DomUtil.getDomElement(descriptor.getPsiElement());
          PsiElement generated;
          if (contextElement == null) {
            generated = rootTag.addSubTag(propTag, true);
          }
          else {
            final AntDomTarget containingTarget = contextElement.getParentOfType(AntDomTarget.class, false);
            final DomElement anchor = containingTarget != null ? containingTarget : contextElement;
            final XmlTag tag = anchor.getXmlTag();
            if (!rootTag.equals(tag)) {
              generated = tag.getParent().addBefore(propTag, tag);
            }
            else {
              generated = rootTag.addSubTag(propTag, true);
            }
          }
          if (generated instanceof XmlTag) {
            final XmlAttribute valueAttrib = ((XmlTag)generated).getAttribute(VALUE_ATTR);
            if (valueAttrib != null) {
              final XmlAttributeValue valueElement = valueAttrib.getValueElement();
              if (valueElement instanceof Navigatable) {
                result = (Navigatable)valueElement;
              }
            }
          }
          if (result == null && generated instanceof Navigatable) {
            result = (Navigatable)generated;
          }
        }
      }
    }

    if (result != null) {
      result.navigate(true);
    }
  }
}
