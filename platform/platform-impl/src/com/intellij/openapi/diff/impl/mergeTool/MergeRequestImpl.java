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

import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.MergeRequest;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

@Deprecated
public class MergeRequestImpl extends MergeRequest {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.mergeTool.MergeRequestImpl");

  private final DiffContent[] myDiffContents = new DiffContent[3];
  private String myWindowTitle = null;
  private String[] myVersionTitles = null;
  private int myResult = DialogWrapper.CANCEL_EXIT_CODE;
  private String myHelpId;

  public MergeRequestImpl(@NotNull String left,
                          @NotNull MergeVersion base,
                          @NotNull String right,
                          @Nullable Project project) {
    this(new SimpleContent(left),
         new MergeContent(base, project),
         new SimpleContent(right),
         project);
  }

  public MergeRequestImpl(@NotNull String left,
                          @NotNull String base,
                          @NotNull String right,
                          @Nullable FileType type,
                          @Nullable Project project) {
    this(new SimpleContent(left, type),
         new SimpleContent(base, type),
         new SimpleContent(right, type),
         project);
  }

  private MergeRequestImpl(@NotNull DiffContent left,
                           @NotNull DiffContent base,
                           @NotNull DiffContent right,
                           @Nullable Project project) {
    super(project);
    myDiffContents[0] = left;
    myDiffContents[1] = base;
    myDiffContents[2] = right;

    if (LOG.isDebugEnabled()) {
      VirtualFile file = base.getFile();
      LOG.debug(new Throwable(base.getClass() + " - writable: " + base.getDocument().isWritable() + ", contentType: " +
                              base.getContentType() + ", file: " + (file != null ? "valid - " + file.isValid() : "null")));
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
  public MergeContent getMergeContent() {
    if (myDiffContents[1] instanceof MergeContent) {
      return (MergeContent)myDiffContents[1];
    }
    return null;
  }

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
    public Navigatable getOpenFileDescriptor(int offset) {
      VirtualFile file = getFile();
      if (file == null) return null;
      return PsiNavigationSupport.getInstance().createNavigatable(myProject, file, offset);
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

    @NotNull
    public MergeVersion getMergeVersion() {
      return myTarget;
    }
  }
}
