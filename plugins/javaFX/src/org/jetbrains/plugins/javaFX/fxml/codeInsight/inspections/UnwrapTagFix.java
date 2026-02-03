// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml.codeInsight.inspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagChild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.JavaFXBundle;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;

public final class UnwrapTagFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance(UnwrapTagFix.class);
  private final String myTagName;

  public UnwrapTagFix(String tagName) {
    myTagName = tagName;
  }

  @Override
  public @NotNull String getName() {
    return JavaFXBundle.message("inspection.javafx.default.tag.unwrap.tag.fix.name", myTagName);
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaFXBundle.message("inspection.javafx.default.tag.unwrap.tag.fix.family.name");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    if (element != null) {
      final PsiFile containingFile = element.getContainingFile();
      LOG.assertTrue(containingFile != null && JavaFxFileTypeFactory.isFxml(containingFile),
                     containingFile == null ? "no containing file found" : "containing file: " + containingFile.getName());
      final XmlTag xmlTag = PsiTreeUtil.getParentOfType(element, XmlTag.class, false);
      if (xmlTag != null) {
        final XmlTag parentTag = xmlTag.getParentTag();
        final PsiElement[] children = PsiTreeUtil.getChildrenOfType(xmlTag, XmlTagChild.class);
        if (children != null && children.length > 0 && parentTag != null) {
          parentTag.addRange(children[0], children[children.length - 1]);
        }
        xmlTag.delete();
        if (parentTag != null) {
          CodeStyleManager.getInstance(project).reformat(parentTag, true);
        }
      }
    }
  }
}
