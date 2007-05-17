/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 28.11.2006
 * Time: 20:38:08
 */
package com.intellij.cvsSupport2.changeBrowser;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutor;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutorCallback;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.openapi.cvsIntegration.CvsResult;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.ChangesBrowserSettingsEditor;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.DataOutput;
import java.io.IOException;
import java.io.DataInput;
import java.util.*;

public class CvsCommittedChangesProvider implements CachingCommittedChangesProvider<CvsChangeList, ChangeBrowserSettings> {
  private final Project myProject;

  @NonNls private static final String INVALID_OPTION_S = "invalid option -- S";

  public CvsCommittedChangesProvider(Project project) {
    myProject = project;
  }

  public ChangeBrowserSettings createDefaultSettings() {
    return new ChangeBrowserSettings();
  }

  public ChangesBrowserSettingsEditor<ChangeBrowserSettings> createFilterUI(final boolean showDateFilter) {
    return new CvsVersionFilterComponent(showDateFilter);
  }

  @Nullable
  public CvsRepositoryLocation getLocationFor(final VirtualFile root) {
    if (!CvsUtil.fileIsUnderCvs(new File(root.getPresentableUrl()))) {
      return null;
    }
    final VirtualFile rootDir = root.isDirectory() ? root : root.getParent();
    final String module = CvsUtil.getModuleName(root);
    final CvsEnvironment connectionSettings = CvsEntriesManager.getInstance().getCvsConnectionSettingsFor(rootDir);
    return new CvsRepositoryLocation(rootDir, connectionSettings, module);
  }

  public ChangeListColumn[] getColumns() {
    return new ChangeListColumn[] { ChangeListColumn.DATE, ChangeListColumn.NAME, ChangeListColumn.DESCRIPTION, BRANCH_COLUMN };
  }

  public List<CvsChangeList> getCommittedChanges(ChangeBrowserSettings settings, RepositoryLocation location, final int maxCount) throws VcsException {
    CvsRepositoryLocation cvsLocation = (CvsRepositoryLocation) location;
    return loadCommittedChanges(settings, cvsLocation.getModuleName(), cvsLocation.getEnvironment(), cvsLocation.getRootFile());
  }

  private List<CvsChangeList> loadCommittedChanges(final ChangeBrowserSettings settings,
                                                   final String module,
                                                   final CvsEnvironment connectionSettings,
                                                   final VirtualFile rootFile) throws VcsException {
    final CvsChangeListsBuilder builder = new CvsChangeListsBuilder(module, connectionSettings, myProject, rootFile);
    Date dateTo = settings.getDateBeforeFilter();
    Date dateFrom = settings.getDateAfterFilter();
    if (dateFrom == null) {
      final Calendar calendar = Calendar.getInstance();
      calendar.set(1970, 2, 2);
      dateFrom = calendar.getTime();
    }
    final List<LogInformationWrapper> log = new ArrayList<LogInformationWrapper>();
    final CvsResult executionResult = runRLogOperation(connectionSettings, module, dateFrom, dateTo, log);

    if (executionResult.isCanceled()) {
      throw new ProcessCanceledException();
    }
    else if (!executionResult.hasNoErrors()) {
      throw executionResult.composeError();
    }
    else {
      builder.addLogs(log);
      final List<CvsChangeList> versions = builder.getVersions();
      settings.filterChanges(versions);
      return versions;
    }
  }

  private CvsResult runRLogOperation(final CvsEnvironment settings,
                                     final String module,
                                     final Date dateFrom,
                                     final Date dateTo,
                                     final List<LogInformationWrapper> log) {
    LoadHistoryOperation operation = new LoadHistoryOperation(settings, module, dateFrom, dateTo, log);

    CvsOperationExecutor executor = new CvsOperationExecutor(myProject);
    executor.performActionSync(new CommandCvsHandler(CvsBundle.message("browse.changes.load.history.progress.title"), operation),
                               CvsOperationExecutorCallback.EMPTY);

    final CvsResult executionResult = executor.getResult();

    for (VcsException error : executionResult.getErrors()) {
      for (String message : error.getMessages()) {
        if (message.indexOf(INVALID_OPTION_S) >= 0) {
          LoadHistoryOperation.doesNotSuppressEmptyHeaders(settings);
          log.clear();
          return runRLogOperation(settings, module, dateFrom, dateTo, log);
        }
      }
    }

    return executionResult;
  }

  public int getFormatVersion() {
    return 2;
  }

  public void writeChangeList(final DataOutput stream, final CvsChangeList list) throws IOException {
    list.writeToStream(stream);
  }

  public CvsChangeList readChangeList(final RepositoryLocation location, final DataInput stream) throws IOException {
    CvsRepositoryLocation cvsLocation = (CvsRepositoryLocation) location;
    return new CvsChangeList(myProject, cvsLocation.getEnvironment(), cvsLocation.getRootFile(), stream);
  }

  public boolean isMaxCountSupported() {
    return false;
  }

  public Collection<FilePath> getIncomingFiles(final RepositoryLocation location) {
    return null;
  }

  public boolean refreshCacheByNumber() {
    return false;
  }

  public String getChangelistTitle() {
    return null;
  }

  private ChangeListColumn<CvsChangeList> BRANCH_COLUMN = new ChangeListColumn<CvsChangeList>() {
    public String getTitle() {
      return CvsBundle.message("changelist.column.branch");
    }

    public Object getValue(final CvsChangeList changeList) {
      final String branch = changeList.getBranch();
      return branch == null ? "" : branch;
    }
  };
}