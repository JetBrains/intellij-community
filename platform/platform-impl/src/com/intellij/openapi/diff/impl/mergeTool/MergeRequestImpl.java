/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.mergeTool;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.diff.impl.incrementalMerge.ChangeCounter;
import com.intellij.openapi.diff.impl.incrementalMerge.ui.MergePanel2;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;

public class MergeRequestImpl extends MergeRequest {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.mergeTool.MergeRequestImpl");
  private final DiffContent[] myDiffContents = new DiffContent[3];
  private String myWindowTitle = null;
  private String[] myVersionTitles = null;
  private int myResult = DialogWrapper.CANCEL_EXIT_CODE;
  private String myHelpId;
  @Nullable private final ActionButtonPresentation myActionButtonPresentation;

  public MergeRequestImpl(String left,
                          MergeVersion base,
                          String right,
                          Project project,
                          @Nullable final ActionButtonPresentation actionButtonPresentation) {
    super(project);
    myActionButtonPresentation = actionButtonPresentation;
    myDiffContents[0] = new SimpleContent(left);
    myDiffContents[1] = new MergeContent(base);
    myDiffContents[2] = new SimpleContent(right);
  }

  public MergeRequestImpl(String left,
                          String base,
                          String right,
                          Project project,
                          @Nullable final ActionButtonPresentation actionButtonPresentation) {
    super(project);
    myActionButtonPresentation = actionButtonPresentation;
    myDiffContents[0] = new SimpleContent(left);
    myDiffContents[1] = new SimpleContent(base);
    myDiffContents[2] = new SimpleContent(right);
  }

  public DiffContent[] getContents() { return myDiffContents; }

  public String[] getContentTitles() { return myVersionTitles; }
  public void setVersionTitles(String[] versionTitles) { myVersionTitles = versionTitles; }

  public String getWindowTitle() { return myWindowTitle; }
  public void setWindowTitle(String windowTitle) { myWindowTitle = windowTitle; }

  public void setResult(int result) {
    if (result == DialogWrapper.OK_EXIT_CODE) applyChanges();
    myResult = result;
  }

  public void applyChanges() {
    MergeContent mergeContent = getMergeContent();
    if (mergeContent != null) {
      mergeContent.applyChanges();
    }
  }

  public int getResult() { return myResult; }

  @Nullable
  private MergeContent getMergeContent() {
    if (myDiffContents [1] instanceof MergeContent) {
      return (MergeContent)myDiffContents[1];
    }
    return null;
  }

  @Nullable
  public DiffContent getResultContent() {
    return getMergeContent();
  }

  public void restoreOriginalContent() {
    final MergeContent mergeContent = getMergeContent();
    if (mergeContent == null) return;
    CommandProcessor.getInstance().executeCommand(
      getProject(),
      new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              FileDocumentManager.getInstance().getDocument(mergeContent.getFile()).setText(mergeContent.getOriginalText());
            }
          });
        }
      }, "", null);
  }

  public void setActions(final DialogBuilder builder, MergePanel2 mergePanel) {
    if (builder.getOkAction() == null && myActionButtonPresentation != null) {
      builder.addOkAction();
    }
    if (builder.getCancelAction() == null) {
      builder.addCancelAction();
    }

    if (myActionButtonPresentation != null) {
      builder.getOkAction().setText(myActionButtonPresentation.getName());

      builder.setOkActionEnabled(myActionButtonPresentation.isEnabled());
      final Action action = ((DialogBuilder.ActionDescriptor)builder.getOkAction()).getAction(builder.getDialogWrapper());
      String actionName = myActionButtonPresentation.getName();
      final int index = actionName.indexOf('&');
      final char mnemonic;
      if (index >= 0 && index < actionName.length() - 1) {
        mnemonic = actionName.charAt(index + 1);
        actionName = actionName.substring(0, index) + actionName.substring(index + 1);
      } else {
        mnemonic = 0;
      }
      action.putValue(Action.NAME, actionName);
      if (mnemonic > 0) {
        action.putValue(Action.MNEMONIC_KEY, Integer.valueOf(mnemonic));
      }
      builder.setOkOperation(new Runnable() {
        public void run() {
          final DataContext dataContext = DataManager.getInstance().getDataContext(builder.getCenterPanel());
          myActionButtonPresentation.run(PlatformDataKeys.DIFF_VIEWER.getData(dataContext));
          if (myActionButtonPresentation.closeDialog()) {
            builder.getDialogWrapper().close(DialogWrapper.OK_EXIT_CODE);
          }
        }
      });
    }
    if (getMergeContent() != null) {
      builder.setCancelOperation(new Runnable() {
        public void run() {
          if (Messages.showYesNoDialog(getProject(),
                                       DiffBundle.message("merge.dialog.exit.without.applying.changes.confirmation.message"),
                                       DiffBundle.message("cancel.visual.merge.dialog.title"),
                                       Messages.getQuestionIcon()) == 0) {
            builder.getDialogWrapper().close(DialogWrapper.CANCEL_EXIT_CODE);
          }
        }
      });
      new AllResolvedListener(mergePanel, builder.getDialogWrapper());
    }
  }

  public String getHelpId() {
    return myHelpId;
  }

  public void setHelpId(@Nullable @NonNls String helpId) {
    myHelpId = helpId;
  }

  private class MergeContent extends DiffContent {
    private final MergeVersion myTarget;
    private final Document myWorkingDocument;

    private MergeContent(MergeVersion target) {
      myTarget = target;
      myWorkingDocument = myTarget.createWorkingDocument(getProject());
      LOG.assertTrue(myWorkingDocument.isWritable());
    }

    public void applyChanges() {
      myTarget.applyText(myWorkingDocument.getText(), getProject());
    }

    public Document getDocument() { return myWorkingDocument; }

    public OpenFileDescriptor getOpenFileDescriptor(int offset) {
      VirtualFile file = getFile();
      if (file == null) return null;
      return new OpenFileDescriptor(getProject(), file, offset);
    }

    public VirtualFile getFile() {
      return myTarget.getFile();
    }

    public FileType getContentType() {
      return myTarget.getContentType();
    }

    public byte[] getBytes() throws IOException {
      return myTarget.getBytes();
    }

    public String getOriginalText() {
      return myTarget.getTextBeforeMerge();
    }
  }

  private class AllResolvedListener implements ChangeCounter.Listener, Runnable {
    private final MergePanel2 myMergePanel;
    private final DialogWrapper myDialogWrapper;
    private boolean myWasInvoked = false;

    private AllResolvedListener(MergePanel2 mergePanel, DialogWrapper dialogWrapper) {
      myMergePanel = mergePanel;
      myDialogWrapper = dialogWrapper;
      final ChangeCounter changeCounter = ChangeCounter.getOrCreate(myMergePanel.getMergeList());
      changeCounter.removeListener(this);
      changeCounter.addListener(this);
    }

    public void run() {
      if (!myActionButtonPresentation.closeDialog()) return;
      if (myWasInvoked) return;
      if (!getWholePanel().isDisplayable()) return;
      myWasInvoked = true;
      ChangeCounter.getOrCreate(myMergePanel.getMergeList()).removeListener(this);
      int doApply = Messages
        .showDialog(getProject(), DiffBundle.message("merge.all.changes.have.processed.save.and.finish.confirmation.text"),
                    DiffBundle.message("all.changes.processed.dialog.title"),
                    new String[]{DiffBundle.message("merge.save.and.finish.button"), DiffBundle.message("merge.continue.button")}, 0,
                    Messages.getQuestionIcon());
      if (doApply != 0) return;
      myDialogWrapper.close(DialogWrapper.OK_EXIT_CODE);
    }

    private JComponent getWholePanel() {
      return myMergePanel.getComponent();
    }

    public void onCountersChanged(ChangeCounter counter) {
      if (myWasInvoked) return;
      if (counter.getChangeCounter() != 0 || counter.getConflictCounter() != 0) return;
      ApplicationManager.getApplication().invokeLater(this);
    }
  }
}
