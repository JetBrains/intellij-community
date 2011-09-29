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
package com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutor;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutorCallback;
import com.intellij.cvsSupport2.cvsExecution.ModalityContextImpl;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvsoperations.common.CvsOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsLog.LogOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.ui.SelectTagDialog;
import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.cvsIntegration.CvsResult;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.command.log.LogInformation;
import org.netbeans.lib.cvsclient.command.log.Revision;
import org.netbeans.lib.cvsclient.command.log.SymbolicName;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

/**
 * author: lesya
 */
public class TagsHelper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.TagsHelper");

  private TagsHelper() {
  }

  @Nullable
  public static String chooseBranch(TagsProvider tagsProvider, Project project) {
    try {
      BranchesProvider branchesProvider = getBranchesProvider(tagsProvider.getOperation(), project);
      return chooseFrom(branchesProvider.getAllBranches(), branchesProvider.getAllRevisions());
    }
    catch (VcsException e1) {
      showErrorMessage(e1);
      return null;
    }
  }

  @Nullable
  public static String chooseBranch(Collection<FilePath> files, Project project) {
    try {
      return chooseFrom(collectAllBranches(files, project), new ArrayList<CvsRevisionNumber>());
    }
    catch (VcsException e1) {
      showErrorMessage(e1);
      return null;
    }
  }

  public static void addChooseBranchAction(final TextFieldWithBrowseButton field, final Collection<FilePath> files, final Project project) {
    field.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        String branchName = TagsHelper.chooseBranch(files, project);
        if (branchName != null) field.setText(branchName);
      }
    });
  }

  public static Collection<String> getAllBranches(List<LogInformation> log) {
    HashSet<String> branches = new HashSet<String>();

    for (final LogInformation logInformation : log) {
      collectBranches(logInformation, branches);
    }

    return branches;

  }

  private static void collectBranches(LogInformation logInformation,
                                      HashSet<String> branches) {
    List<SymbolicName> allSymbolicNames = logInformation.getAllSymbolicNames();
    for (final SymbolicName symbolicName : allSymbolicNames) {
      branches.add(symbolicName.getName());
    }
  }

  private static void collectRevisions(LogInformation logInformation, ArrayList<CvsRevisionNumber> result) {
    for (final Revision revision : logInformation.getRevisionList()) {
      result.add(new CvsRevisionNumber(revision.getNumber()));
    }
  }

  private static BranchesProvider getBranchesProvider(CvsOperation operation, Project project) throws VcsException {
    LOG.assertTrue(operation instanceof BranchesProvider);
    CvsOperationExecutor executor = new CvsOperationExecutor(true, project,
                                                             new ModalityContextImpl(ModalityState.defaultModalityState()
                                                             ));
    CommandCvsHandler handler = new CommandCvsHandler(CvsBundle.message("load.tags.operation.name"), operation, true);
    executor.performActionSync(handler, CvsOperationExecutorCallback.EMPTY);
    CvsResult executionResult = executor.getResult();
    if (!executionResult.hasNoErrors()) throw executionResult.composeError();
    return (BranchesProvider)operation;
  }

  private static Collection<String> collectAllBranches(Collection<FilePath> files,
                                                       Project project) throws VcsException {
    ArrayList<String> result = new ArrayList<String>();
    if (files.isEmpty()) {
      return result;
    }
    return getBranchesProvider(new LogOperation(files), project).getAllBranches();
  }

  private static void showErrorMessage(VcsException e1) {
    Messages.showErrorDialog(CvsBundle.message("error.message.cannot.load.tags", e1.getLocalizedMessage()),
                             CvsBundle.message("operation.name.select.tag"));
  }

  @Nullable
  private static String chooseFrom(Collection<String> tags, Collection<CvsRevisionNumber> revisions) {
    if (tags == null) return null;
    Collection<String> revisionsNames = collectSortedRevisionsNames(revisions);

    if (tags.isEmpty() && revisionsNames.isEmpty()) {
      Messages.showMessageDialog(CvsBundle.message("message.no.tags.found"), CvsBundle.message("operation.name.select.tag"),
                                 Messages.getInformationIcon());
      return null;
    }
    final SelectTagDialog selectTagDialog = new SelectTagDialog(collectSortedTags(tags), revisionsNames);
    selectTagDialog.show();
    if (selectTagDialog.isOK()) {
      return selectTagDialog.getTag();
    }

    return null;
  }

  private static Collection<String> collectSortedTags(Collection<String> tags) {
    ArrayList<String> result = new ArrayList<String>(tags);
    Collections.sort(result);
    return result;
  }

  private static Collection<String> collectSortedRevisionsNames(Collection<CvsRevisionNumber> revisions) {
    if (revisions == null) return new ArrayList<String>();
    ArrayList<CvsRevisionNumber> list = new ArrayList<CvsRevisionNumber>(revisions);
    Collections.sort(list, new Comparator<CvsRevisionNumber>() {
      @Override
      public int compare(CvsRevisionNumber o, CvsRevisionNumber o1) {
        return o.compareTo(o1);
      }

    });
    ArrayList<String> result = new ArrayList<String>();
    for (final CvsRevisionNumber aList : list) {
      result.add(aList.toString());
    }
    return result;
  }

  public static Collection<CvsRevisionNumber> getAllRevisions(List<LogInformation> logs) {
    ArrayList<CvsRevisionNumber> result = new ArrayList<CvsRevisionNumber>();
    for (final LogInformation log : logs) {
      collectRevisions(log, result);
    }

    return result;
  }


}
