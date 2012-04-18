/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.editor.actions;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mGSTRING_LITERAL;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mSTRING_LITERAL;

/**
 * @author ilyas
 */
public class GroovyEditorActionUtil {
  private GroovyEditorActionUtil() {
  }

  public static void insertSpacesByGroovyContinuationIndent(Editor editor, Project project) {
    int indentSize = CodeStyleSettingsManager.getSettings(project).getContinuationIndentSize(GroovyFileType.GROOVY_FILE_TYPE);
    EditorModificationUtil.insertStringAtCaret(editor, StringUtil.repeatSymbol(' ', indentSize));
  }

  public static boolean isPlainStringLiteral(ASTNode node) {
    String text = node.getText();
    return text.length() < 3 && text.equals("''") || text.length() >= 3 && !text.startsWith("'''");
  }

  public static boolean isPlainGString(ASTNode node) {
    String text = node.getText();
    return text.length() < 3 && text.equals("\"\"") || text.length() >= 3 && !text.startsWith("\"\"\"");
  }

  public static boolean isMultilineStringElement(ASTNode node) {
    PsiElement element = node.getPsi();
    if (element instanceof GrLiteral) {
      if (element instanceof GrString) return !((GrString) element).isPlainString();
      return isSimpleStringLiteral(((GrLiteral) element)) && !isPlainStringLiteral(node) ||
          isSimpleGStringLiteral(((GrLiteral) element)) && !isPlainGString(node);
    }
    return false;
  }

  public static boolean isSimpleStringLiteral(GrLiteral literal) {
    PsiElement child = literal.getFirstChild();
    if (child != null && child.getNode() != null) {
      ASTNode node = child.getNode();
      assert node != null;
      return node.getElementType() == mSTRING_LITERAL;
    }
    return false;
  }

  public static boolean isSimpleGStringLiteral(GrLiteral literal) {
    PsiElement child = literal.getFirstChild();
    if (child != null && child.getNode() != null) {
      ASTNode node = child.getNode();
      assert node != null;
      return node.getElementType() == mGSTRING_LITERAL;
    }
    return false;
  }
}
