// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.dom.AntDomTarget;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class AntCreateTargetFix implements LocalQuickFix {
  private static final @NonNls String TAG_NAME = "target";
  private static final @NonNls String NAME_ATTR = "name";
  private final @NlsSafe String myCanonicalText;

  public AntCreateTargetFix(@NlsSafe String canonicalText) {
    myCanonicalText = canonicalText;
  }

  @Override
  public @NotNull String getName() {
    return AntBundle.message("ant.create.target.intention.description", myCanonicalText);
  }

  @Override
  public @NotNull String getFamilyName() {
    return AntBundle.message("ant.intention.create.target.family.name");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement psiElement = descriptor.getPsiElement();
    final PsiFile containingFile = psiElement.getContainingFile();

    Navigatable result = null;
    if (containingFile instanceof XmlFile xmlFile) {
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
