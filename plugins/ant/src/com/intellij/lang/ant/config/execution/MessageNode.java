package com.intellij.lang.ant.config.execution;

import com.intellij.lang.ant.resources.AntBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;

final class MessageNode extends DefaultMutableTreeNode {
  private String[] myText;
  private AntMessage myMessage;
  private RangeMarker myRangeMarker;
  private Document myEditorDocument;
  private boolean myAllowToShowPosition;

  public MessageNode(final AntMessage message, final Project project, final boolean allowToShowPosition) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        myMessage = message;
        myText = message.getTextLines();
        if(myMessage.getFile() != null) {
          PsiFile psiFile = PsiManager.getInstance(project).findFile(myMessage.getFile());
          if (psiFile != null) {
            myEditorDocument = PsiDocumentManager.getInstance(project).getDocument(psiFile);
            if(myEditorDocument != null) {
              int line = myMessage.getLine();
              int column = myMessage.getColumn();
              if(line-1 >= 0 && line < myEditorDocument.getLineCount()) {
                int start = myEditorDocument.getLineStartOffset(line-1) + column-1;
                if(start >=0 && start < myEditorDocument.getTextLength()) {
                  myRangeMarker = myEditorDocument.createRangeMarker(start, start);
                }
              }
            }
          }
        }
        myAllowToShowPosition = allowToShowPosition;
      }
    });
  }

  public String[] getText() {
    return myText;
  }

  public VirtualFile getFile() {
    return myMessage.getFile();
  }

  public int getOffset() {
    if(myRangeMarker == null) {
      return -1;
    }
    return myRangeMarker.getStartOffset();
  }

  public AntBuildMessageView.MessageType getType() {
    return myMessage.getType();
  }

  public String getPositionString() {
    if(myRangeMarker == null || !myAllowToShowPosition) {
      return "";
    }
    return "(" + myMessage.getLine() + ", " + myMessage.getColumn() + ") ";
  }

  @Nullable
  public String getTypeString() {
    AntBuildMessageView.MessageType type = myMessage.getType();
    if (type == AntBuildMessageView.MessageType.BUILD) {
      return AntBundle.message("ant.build.message.node.prefix.text");
    }
    else if (type == AntBuildMessageView.MessageType.TARGET) {
      return AntBundle.message("ant.target.message.node.prefix.text");
    }
    else if (type == AntBuildMessageView.MessageType.TASK) {
      return AntBundle.message("ant.task.message.node.prefix.text");
    }
    return "";
  }

  public int getPriority() {
    return myMessage.getPriority();
  }
}

