package com.intellij.structuralsearch.plugin.ui;

import com.intellij.codeInsight.hint.TooltipController;
import com.intellij.codeInsight.template.impl.TemplateImplUtil;
import com.intellij.codeInsight.template.impl.Variable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Apr 23, 2004
 * Time: 5:20:56 PM
 * To change this template use File | Settings | File Templates.
 */
public class SubstitutionShortInfoHandler implements DocumentListener, EditorMouseMotionListener, CaretListener {

  private long modificationTimeStamp;
  private final ArrayList<Variable> variables = new ArrayList<>();
  private final Editor editor;
  public static final Key<Configuration> CURRENT_CONFIGURATION_KEY = Key.create("SS.CurrentConfiguration");

  SubstitutionShortInfoHandler(@NotNull Editor _editor) {
    editor = _editor;
  }

  public void beforeDocumentChange(DocumentEvent event) {
  }

  public void documentChanged(DocumentEvent event) {
  }

  public void mouseMoved(EditorMouseEvent e) {
    LogicalPosition position  = editor.xyToLogicalPosition( e.getMouseEvent().getPoint() );

    handleInputFocusMovement(position);
  }

  private void handleInputFocusMovement(LogicalPosition position) {
    checkModelValidity();
    String text = "";
    final int offset = editor.logicalPositionToOffset(position);
    final int length = editor.getDocument().getTextLength();
    final CharSequence elements = editor.getDocument().getCharsSequence();

    int start = offset-1;
    int end = -1;
    while(start >=0 && Character.isJavaIdentifierPart(elements.charAt(start)) && elements.charAt(start)!='$') start--;

    if (start >=0 && elements.charAt(start)=='$') {
      end = offset;

      while(end < length && Character.isJavaIdentifierPart(elements.charAt(end)) && elements.charAt(end)!='$') end++;
      if (end < length && elements.charAt(end)=='$') {
        String varname = elements.subSequence(start + 1, end).toString();
        Variable foundVar = null;

        for (final Variable var : variables) {
          if (var.getName().equals(varname)) {
            foundVar = var;
            break;
          }
        }

        if (foundVar!=null) {
          text = UIUtil.getShortParamString(editor.getUserData(CURRENT_CONFIGURATION_KEY),varname);
        }
      }
    }

    if (text.length() > 0) {
      UIUtil.showTooltip(editor, start, end + 1, text);
    }
    else {
      TooltipController.getInstance().cancelTooltips();
    }
  }

  private void checkModelValidity() {
    Document document = editor.getDocument();
    if (modificationTimeStamp != document.getModificationStamp()) {
      variables.clear();
      variables.addAll(TemplateImplUtil.parseVariables(document.getCharsSequence()).values());
      modificationTimeStamp = document.getModificationStamp();
    }
  }

  public void mouseDragged(EditorMouseEvent e) {
  }

  public void caretPositionChanged(CaretEvent e) {
    handleInputFocusMovement(e.getNewPosition());
  }

  @Override
  public void caretAdded(CaretEvent e) {
  }

  @Override
  public void caretRemoved(CaretEvent e) {
  }

  public ArrayList<Variable> getVariables() {
    checkModelValidity();
    return variables;
  }
}
