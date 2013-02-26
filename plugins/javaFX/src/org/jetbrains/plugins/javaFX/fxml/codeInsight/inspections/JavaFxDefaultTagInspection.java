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

import com.intellij.codeInspection.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxPropertyElementDescriptor;

/**
 * User: anna
 */
public class JavaFxDefaultTagInspection extends XmlSuppressableInspectionTool{
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new XmlElementVisitor() {
      @Override
      public void visitXmlTag(XmlTag tag) {
        super.visitXmlTag(tag);
        final XmlElementDescriptor descriptor = tag.getDescriptor();
        if (descriptor instanceof JavaFxPropertyElementDescriptor) {
          final XmlTag parentTag = tag.getParentTag();
          if (parentTag != null) {
            final String propertyName = JavaFxPsiUtil.getDefaultPropertyName(JavaFxPsiUtil.getTagClass(parentTag));
            final String tagName = tag.getName();
            if (Comparing.strEqual(tagName, propertyName)) {
              holder.registerProblem(tag.getFirstChild(), 
                                     "Default property tag could be removed", 
                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                     new UnwrapTagFix(tagName));
            }
          }
        }
      }
    };
  }
}
