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
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.dom.AntDomTarget;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;

public class AntCreateTargetFix implements LocalQuickFix {
  private static final String TAG_NAME = "target";
  private static final String NAME_ATTR = "name";
  private final String myCanonicalText;

  public AntCreateTargetFix(String canonicalText) {
    myCanonicalText = canonicalText;
  }

  @NotNull
  public String getName() {
    return AntBundle.message("ant.create.target.intention.description", myCanonicalText);
  }

  @NotNull
  public String getFamilyName() {
    return AntBundle.message("ant.intention.create.target.family.name");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement psiElement = descriptor.getPsiElement();
    final PsiFile containingFile = psiElement.getContainingFile();

    Navigatable result = null;
    if (containingFile instanceof XmlFile) {
      final XmlFile xmlFile = (XmlFile)containingFile;
      final XmlTag rootTag = xmlFile.getRootTag();
      if (rootTag != null) {
        final XmlTag propTag = rootTag.createChildTag(TAG_NAME, rootTag.getNamespace(), "", false);
        propTag.setAttribute(NAME_ATTR, myCanonicalText);
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
          result = PsiNavigationSupport.getInstance().createNavigatable(project, containingFile.getVirtualFile(),
                                                                        ((XmlTag)generated).getValue().getTextRange()
                                                                                           .getEndOffset());
        }
        if (result == null && generated instanceof Navigatable) {
          result = (Navigatable)generated;
        }
      }
    }

    if (result != null) {
      result.navigate(true);
    }
  }
}
