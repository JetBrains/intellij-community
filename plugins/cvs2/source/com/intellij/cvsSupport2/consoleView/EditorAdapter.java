package com.intellij.cvsSupport2.consoleView;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.util.Alarm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/**
 * author: lesya
 */

class Line {
  private final String myValue;
  private final TextAttributes myTextAttributes;

  public Line(String value, TextAttributes textAttributes) {
    myValue = value.replaceAll("\r", "") + "\n";
    myTextAttributes = textAttributes;
  }

  public String getValue() {
    return myValue;
  }

  public TextAttributes getAttributes() {
    return myTextAttributes;
  }
}

public class EditorAdapter {

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.consoleView.EditorAdapter");

  private final Editor myEditor;

  private Alarm myFlushAlarm = new Alarm();
  private Collection myLines = new ArrayList();
  private final Project myProject;

  private final Runnable myFlushDeferredRunnable = new Runnable() {
    public void run() {
      flushStoredLines();
    }
  };

  private synchronized void flushStoredLines() {
    ApplicationManager.getApplication().runWriteAction(writingCommand());
    myLines = new ArrayList();
  }


  public EditorAdapter(Editor editor, Project project) {
    myEditor = editor;
    myProject = project;
    LOG.assertTrue(myEditor.isViewer());
  }

  public synchronized void appendString(String string, TextAttributes attrs) {
    myLines.add(new Line(string, attrs));
    if (myFlushAlarm.getActiveRequestCount() == 0) {
      myFlushAlarm.addRequest(myFlushDeferredRunnable, 200, ModalityState.NON_MMODAL);
    }
  }

  private Runnable writingCommand() {
    final Runnable command = new Runnable() {
          public void run() {

            Document document = myEditor.getDocument();

            if (document.getMarkupModel(myProject) == null) return;

            StringBuffer buffer = new StringBuffer();
            for (Iterator each = myLines.iterator(); each.hasNext();) {
              Line line = (Line) each.next();
              buffer.append(line.getValue());
            }
            int endBefore = document.getTextLength();
            int endBeforeLine = endBefore;
            document.insertString(endBefore, buffer.toString());
            for (Iterator each = myLines.iterator(); each.hasNext();) {
              Line line = (Line) each.next();
              myEditor.getMarkupModel().addRangeHighlighter(endBeforeLine,
                  endBeforeLine + line.getValue().length(), HighlighterLayer.ADDITIONAL_SYNTAX,
                  line.getAttributes(), HighlighterTargetArea.EXACT_RANGE);
              endBeforeLine += line.getValue().length();
            }
            shiftCursorToTheEndOfDocument();
          }
        };
    return new Runnable() {
      public void run() {
        CommandProcessor.getInstance().executeCommand(myProject, command, "", null);
      }
    };
  }

  private void shiftCursorToTheEndOfDocument() {
    myEditor.getCaretModel().moveToOffset(myEditor.getDocument().getTextLength());
    myEditor.getSelectionModel().removeSelection();
    myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }
}
