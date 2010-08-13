/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.ant.validation;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.dom.AntDomElement;
import com.intellij.lang.ant.dom.AntDomProject;
import com.intellij.lang.ant.dom.AntDomProperty;
import com.intellij.lang.ant.dom.AntDomRecursiveVisitor;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AntMissingPropertiesFileInspection extends AntInspection {

  @NonNls private static final String SHORT_NAME = "AntMissingPropertiesFileInspection";

  @Nls
  @NotNull
  public String getDisplayName() {
    return AntBundle.message("ant.missing.properties.file.inspection");
  }

  @NonNls
  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  @Nullable
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    final AntDomProject project = AntSupport.getAntDomProject(file);
    if (project != null) {
      final List<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>();
      checkElement(project, manager, problems, isOnTheFly);
      final int problemCount = problems.size();
      if (problemCount > 0) {
        return problems.toArray(new ProblemDescriptor[problemCount]);
      }
    }
    return null;
  }

  private static void checkElement(final AntDomElement tag, @NotNull final InspectionManager manager, final List<ProblemDescriptor> problems, final boolean isOnTheFly) {
    tag.accept(new AntDomRecursiveVisitor() {

      public void visitProperty(AntDomProperty property) {
        final String fileName = property.getFile().getStringValue();
        if (fileName != null) {
          final PsiFileSystemItem file = property.getFile().getValue();
          if (!(file instanceof PropertiesFile)) {
            final PsiElement psiElement = property.getFile().getXmlElement();
            if (psiElement != null)  {
              problems.add(manager.createProblemDescriptor(
                psiElement, 
                AntBundle.message("file.doesnt.exist", fileName), 
                isOnTheFly, 
                LocalQuickFix.EMPTY_ARRAY,
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
              );
            }
          }
        }
      }
    });
  }
}

