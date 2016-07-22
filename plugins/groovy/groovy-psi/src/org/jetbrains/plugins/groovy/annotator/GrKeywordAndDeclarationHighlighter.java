/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.highlighter.GroovySyntaxHighlighter;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GrKeywordAndDeclarationHighlighter extends TextEditorHighlightingPass implements DumbAware {
  private final GroovyFileBase myFile;

  private List<HighlightInfo> toHighlight;

  public GrKeywordAndDeclarationHighlighter(GroovyFileBase file, Document document) {
    super(file.getProject(), document);
    myFile = file;
  }

  @Override
  public void doCollectInformation(@NotNull ProgressIndicator progress) {
    final List<HighlightInfo> result = new ArrayList<>();
    myFile.accept(new PsiRecursiveElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        IElementType tokenType = element.getNode().getElementType();
        if (TokenSets.KEYWORDS.contains(tokenType)) {
          if (highlightKeyword(element, tokenType)) {
            addInfo(element, GroovySyntaxHighlighter.KEYWORD);
          }
        }
        else if (!(element instanceof GroovyPsiElement || element instanceof PsiErrorElement)) {
          final TextAttributesKey attribute = getDeclarationAttribute(element);
          if (attribute != null) {
            addInfo(element, attribute);
          }
        }
        else {
          super.visitElement(element);
        }
      }

      private void addInfo(@NotNull PsiElement element, @NotNull TextAttributesKey attribute) {
        HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION).range(element);
        HighlightInfo info = builder.needsUpdateOnTyping(false).textAttributes(attribute).create();
        if (info != null) {
          result.add(info);
        }
      }
    });
    toHighlight = result;
  }

  private static boolean highlightKeyword(PsiElement element, IElementType token) {
    final PsiElement parent = element.getParent();
    if (parent instanceof GrArgumentLabel) return false; //don't highlight: print (void:'foo')

    if (PsiTreeUtil.getParentOfType(element, GrCodeReferenceElement.class) != null) {
      if (token == GroovyTokenTypes.kDEF || token == GroovyTokenTypes.kIN || token == GroovyTokenTypes.kAS) {
        return false; //It is allowed to name packages 'as', 'in' or 'def'
      }
    }
    else if (token == GroovyTokenTypes.kDEF && element.getParent() instanceof GrAnnotationNameValuePair) {
      return false;
    }
    else if (parent instanceof GrReferenceExpression && element == ((GrReferenceExpression)parent).getReferenceNameElement()) {
      if (token == GroovyTokenTypes.kSUPER && ((GrReferenceExpression)parent).getQualifier() == null) return true;
      if (token == GroovyTokenTypes.kTHIS && ((GrReferenceExpression)parent).getQualifier() == null) return true;
      return false; //don't highlight foo.def
    }

    return true;
  }


  @Override
  public void doApplyInformationToEditor() {
    if (toHighlight == null || myDocument == null) return;
    UpdateHighlightersUtil.setHighlightersToEditor(
      myProject, myDocument, 0, myFile.getTextLength(), toHighlight, getColorsScheme(), getId()
    );
  }

  @Nullable
  private static TextAttributesKey getDeclarationAttribute(PsiElement element) {
    if (element.getParent() instanceof GrAnnotation && element.getNode().getElementType() == GroovyTokenTypes.mAT) {
      return GroovySyntaxHighlighter.ANNOTATION;
    }

    PsiElement parent = element.getParent();
    if (!(parent instanceof GrNamedElement) || ((GrNamedElement)parent).getNameIdentifierGroovy() != element) {
      return null;
    }

    //don't highlight local vars and parameters here because their highlighting needs index.
    if (PsiUtil.isLocalVariable(parent) || parent instanceof GrParameter) return null;

    return GrHighlightUtil.getDeclarationHighlightingAttribute(parent, null);
  }
}
