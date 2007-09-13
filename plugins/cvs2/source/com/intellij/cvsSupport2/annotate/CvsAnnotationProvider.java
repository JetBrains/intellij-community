/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.cvsSupport2.annotate;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.connections.CvsConnectionSettings;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutor;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutorCallback;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvsoperations.cvsAnnotate.AnnotateOperation;
import com.intellij.cvsSupport2.history.CvsHistoryProvider;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.peer.PeerFactory;

import java.io.File;
import java.util.List;

public class CvsAnnotationProvider implements AnnotationProvider{
  private final Project myProject;

  public CvsAnnotationProvider(final Project project) {
    myProject = project;
  }

  public FileAnnotation annotate(VirtualFile file) throws VcsException {
    final AnnotateOperation operation = AnnotateOperation.createForFile(new File(file.getPath()));
    final CvsOperationExecutor executor = new CvsOperationExecutor(true, myProject, ModalityState.defaultModalityState());
    executor.performActionSync(new CommandCvsHandler(CvsBundle.getAnnotateOperationName(), operation),
                               CvsOperationExecutorCallback.EMPTY);
    if (executor.getResult().hasNoErrors()) {
      final CvsHistoryProvider historyProvider = (CvsHistoryProvider) CvsVcs2.getInstance(myProject).getVcsHistoryProvider();
      final FilePath filePath = PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(file);
      final List<VcsFileRevision> revisions = historyProvider.createRevisions(filePath);
      return new CvsFileAnnotation(operation.getContent(), operation.getLineAnnotations(), revisions, file);
    } else {
      throw executor.getFirstError();
    }
  }

  public FileAnnotation annotate(VirtualFile file, VcsFileRevision revision) throws VcsException {
    final CvsConnectionSettings settings = CvsEntriesManager.getInstance().getCvsConnectionSettingsFor(file.getParent());
    return CvsVcs2.getInstance(myProject).createAnnotation(file, revision.getRevisionNumber().asString(), settings);
  }

  public boolean isAnnotationValid( VcsFileRevision rev ){
    return true;
  }
}
