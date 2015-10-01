/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.diff.impl.incrementalMerge.ChangeCounter;
import com.intellij.openapi.diff.impl.incrementalMerge.ui.MergePanel2;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
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
  @Nullable private final ActionButtonPresentation myOkButtonPresentation;
  @Nullable private final ActionButtonPresentation myCancelButtonPresentation;

  public MergeRequestImpl(@NotNull String left,
                          @NotNull MergeVersion base,
                          @NotNull String right,
                          @Nullable Project project,
                          @Nullable final ActionButtonPresentation okButtonPresentation,
                          @Nullable final ActionButtonPresentation cancelButtonPresentation) {
    this(new SimpleContent(left), new MergeContent(base, project), new SimpleContent(right), project, okButtonPresentation,
         cancelButtonPresentation);
  }

  public MergeRequestImpl(@NotNull DiffContent left,
                          @NotNull MergeVersion base,
                          @NotNull DiffContent right,
                          @Nullable Project project,
                          @Nullable final ActionButtonPresentation okButtonPresentation,
                          @Nullable final ActionButtonPresentation cancelButtonPresentation) {
    this(left, new MergeContent(base, project), right, project, okButtonPresentation, cancelButtonPresentation);
  }

  public MergeRequestImpl(@NotNull String left,
                          @NotNull String base,
                          @NotNull String right,
                          @Nullable Project project,
                          @Nullable final ActionButtonPresentation okButtonPresentation,
                          @Nullable final ActionButtonPresentation cancelButtonPresentation) {
    this(left, base, right, null, project, okButtonPresentation, cancelButtonPresentation);
  }

  public MergeRequestImpl(@NotNull String left,
                          @NotNull String base,
                          @NotNull String right,
                          @Nullable FileType type,
                          @Nullable Project project,
                          @Nullable final ActionButtonPresentation okButtonPresentation,
                          @Nullable final ActionButtonPresentation cancelButtonPresentation) {
    this(new SimpleContent(left, type),
         new SimpleContent(base, type),
         new SimpleContent(right, type),
         project, okButtonPresentation, cancelButtonPresentation);
  }

  private MergeRequestImpl(@NotNull DiffContent left,
                           @NotNull DiffContent base,
                           @NotNull DiffContent right,
                           @Nullable Project project,
                           @Nullable final ActionButtonPresentation okButtonPresentation,
                           @Nullable final ActionButtonPresentation cancelButtonPresentation) {
    super(project);
    myOkButtonPresentation = okButtonPresentation;
    myCancelButtonPresentation = cancelButtonPresentation;
    myDiffContents[0] = left;
    myDiffContents[1] = base;
    myDiffContents[2] = right;

    if (MergeTool.LOG.isDebugEnabled()) {
      VirtualFile file = base.getFile();
      MergeTool.LOG.debug(new Throwable(base.getClass() + " - writable: " + base.getDocument().isWritable() + ", contentType: " +
                                        base.getContentType() + ", file: " + (file != null ? "valid - " + file.isValid() : "null") +
                                        ", presentation: " + myOkButtonPresentation + "-" + myCancelButtonPresentation));
    }
  }

  @Override
  @NotNull
  public DiffContent[] getContents() {
    return myDiffContents;
  }

  @Override
  public String[] getContentTitles() {
    return myVersionTitles;
  }

  @Override
  public void setVersionTitles(String[] versionTitles) {
    myVersionTitles = versionTitles;
  }

  @Override
  public String getWindowTitle() {
    return myWindowTitle;
  }

  @Override
  public void setWindowTitle(String windowTitle) {
    myWindowTitle = windowTitle;
  }

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

  @Override
  public int getResult() {
    return myResult;
  }

  @Nullable
  private MergeContent getMergeContent() {
    if (myDiffContents[1] instanceof MergeContent) {
      return (MergeContent)myDiffContents[1];
    }
    return null;
  }

  @Override
  @Nullable
  public DiffContent getResultContent() {
    return getMergeContent();
  }

  @Override
  public void restoreOriginalContent() {
    final MergeContent mergeContent = getMergeContent();
    if (mergeContent == null) return;
    mergeContent.restoreOriginalContent();
  }

  private static void configureAction(DialogBuilder builder,
                                      DialogBuilder.CustomizableAction customizableAction,
                                      ActionButtonPresentation presentation) {
    customizableAction.setText(presentation.getName());

    String actionName = presentation.getName();
    final int index = actionName.indexOf('&');
    final char mnemonic;
    if (index >= 0 && index < actionName.length() - 1) {
      mnemonic = actionName.charAt(index + 1);
      actionName = actionName.substring(0, index) + actionName.substring(index + 1);
    }
    else {
      mnemonic = 0;
    }
    final Action action = ((DialogBuilder.ActionDescriptor)customizableAction).getAction(builder.getDialogWrapper());
    action.putValue(Action.NAME, actionName);
    if (mnemonic > 0) {
      action.putValue(Action.MNEMONIC_KEY, Integer.valueOf(mnemonic));
    }
  }

  public void setActions(final DialogBuilder builder, MergePanel2 mergePanel) {
    setActions(builder, mergePanel, null);
  }

  public void setActions(final DialogBuilder builder, MergePanel2 mergePanel, final Convertor<DialogWrapper, Boolean> preOkHook) {
    builder.removeAllActions(); // otherwise dialog will get default actions (OK, Cancel)

    if (myOkButtonPresentation != null) {
      if (builder.getOkAction() == null) {
        builder.addOkAction();
      }

      configureAction(builder, builder.getOkAction(), myOkButtonPresentation);
      builder.setOkOperation(new Runnable() {
        @Override
        public void run() {
          if (preOkHook != null && !preOkHook.convert(builder.getDialogWrapper())) return;
          myOkButtonPresentation.run(builder.getDialogWrapper());
        }
      });
    }

    if (myCancelButtonPresentation != null) {
      if (builder.getCancelAction() == null) {
        builder.addCancelAction();
      }

      configureAction(builder, builder.getCancelAction(), myCancelButtonPresentation);
      builder.setCancelOperation(new Runnable() {
        @Override
        public void run() {
          myCancelButtonPresentation.run(builder.getDialogWrapper());
        }
      });
    }

    if (getMergeContent() != null && mergePanel.getMergeList() != null) {
      new AllResolvedListener(mergePanel, builder.getDialogWrapper());
    }
  }

  public String getHelpId() {
    return myHelpId;
  }

  @Override
  public void setHelpId(@Nullable @NonNls String helpId) {
    myHelpId = helpId;
  }

  public static class MergeContent extends DiffContent {
    @NotNull private final MergeVersion myTarget;
    private final Document myWorkingDocument;
    private final Project myProject;

    public MergeContent(@NotNull MergeVersion target, Project project) {
      myTarget = target;
      myProject = project;
      myWorkingDocument = myTarget.createWorkingDocument(project);
      LOG.assertTrue(myWorkingDocument.isWritable());
    }

    public void applyChanges() {
      myTarget.applyText(myWorkingDocument.getText(), myProject);
    }

    @Override
    public Document getDocument() {
      return myWorkingDocument;
    }

    @Override
    public OpenFileDescriptor getOpenFileDescriptor(int offset) {
      VirtualFile file = getFile();
      if (file == null) return null;
      return new OpenFileDescriptor(myProject, file, offset);
    }

    @Override
    public VirtualFile getFile() {
      return myTarget.getFile();
    }

    @Override
    @Nullable
    public FileType getContentType() {
      return myTarget.getContentType();
    }

    @Override
    public byte[] getBytes() throws IOException {
      return myTarget.getBytes();
    }

    public void restoreOriginalContent() {
      myTarget.restoreOriginalContent(myProject);
    }
  }

  private static class AllResolvedListener implements ChangeCounter.Listener, Runnable {
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

    @Override
    public void run() {
      if (myWasInvoked) return;
      if (!getWholePanel().isDisplayable()) return;
      myWasInvoked = true;
      ChangeCounter.getOrCreate(myMergePanel.getMergeList()).removeListener(this);
      int doApply = Messages
        .showOkCancelDialog(getWholePanel(), DiffBundle.message("merge.all.changes.have.processed.save.and.finish.confirmation.text"),
                            DiffBundle.message("all.changes.processed.dialog.title"),
                            DiffBundle.message("merge.save.and.finish.button"), DiffBundle.message("merge.continue.button"),
                            Messages.getQuestionIcon());
      if (doApply != Messages.OK) return;
      myDialogWrapper.close(DialogWrapper.OK_EXIT_CODE);
    }

    private JComponent getWholePanel() {
      return myMergePanel.getComponent();
    }

    @Override
    public void onCountersChanged(ChangeCounter counter) {
      if (myWasInvoked) return;
      if (counter.getChangeCounter() != 0 || counter.getConflictCounter() != 0) return;
      ApplicationManager.getApplication().invokeLater(this);
    }
  }
}
