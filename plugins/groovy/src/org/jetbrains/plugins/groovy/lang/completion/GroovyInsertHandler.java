package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.DefaultInsertHandler;
import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.codeInsight.completion.LookupData;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.CaretModel;

/**
 * @author ven
 */
public class GroovyInsertHandler extends DefaultInsertHandler {
  public void handleInsert(CompletionContext context, int startOffset, LookupData data, LookupItem item, boolean signatureSelected, char completionChar) {
    Object obj = item.getObject();
    if (obj instanceof PsiMethod) {
      PsiParameter[] parameters = ((PsiMethod) obj).getParameterList().getParameters();
      Editor editor = context.editor;
      Document document = editor.getDocument();
      if (startOffset > 0 && document.getCharsSequence().charAt(startOffset - 1) == '&') return;   //closure creation
      CaretModel caretModel = editor.getCaretModel();
      int offset = caretModel.getOffset();
      if (parameters.length == 0 || parameters.length > 1) {
        document.insertString(offset, "()");
        if (parameters.length > 0) {
          caretModel.moveToOffset(offset + 1);
        } else {
          caretModel.moveToOffset(offset + 2);
        }
      } else {
        PsiType paramType = parameters[0].getType();
        if (paramType.getCanonicalText().equals("groovy.lang.Closure")) {
          document.insertString(offset, " {}");
          caretModel.moveToOffset(offset + 2);
        }
      }
      return;
    }
    super.handleInsert(context, startOffset, data, item, signatureSelected, completionChar);
  }
}
