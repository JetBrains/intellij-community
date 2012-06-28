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
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.highlighter.DefaultHighlighter;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class KeywordHighlighter extends TextEditorHighlightingPass {
  private final GroovyFile myFile;

  private List<HighlightInfo> toHighlight;

  protected KeywordHighlighter(GroovyFile file, Document document) {
    super(file.getProject(), document);
    myFile = file;
  }

  @Override
  public void doCollectInformation(@NotNull ProgressIndicator progress) {
    final List<HighlightInfo> result = new ArrayList<HighlightInfo>();
    myFile.accept(new PsiRecursiveElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        IElementType tokenType = element.getNode().getElementType();
        if (TokenSets.KEYWORDS.contains(tokenType)) {
          highlightKeyword(element, result, tokenType);
        }
        else {
          super.visitElement(element);
        }
      }
    });
    toHighlight = result;
  }

  private static void highlightKeyword(PsiElement element, List<HighlightInfo> result, IElementType token) {
    final PsiElement parent = element.getParent();
    if (parent instanceof GrArgumentLabel) return; //don't highlight: print (void:'foo')

    if (PsiTreeUtil.getParentOfType(element, GrCodeReferenceElement.class) != null) {
      if (token == GroovyTokenTypes.kDEF || token == GroovyTokenTypes.kIN || token == GroovyTokenTypes.kAS) {
        return; //It is allowed to name packages 'as', 'in' or 'def'
      }
    }
    else if (parent instanceof GrReferenceExpression && element == ((GrReferenceExpression)parent).getReferenceNameElement()) {
      return; //don't highlight foo.def
    }

    result.add(HighlightInfo.createHighlightInfo(HighlightInfoType.INFORMATION, element, null, DefaultHighlighter.KEYWORD_ATTRIBUTES));
  }


  @Override
  public void doApplyInformationToEditor() {
    if (toHighlight == null) return;
    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, 0, myFile.getTextLength(), toHighlight, getColorsScheme(), getId());
  }
}
