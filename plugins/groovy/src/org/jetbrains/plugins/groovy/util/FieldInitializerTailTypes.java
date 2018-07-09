package org.jetbrains.plugins.groovy.util;

import com.intellij.codeInsight.TailType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;

/**
 * @author Sergey Evdokimov
 */
public class FieldInitializerTailTypes extends TailType {

  public static final TailType EQ_CLOSURE = new FieldInitializerTailTypes("{}", 1);
  public static final TailType EQ_ARRAY = new FieldInitializerTailTypes("[]", 1);
  public static final TailType EQ_STRING_ARRAY = new FieldInitializerTailTypes("['']", 2);
  public static final TailType EQ_STRING = new FieldInitializerTailTypes("\"\"", 1);

  private final String myText;
  private final int myPosition;

  public FieldInitializerTailTypes(String text, int position) {
    myText = text;
    myPosition = position;
  }

  @Override
  public int processTail(Editor editor, int tailOffset) {
    CommonCodeStyleSettings styleSettings = getLocalCodeStyleSettings(editor, tailOffset);
    Document document = editor.getDocument();
    CharSequence chars = document.getCharsSequence();
    int textLength = chars.length();

    if (tailOffset < textLength - 1 && chars.charAt(tailOffset) == ' ' && chars.charAt(tailOffset + 1) == '='){
      return moveCaret(editor, tailOffset, 2);
    }
    if (tailOffset < textLength && chars.charAt(tailOffset) == '='){
      return moveCaret(editor, tailOffset, 1);
    }
    if (styleSettings.SPACE_AROUND_ASSIGNMENT_OPERATORS){
      document.insertString(tailOffset, " =");
      tailOffset = moveCaret(editor, tailOffset, 2);
    }
    else{
      document.insertString(tailOffset, "=");
      tailOffset = moveCaret(editor, tailOffset, 1);

    }
    if (styleSettings.SPACE_AROUND_ASSIGNMENT_OPERATORS){
      tailOffset = insertChar(editor, tailOffset, ' ');
    }

    document.insertString(tailOffset, myText);
    return moveCaret(editor, tailOffset, myPosition);
  }

}
