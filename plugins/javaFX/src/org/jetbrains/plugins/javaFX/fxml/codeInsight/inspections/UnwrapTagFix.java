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
package org.jetbrains.plugins.javaFX.fxml.codeInsight.inspections;

import com.intellij.codeInsight.FileModificationService;
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
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;

/**
* User: anna
*/
public class UnwrapTagFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#" + UnwrapTagFix.class.getName());
  private final String myTagName;

  public UnwrapTagFix(String tagName) {
    myTagName = tagName;
  }

  @NotNull
  @Override
  public String getName() {
    return "Unwrap '" + myTagName + "'";
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getName();
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    if (element != null) {
      final PsiFile containingFile = element.getContainingFile();
      LOG.assertTrue(containingFile != null && JavaFxFileTypeFactory.isFxml(containingFile), containingFile == null ? "no containing file found" : "containing file: " + containingFile.getName());
      final XmlTag xmlTag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
      if (xmlTag != null) {
        final XmlTag parentTag = xmlTag.getParentTag();
        final PsiElement[] children = PsiTreeUtil.getChildrenOfType(xmlTag, XmlTagChild.class);
        if (children != null) {
          if (!FileModificationService.getInstance().preparePsiElementsForWrite(element)) return;
          if (children.length > 0) {
            parentTag.addRange(children[0], children[children.length - 1]);
          }
          xmlTag.delete();
          CodeStyleManager.getInstance(project).reformat(parentTag);
        }
      }
    }
  }
}
