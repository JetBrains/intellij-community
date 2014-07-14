/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.completion.smartEnter.fixers;

import com.intellij.lang.SmartEnterProcessorWithFixers;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.completion.smartEnter.GroovySmartEnterProcessor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

/**
 * User: Dmitry.Krasilschikov
 * Date: 08.08.2008
 */
public class GrMethodBodyFixer extends SmartEnterProcessorWithFixers.Fixer<GroovySmartEnterProcessor> {
  @Override
  public void apply(@NotNull Editor editor, @NotNull GroovySmartEnterProcessor processor, @NotNull PsiElement psiElement) {
    if (!(psiElement instanceof GrMethod)) return;
    GrMethod method = (GrMethod) psiElement;
    final PsiClass aClass = method.getContainingClass();
    if (aClass != null && aClass.isInterface() || method.hasModifierProperty(PsiModifier.ABSTRACT)) return;
    final GrCodeBlock body = method.getBlock();
    final Document doc = editor.getDocument();
    if (body != null) {
      // See IDEADEV-1093. This is quite hacky heuristic but it seem to be best we can do.
      String bodyText = body.getText();
      if (StringUtil.startsWithChar(bodyText, '{')) {
        final GrStatement[] statements = body.getStatements();
        if (statements.length > 0) {
//          [todo]
//          if (statements[0] instanceof PsiDeclarationStatement) {
//            if (PsiTreeUtil.getDeepestLast(statements[0]) instanceof PsiErrorElement) {
//              if (method.getContainingClass().getRBrace() == null) {
//                doc.insertString(body.getTextRange().getStartOffset() + 1, "\n}");
//              }
//            }
//          }
        }
      }
      return;
    }
    int endOffset = method.getTextRange().getEndOffset();
    if (StringUtil.endsWithChar(method.getText(), ';')) {
      doc.deleteString(endOffset - 1, endOffset);
      endOffset--;
    }
    doc.insertString(endOffset, "{\n}");
  }
}
