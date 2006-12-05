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
import com.intellij.cvsSupport2.connections.CvsConnectionSettings;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutor;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutorCallback;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.openapi.cvsIntegration.CvsResult;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.ChangeListColumn;
import com.intellij.openapi.vcs.CommittedChangesProvider;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

public class CvsCommittedChangesProvider implements CommittedChangesProvider<CvsChangeList> {
  private final Project myProject;

  @NonNls private static final String INVALID_OPTION_S = "invalid option -- S";

  public CvsCommittedChangesProvider(Project project) {
    myProject = project;
  }

  public RefreshableOnComponent createFilterUI() {
    return new CvsVersionFilterComponent(myProject);
  }

  public List<CvsChangeList> getAllCommittedChanges(final int maxCount) throws VcsException {
    return new ArrayList<CvsChangeList>();
  }

  public ChangeListColumn[] getColumns() {
    return new ChangeListColumn[] { ChangeListColumn.DATE, ChangeListColumn.NAME };
  }

  public List<CvsChangeList> getCommittedChanges(VirtualFile root) throws VcsException {
    final String module = CvsUtil.getModuleName(root);
    if (module != null) {
      CvsHistoryCache cache = CvsHistoryCache.create();

      try {
        final CvsConnectionSettings settings = CvsEntriesManager.getInstance().getCvsConnectionSettingsFor(root);
        final CvsHistoryCacheElement cacheElement = cache.getCache(settings, module);

        final CvsChangeListsBuilder builder = new CvsChangeListsBuilder(module, settings, myProject);


        final ChangeBrowserSettings browserSettings = ChangeBrowserSettings.getSettings(myProject);

        Date date = browserSettings.getDateBeforeFilter();
        if (date == null) {
          date = new Date();
        }
        final CvsResult executionResult = runRLogOperation(settings, module, cacheElement, date);

        if (!executionResult.hasNoErrors()) {
          throw executionResult.composeError();
        }
        else if (executionResult.isCanceled()) {
          throw new ProcessCanceledException();
        }
        else {
          cacheElement.flush(date);
          final List<LogInformationWrapper> logs = cacheElement.getLogInformationList();
          builder.addLogs(logs);
          final List<CvsChangeList> versions = builder.getVersions();
          browserSettings.filterChanges(versions);
          if (browserSettings.USE_USER_FILTER) {
            for (Iterator<CvsChangeList> iterator = versions.iterator(); iterator.hasNext();) {
              CvsChangeList repositoryVersion = iterator.next();
              if (!Comparing.equal(browserSettings.USER, repositoryVersion.getCommitterName())) {
                iterator.remove();
              }
            }
          }
          return versions;
        }
      }
      finally {
        cache.dispose();
      }


    }
    else {
      throw new VcsException(CvsBundle.message("cannot.find.repository.location.error.message", root.getPath()));
    }
  }

  private CvsResult runRLogOperation(final CvsConnectionSettings settings,
                                     final String module,
                                     final CvsHistoryCacheElement cacheElement,
                                     final Date date) {
    LoadHistoryOperation operation = new LoadHistoryOperation(settings, module, cacheElement, date);

    operation.setDateFrom(cacheElement.getLastCachedDate());

    CvsOperationExecutor executor = new CvsOperationExecutor(myProject);
    executor.performActionSync(new CommandCvsHandler(CvsBundle.message("browse.changes.load.history.progress.title"), operation),
                               CvsOperationExecutorCallback.EMPTY);

    final CvsResult executionResult = executor.getResult();

    for (VcsException error : executionResult.getErrors()) {
      for (String message : error.getMessages()) {
        if (message.indexOf(INVALID_OPTION_S) >= 0) {
          LoadHistoryOperation.doesNotSuppressEmptyHeaders(settings);
          return runRLogOperation(settings, module, cacheElement, date);
        }
      }
    }

    return executionResult;
  }

}