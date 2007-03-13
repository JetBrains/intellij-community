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
import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutor;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutorCallback;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.openapi.cvsIntegration.CvsResult;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.ChangeListColumn;
import com.intellij.openapi.vcs.CommittedChangesProvider;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.ChangesBrowserSettingsEditor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.peer.PeerFactory;
import org.jetbrains.annotations.NonNls;

import java.util.*;

public class CvsCommittedChangesProvider implements CommittedChangesProvider<CvsChangeList, ChangeBrowserSettings> {
  private final Project myProject;
  private CvsEnvironment myEnvironment;
  private String myModuleName;

  @NonNls private static final String INVALID_OPTION_S = "invalid option -- S";

  public CvsCommittedChangesProvider(Project project) {
    myProject = project;
  }
  public CvsCommittedChangesProvider(final Project project, final CvsEnvironment environment, final String moduleName) {
    myProject = project;
    myEnvironment = environment;
    myModuleName = moduleName;
  }

  public ChangeBrowserSettings createDefaultSettings() {
    return new ChangeBrowserSettings();
  }

  public ChangesBrowserSettingsEditor<ChangeBrowserSettings> createFilterUI(final boolean showDateFilter) {
    return new CvsVersionFilterComponent(showDateFilter);
  }

  public List<CvsChangeList> getAllCommittedChanges(ChangeBrowserSettings settings, final int maxCount) throws VcsException {
    if (myEnvironment != null && myModuleName != null) {
      return loadCommittedChanges(settings, myModuleName, myEnvironment);
    }
    LinkedHashSet<CvsChangeList> result = new LinkedHashSet<CvsChangeList>();
    final CvsVcs2 vcs = CvsVcs2.getInstance(myProject);
    final VirtualFile[] files = ProjectLevelVcsManager.getInstance(myProject).getRootsUnderVcs(vcs);
    for(VirtualFile file: files) {
      if (vcs.fileIsUnderVcs(PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(file))) {
        result.addAll(getCommittedChanges(settings, file, 0));
      }
    }
    return new ArrayList<CvsChangeList>(result);
  }

  public ChangeListColumn[] getColumns() {
    return new ChangeListColumn[] { ChangeListColumn.DATE, ChangeListColumn.NAME, ChangeListColumn.DESCRIPTION };
  }

  public List<CvsChangeList> getCommittedChanges(ChangeBrowserSettings settings, VirtualFile root, final int maxCount) throws VcsException {
    if (myEnvironment != null && myModuleName != null) {
      return loadCommittedChanges(settings, myModuleName, myEnvironment);
    }
    final VirtualFile rootDir = root.isDirectory() ? root : root.getParent();
    final String module = CvsUtil.getModuleName(root);
    final CvsEnvironment connectionSettings = CvsEntriesManager.getInstance().getCvsConnectionSettingsFor(rootDir);
    if (module != null) {
      return loadCommittedChanges(settings, module, connectionSettings);
    }
    else {
      throw new VcsException(CvsBundle.message("cannot.find.repository.location.error.message", root.getPath()));
    }
  }

  private List<CvsChangeList> loadCommittedChanges(final ChangeBrowserSettings settings, final String module, final CvsEnvironment connectionSettings)
    throws VcsException {
    CvsHistoryCache cache = CvsHistoryCache.create();

    try {
      final CvsHistoryCacheElement cacheElement = cache.getCache(connectionSettings, module);

      final CvsChangeListsBuilder builder = new CvsChangeListsBuilder(module, connectionSettings, myProject);


      Date date = settings.getDateBeforeFilter();
      if (date == null) {
        date = new Date();
      }
      final CvsResult executionResult = runRLogOperation(connectionSettings, module, cacheElement, date);

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
        settings.filterChanges(versions);
        if (settings.USE_USER_FILTER) {
          for (Iterator<CvsChangeList> iterator = versions.iterator(); iterator.hasNext();) {
            CvsChangeList repositoryVersion = iterator.next();
            if (!Comparing.equal(settings.USER, repositoryVersion.getCommitterName())) {
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

  private CvsResult runRLogOperation(final CvsEnvironment settings,
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