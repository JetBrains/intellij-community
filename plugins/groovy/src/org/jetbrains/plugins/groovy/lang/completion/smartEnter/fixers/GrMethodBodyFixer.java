package org.jetbrains.plugins.groovy.lang.completion.smartEnter.fixers;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import org.jetbrains.plugins.groovy.lang.completion.smartEnter.GroovySmartEnterProcessor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;

/**
 * User: Dmitry.Krasilschikov
 * Date: 08.08.2008
 */
public class GrMethodBodyFixer implements GrFixer {
  public void apply(Editor editor, GroovySmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (!(psiElement instanceof GrMethod)) return;
    GrMethod method = (GrMethod) psiElement;
    if (method.getContainingClass().isInterface() || method.hasModifierProperty(PsiModifier.ABSTRACT)) return;
    final GrCodeBlock body = method.getBlock();
    final Document doc = editor.getDocument();
    if (body != null) {
      // See IDEADEV-1093. This is quite hacky heuristic but it seem to be best we can do.
      String bodyText = body.getText();
      if (bodyText.startsWith("{")) {
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
