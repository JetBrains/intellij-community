package org.jetbrains.plugins.groovy.gant;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNamedElement;
import com.intellij.util.containers.HashSet;
import org.jetbrains.plugins.groovy.gant.AntTasksProvider;
import org.jetbrains.plugins.groovy.gant.GantUtils;
import org.jetbrains.plugins.groovy.extensions.completion.ContextSpecificInsertHandler;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;

import java.util.Set;

/**
 * @author ilyas
 */
public class GantPropertiesInsertHandler implements ContextSpecificInsertHandler {
  private final static Set<String> GANT_SCRIPT_CLOSURE_PROPERTIES = new HashSet<String>();

  static {
    GANT_SCRIPT_CLOSURE_PROPERTIES.add("target");
    GANT_SCRIPT_CLOSURE_PROPERTIES.add("setDefaultTarget");
  }


  public boolean isAcceptable(InsertionContext context, int startOffset, LookupElement item) {
    PsiFile file = context.getFile();

    Object obj = item.getObject();
    if (!GantUtils.isGantScriptFile(file)) return false;
    if (obj instanceof PsiMethod) return true;
    if (obj instanceof String) {
      String str = (String)obj;
      GrArgumentLabel[] targets = GantUtils.getScriptTargets(((GroovyFile)file));
      for (GrArgumentLabel target : targets) {
        if (target.getName().equals(str)) return true;
      }

      for (String classsName : AntTasksProvider.getInstance(file.getProject()).getAntTasks()) {
        if (StringUtil.decapitalize(classsName).equals(str)) {
          return true;
        }
      }
    }


    return GANT_SCRIPT_CLOSURE_PROPERTIES.contains(item.getLookupString());
  }

  public void handleInsert(InsertionContext context, int startOffset, LookupElement item) {
    Object obj = item.getObject();
    String name = null;
    if (obj instanceof String) {
      name = ((String)obj);
    }
    if (obj instanceof PsiNamedElement) {
      name = ((PsiNamedElement)obj).getName();
    } else if (obj instanceof GrArgumentLabel) {
      name = ((GrArgumentLabel)obj).getName();
    }
    Editor editor = context.getEditor();
    Document document = editor.getDocument();
    CaretModel caretModel = editor.getCaretModel();
    int offset = startOffset + name.length();
    final String text = document.getText();
    if (offset == document.getTextLength() ||
        !text.substring(offset).trim().startsWith("(")) {
      document.insertString(offset, "()");
    }
    caretModel.moveToOffset(offset + 1);
  }

}