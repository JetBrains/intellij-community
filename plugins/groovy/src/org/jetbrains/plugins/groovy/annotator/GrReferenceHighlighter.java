/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.annotator;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GrReferenceHighlighter extends TextEditorHighlightingPass {
  @NotNull private final GroovyFileBase myFile;
  @Nullable private List<HighlightInfo> myInfos = null;

  public GrReferenceHighlighter(@Nullable Document document, @NotNull GroovyFileBase file) {
    super(file.getProject(), document);
    myFile = file;
  }

  @Override
  public void doCollectInformation(@NotNull ProgressIndicator progress) {
    myInfos = new ArrayList<HighlightInfo>();
    myFile.accept(new GroovyRecursiveElementVisitor() {
      @Override
      public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
        super.visitReferenceExpression(referenceExpression);

        visit(referenceExpression);
        HighlightInfo info = GrUnresolvedAccessInspection.checkReferenceExpression(referenceExpression);
        if (info != null) {
          myInfos.add(info);
        }
      }

      @Override
      public void visitCodeReferenceElement(GrCodeReferenceElement refElement) {
        super.visitCodeReferenceElement(refElement);

        visit(refElement);

        HighlightInfo info = GrUnresolvedAccessInspection.checkCodeReferenceElement(refElement);
        if (info != null) {
          myInfos.add(info);
        }
      }

      @Override
      public void visitVariable(GrVariable variable) {
        super.visitVariable(variable);

        if (GroovyRefactoringUtil.isLocalVariable(variable) || variable instanceof GrParameter) {
          final TextAttributesKey attribute = GrHighlightUtil.getDeclarationHighlightingAttribute(variable);
          if (attribute != null) {
            final PsiElement nameElement = variable.getNameIdentifierGroovy();
            myInfos.add(HighlightInfo.createHighlightInfo(HighlightInfoType.INFORMATION, nameElement, null, attribute));
          }
        }
      }


      private void visit(GrReferenceElement element) {
        final PsiElement resolved = element.resolve();
        final TextAttributesKey attribute = GrHighlightUtil.getDeclarationHighlightingAttribute(resolved);
        if (attribute != null) {
          final PsiElement refNameElement = GrHighlightUtil.getElementToHighlight(element);
          myInfos.add(HighlightInfo.createHighlightInfo(HighlightInfoType.INFORMATION, refNameElement, null, attribute));
        }
      }
    });
  }

  @Override
  public void doApplyInformationToEditor() {
    if (myInfos == null) return;
    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, 0, myFile.getTextLength(), myInfos, getColorsScheme(), getId());
  }
}
