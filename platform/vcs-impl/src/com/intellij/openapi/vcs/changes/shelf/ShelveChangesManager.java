// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.shelf;

import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static com.intellij.openapi.util.text.StringUtil.notNullize;
import static com.intellij.openapi.vcs.changes.ChangeListUtil.getChangeListNameForUnshelve;
import static com.intellij.openapi.vcs.changes.ChangeListUtil.getPredefinedChangeList;
import static com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList.createShelvedChangesFromFilePatches;
import static com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.SHELF;
import static com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.getToolWindowFor;
import static com.intellij.util.containers.ContainerUtil.filter;
import static com.intellij.util.containers.ContainerUtil.find;
import static com.intellij.util.containers.ContainerUtil.intersection;
import static com.intellij.util.containers.ContainerUtil.isEmpty;
import static com.intellij.util.containers.ContainerUtil.map2SetNotNull;
import static com.intellij.util.containers.ContainerUtil.newUnmodifiableList;
import static com.intellij.util.containers.ContainerUtil.process;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.Lists;
import com.intellij.concurrency.JobScheduler;
import com.intellij.configurationStore.XmlSerializer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.BaseRevisionTextPatchEP;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.IdeaTextPatchBuilder;
import com.intellij.openapi.diff.impl.patch.PatchEP;
import com.intellij.openapi.diff.impl.patch.PatchReader;
import com.intellij.openapi.diff.impl.patch.PatchSyntaxException;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.diff.impl.patch.UnifiedDiffWriter;
import com.intellij.openapi.diff.impl.patch.formove.PatchApplier;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.options.NonLazySchemeProcessor;
import com.intellij.openapi.options.SchemeManager;
import com.intellij.openapi.options.SchemeManagerFactory;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.VcsType;
import com.intellij.openapi.vcs.changes.BinaryContentRevision;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListChange;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchDefaultExecutor;
import com.intellij.openapi.vcs.changes.patch.PatchFileType;
import com.intellij.openapi.vcs.changes.patch.PatchNameChecker;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vcs.changes.ui.RollbackChangesDialog;
import com.intellij.openapi.vcs.changes.ui.RollbackWorker;
import com.intellij.openapi.vcs.changes.ui.ShelvedChangeListDragBean;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.project.ProjectKt;
import com.intellij.ui.GuiUtils;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import com.intellij.util.text.CharArrayCharSequence;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.XCollection;
import com.intellij.vcs.log.util.StopWatch;
import com.intellij.vcsUtil.FilesProgress;
import com.intellij.vcsUtil.VcsImplUtil;
import com.intellij.vcsUtil.VcsUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.jdom.Element;
import org.jdom.Parent;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.CalledInBackground;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
  name = "ShelveChangesManager",
  storages = @Storage(StoragePathMacros.WORKSPACE_FILE)
)
public final class ShelveChangesManager implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance(ShelveChangesManager.class);
  @NonNls private static final String ELEMENT_CHANGELIST = "changelist";
  @NonNls private static final String ELEMENT_RECYCLED_CHANGELIST = "recycled_changelist";
  @NonNls private static final String DEFAULT_PATCH_NAME = "shelved";

  private static final String SHELVE_MANAGER_DIR_PATH = "shelf"; //NON-NLS
  public static final String DEFAULT_PROJECT_PRESENTATION_PATH = "<Project>/shelf"; //NON-NLS

  private static final Element EMPTY_ELEMENT = new Element("state");

  private State myState = new State();

  @NotNull private final PathMacroManager myPathMacroSubstitutor;
  @NotNull private SchemeManager<ShelvedChangeList> mySchemeManager;

  private ScheduledFuture<?> myCleaningFuture;
  private final ReentrantReadWriteLock SHELVED_FILES_LOCK = new ReentrantReadWriteLock(true);
  @Nullable private Set<VirtualFile> myShelvingFiles;

  public static ShelveChangesManager getInstance(@NotNull Project project) {
    return project.getService(ShelveChangesManager.class);
  }

  public static final class State {
    @OptionTag("remove_strategy")
    public boolean myRemoveFilesFromShelf;
    @Attribute("show_recycled")
    public boolean myShowRecycled;
    @XCollection
    public Set<String> groupingKeys = new HashSet<>();
  }

  @Override
  public @NotNull Element getState() {
    //provide new element if all State fields have their default values  - > to delete existing settings in xml,
    return ObjectUtils.chooseNotNull(XmlSerializer.serialize(myState), EMPTY_ELEMENT);
  }

  @Override
  public void loadState(@NotNull Element state) {
    myState = XmlSerializer.deserialize(state, State.class);
    migrateOldShelfInfo(state, false);
    migrateOldShelfInfo(state, true);
  }

  /**
   * System independent file-path for non-default project
   *
   * @return path to default shelf directory e.g. {@code "<Project>/.idea/shelf"}
   */
  @NotNull
  public static String getDefaultShelfPath(@NotNull Project project) {
    IProjectStore store = ProjectKt.getStateStore(project);
    return store.getDirectoryStorePath(true) +
           "/" +
           (ProjectKt.isDirectoryBased(project) ? SHELVE_MANAGER_DIR_PATH : "." + SHELVE_MANAGER_DIR_PATH);
  }

  /**
   * System independent file-path for non-default project
   *
   * @return path to custom shelf directory if set. Otherwise return default shelf directory e.g. {@code "<Project>/.idea/shelf"}
   */
  @NotNull
  public static String getShelfPath(@NotNull Project project) {
    VcsConfiguration vcsConfiguration = VcsConfiguration.getInstance(project);
    if (vcsConfiguration.USE_CUSTOM_SHELF_PATH) {
      return requireNonNull(vcsConfiguration.CUSTOM_SHELF_PATH);
    }
    return getDefaultShelfPath(project);
  }

  private final Project myProject;
  private final MessageBus myBus;

  public static final Topic<ChangeListener> SHELF_TOPIC = new Topic<>("shelf updates", ChangeListener.class);

  public ShelveChangesManager(final Project project, final MessageBus bus) {
    myPathMacroSubstitutor = PathMacroManager.getInstance(project);
    myProject = project;
    myBus = bus;
    VcsConfiguration vcsConfiguration = VcsConfiguration.getInstance(project);
    mySchemeManager =
      createShelveSchemeManager(project, vcsConfiguration.USE_CUSTOM_SHELF_PATH ? vcsConfiguration.CUSTOM_SHELF_PATH : null);

    myCleaningFuture = JobScheduler.getScheduler().scheduleWithFixedDelay(() -> cleanDeletedOlderOneWeek(), 1, 1, TimeUnit.DAYS);
    Disposer.register(project, new Disposable() {
      @Override
      public void dispose() {
        stopCleanScheduler();
      }
    });
  }

  private void stopCleanScheduler() {
    if (myCleaningFuture != null) {
      myCleaningFuture.cancel(false);
      myCleaningFuture = null;
    }
  }

  @NotNull
  private SchemeManager<ShelvedChangeList> createShelveSchemeManager(@NotNull Project project,
                                                                     @Nullable String customPath) {
    FilePath customShelfFilePath = customPath != null ? VcsUtil.getFilePath(myPathMacroSubstitutor.expandPath(customPath)) : null;
    //don't collapse custom paths
    final boolean shouldCollapsePath = !VcsConfiguration.getInstance(myProject).USE_CUSTOM_SHELF_PATH;
    return SchemeManagerFactory.getInstance(project)
      .create(customShelfFilePath != null ? customShelfFilePath.getName() : SHELVE_MANAGER_DIR_PATH,
              new NonLazySchemeProcessor<ShelvedChangeList, ShelvedChangeList>() {
                @NotNull
                @Override
                public ShelvedChangeList readScheme(@NotNull Element element, boolean duringLoad) throws InvalidDataException {
                  return readOneShelvedChangeList(element);
                }

                @NotNull
                @Override
                public Parent writeScheme(@NotNull ShelvedChangeList scheme) throws WriteExternalException {
                  Element child = new Element(ELEMENT_CHANGELIST);
                  scheme.writeExternal(child);
                  if (shouldCollapsePath) {
                    myPathMacroSubstitutor.collapsePaths(child);
                  }
                  return child;
                }
              }, null, customPath != null ? Paths.get(customPath) : null);
  }

  public void projectOpened() {
    try {
      mySchemeManager.loadSchemes();
      //workaround for ignoring not valid patches, because readScheme doesn't support nullable value as it should be
      filterNonValidShelvedChangeLists();
      markDeletedSystemUnshelved();
      cleanDeletedOlderOneWeek();
    }
    catch (Exception e) {
      LOG.error("Couldn't read shelf information", e);
    }
  }

  private void filterNonValidShelvedChangeLists() {
    final List<ShelvedChangeList> allSchemes = new ArrayList<>(mySchemeManager.getAllSchemes());
    process(allSchemes, shelvedChangeList -> {
      if (!shelvedChangeList.isValid()) {
        mySchemeManager.removeScheme(shelvedChangeList);
      }
      return true;
    });
  }

  public void checkAndMigrateUnderProgress(@NotNull File fromFile, @NotNull File toFile, boolean wasCustom) {
    final SchemeManager<ShelvedChangeList> newSchemeManager = createShelveSchemeManager(myProject, VcsUtil.getFilePath(toFile).getPath());
    newSchemeManager.loadSchemes();
    if (VcsConfiguration.getInstance(myProject).MOVE_SHELVES && fromFile.exists()) {
      new Task.Modal(myProject, VcsBundle.message("shelve.copying.shelves.to.progress"), true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          for (ShelvedChangeList list : mySchemeManager.getAllSchemes()) {
            if (!list.isValid()) continue;
            try {
              File newTargetDirectory = suggestPatchName(myProject, list.DESCRIPTION, toFile, "");
              ShelvedChangeList migratedList = createChangelistCopyWithChanges(list, newTargetDirectory);
              newSchemeManager.addScheme(migratedList, false);
              indicator.checkCanceled();
            }
            catch (IOException e) {
              LOG.error("Can't copy patch file: " + list.PATH);
            }
          }
          clearShelvedLists(mySchemeManager.getAllSchemes(), false);
        }

        @Override
        public void onSuccess() {
          super.onSuccess();
          updateShelveSchemaManager(newSchemeManager);
        }

        @Override
        public void onCancel() {
          super.onCancel();
          suggestToCancelMigrationOrRevertPathToPrevious();
        }

        //
        private void suggestToCancelMigrationOrRevertPathToPrevious() {
          if (Messages.showOkCancelDialog(myProject,
                                          VcsBundle.message("shelve.moving.failed.prompt"),
                                          VcsBundle.message("shelve.error.title"),
                                          VcsBundle.message("shelve.use.new.directory.button"),
                                          VcsBundle.message("shelve.revert.moving.button"),
                                          UIUtil.getWarningIcon()) == Messages.OK) {
            updateShelveSchemaManager(newSchemeManager);
          }
          else {
            VcsConfiguration vcsConfiguration = VcsConfiguration.getInstance(myProject);
            vcsConfiguration.USE_CUSTOM_SHELF_PATH = wasCustom;
            if (wasCustom) {
              vcsConfiguration.CUSTOM_SHELF_PATH = toSystemIndependentName(fromFile.getPath());
            }
          }
        }

        @Override
        public void onThrowable(@NotNull Throwable error) {
          super.onThrowable(error);
          suggestToCancelMigrationOrRevertPathToPrevious();
        }
      }.queue();
    }
    else {
      updateShelveSchemaManager(newSchemeManager);
    }
  }

  private void updateShelveSchemaManager(SchemeManager<ShelvedChangeList> newSchemeManager) {
    myProject.save();
    ApplicationManager.getApplication().saveSettings();
    SchemeManagerFactory.getInstance(myProject).dispose(mySchemeManager);
    mySchemeManager = newSchemeManager;
    notifyStateChanged();
  }

  @NotNull
  public File getShelfResourcesDirectory() {
    return mySchemeManager.getRootDirectory();
  }

  @NotNull
  private ShelvedChangeList readOneShelvedChangeList(@NotNull Element element) throws InvalidDataException {
    ShelvedChangeList data = new ShelvedChangeList();
    myPathMacroSubstitutor.expandPaths(element);
    data.readExternal(element);
    return data;
  }

  //load old shelf information from workspace.xml without moving .patch and binary files into new directory
  private void migrateOldShelfInfo(@NotNull Element element, boolean recycled) throws InvalidDataException {
    for (Element changeSetElement : element.getChildren(recycled ? ELEMENT_RECYCLED_CHANGELIST : ELEMENT_CHANGELIST)) {
      ShelvedChangeList list = readOneShelvedChangeList(changeSetElement);
      if (!list.isValid()) break;
      File uniqueDir = generateUniqueSchemePatchDir(list.DESCRIPTION, false);
      list.setName(uniqueDir.getName());
      list.setRecycled(recycled);
      mySchemeManager.addScheme(list, false);
    }
  }

  @NotNull
  private static List<ShelvedBinaryFile> copyBinaryFiles(@NotNull ShelvedChangeList list, @NotNull File targetDirectory) {
    List<ShelvedBinaryFile> copied = new ArrayList<>();
    for (ShelvedBinaryFile file : list.getBinaryFiles()) {
      if (file.SHELVED_PATH != null) {
        File shelvedFile = new File(file.SHELVED_PATH);
        if (!StringUtil.isEmptyOrSpaces(file.AFTER_PATH) && shelvedFile.exists()) {
          File newShelvedFile = new File(targetDirectory, PathUtil.getFileName(file.AFTER_PATH));
          try {
            FileUtil.copy(shelvedFile, newShelvedFile);
            copied.add(new ShelvedBinaryFile(file.BEFORE_PATH, file.AFTER_PATH, toSystemIndependentName(newShelvedFile.getPath())));
          }
          catch (IOException e) {
            LOG.error("Can't copy binary file: " + list.PATH);
          }
        }
      }
    }
    return copied;
  }

  @NotNull
  public List<ShelvedChangeList> getShelvedChangeLists() {
    return getRecycled(false);
  }

  @NotNull
  private List<ShelvedChangeList> getRecycled(final boolean recycled) {
    return newUnmodifiableList(
      filter(mySchemeManager.getAllSchemes(), list -> recycled == list.isRecycled() && !list.isDeleted()));
  }

  @NotNull
  public List<ShelvedChangeList> getAllLists() {
    return newUnmodifiableList(mySchemeManager.getAllSchemes());
  }

  public ShelvedChangeList shelveChanges(final Collection<? extends Change> changes, final String commitMessage, final boolean rollback)
    throws IOException, VcsException {
    return shelveChanges(changes, commitMessage, rollback, false);
  }

  public ShelvedChangeList shelveChanges(final Collection<? extends Change> changes,
                                         final String commitMessage,
                                         final boolean rollback,
                                         boolean markToBeDeleted) throws IOException, VcsException {
    return shelveChanges(changes, commitMessage, rollback, markToBeDeleted, false);
  }

  public ShelvedChangeList shelveChanges(final Collection<? extends Change> changes,
                                         final String commitMessage,
                                         final boolean rollback,
                                         boolean markToBeDeleted,
                                         boolean honorExcludedFromCommit) throws IOException, VcsException {
    final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    if (progressIndicator != null) {
      progressIndicator.setText(VcsBundle.message("shelve.changes.progress.text"));
    }
    ShelvedChangeList shelveList;
    try {
      SHELVED_FILES_LOCK.writeLock().lock();
      rememberShelvingFiles(changes);
      shelveList = createShelfFromChanges(changes, commitMessage, markToBeDeleted, honorExcludedFromCommit);
    }
    finally {
      cleanShelvingFiles();
      SHELVED_FILES_LOCK.writeLock().unlock();
      notifyStateChanged();
    }
    if (rollback) {
      rollbackChangesAfterShelve(changes, honorExcludedFromCommit);
    }
    return shelveList;
  }

  @NotNull
  private ShelvedChangeList createShelfFromChanges(@NotNull Collection<? extends Change> changes,
                                                   String commitMessage,
                                                   boolean markToBeDeleted,
                                                   boolean honorExcludedFromCommit)
    throws IOException, VcsException {

    LOG.debug("Shelving of " + changes.size() + " changes...");
    StopWatch totalSW = StopWatch.start("Total shelving");

    File schemePatchDir = generateUniqueSchemePatchDir(commitMessage, true);
    List<Change> textChanges = new ArrayList<>();
    final List<ShelvedBinaryFile> binaryFiles = new ArrayList<>();
    for (Change change : changes) {
      if (ChangesUtil.getFilePath(change).isDirectory()) {
        continue;
      }
      if (change.getBeforeRevision() instanceof BinaryContentRevision || change.getAfterRevision() instanceof BinaryContentRevision) {
        binaryFiles.add(shelveBinaryFile(schemePatchDir, change));
      }
      else {
        textChanges.add(change);
      }
    }


    File patchFile = getPatchFileInConfigDir(schemePatchDir);
    List<FilePatch> patches = new ArrayList<>(buildAndSavePatchInBatches(patchFile, textChanges, honorExcludedFromCommit));

    final ShelvedChangeList changeList = new ShelvedChangeList(patchFile.toString(), commitMessage.replace('\n', ' '), binaryFiles,
                                                               createShelvedChangesFromFilePatches(myProject, patchFile.toString(),
                                                                                                   patches));
    changeList.markToDelete(markToBeDeleted);
    changeList.setName(schemePatchDir.getName());
    ProgressManager.checkCanceled();
    mySchemeManager.addScheme(changeList, false);
    totalSW.report(LOG);
    return changeList;
  }

  private List<FilePatch> buildAndSavePatchInBatches(@NotNull File patchFile,
                                                     @NotNull List<Change> textChanges,
                                                     boolean honorExcludedFromCommit) throws VcsException, IOException {
    List<FilePatch> patches = new ArrayList<>();
    if (textChanges.isEmpty()) {
      ShelfFileProcessorUtil.savePatchFile(myProject, patchFile, patches, null, new CommitContext());
      return patches;
    }

    int batchIndex = 0;
    int baseContentsPreloadSize = Registry.intValue("git.shelve.load.base.in.batches", -1);
    int partitionSize = baseContentsPreloadSize > 0 ? baseContentsPreloadSize : textChanges.size();
    List<List<Change>> partition = Lists.partition(textChanges, partitionSize);
    for (List<Change> list : partition) {
      batchIndex++;
      String inbatch = partition.size() > 1 ? " in batch #" + batchIndex : ""; //NON-NLS
      StopWatch totalSw = StopWatch.start("Total shelving" + inbatch);
      try {
        StopWatch iterSw;
        if (baseContentsPreloadSize > 0) {
          iterSw = StopWatch.start("Preloading base revisions for " + list.size() + " changes");
          preloadBaseRevisions(list);
          iterSw.report(LOG);
        }

        ProgressManager.checkCanceled();
        iterSw = StopWatch.start("Building patches" + inbatch);
        patches.addAll(IdeaTextPatchBuilder
                         .buildPatch(myProject, list, PathUtil.toSystemDependentName(myProject.getBasePath()), false,
                                     honorExcludedFromCommit));
        iterSw.report(LOG);
        ProgressManager.checkCanceled();

        iterSw = StopWatch.start("Storing base revisions" + inbatch);
        CommitContext commitContext = new CommitContext();
        baseRevisionsOfDvcsIntoContext(list, commitContext);
        iterSw.report(LOG);

        iterSw = StopWatch.start("Saving patch file" + inbatch);
        ShelfFileProcessorUtil.savePatchFile(myProject, patchFile, patches, null, commitContext);
        iterSw.report(LOG);
      }
      finally {
        ProjectLevelVcsManager.getInstance(myProject).getContentRevisionCache().clearConstantCache();
        totalSw.report(LOG);
      }
    }
    return patches;
  }

  private void preloadBaseRevisions(@NotNull List<Change> textChanges) {
    MultiMap<VcsRoot, Change> changesGroupedByRoot = MultiMap.create();
    for (Change change : textChanges) {
      ContentRevision beforeRevision = change.getBeforeRevision();
      if (beforeRevision != null) {
        FilePath file = beforeRevision.getFile();
        VcsRoot vcsRoot = ProjectLevelVcsManager.getInstance(myProject).getVcsRootObjectFor(file);
        if (vcsRoot == null || vcsRoot.getVcs() == null) {
          LOG.error(file + " is not under VCS");
        }
        else {
          changesGroupedByRoot.putValue(vcsRoot, change);
        }
      }
    }

    for (VcsRoot vcsRoot : changesGroupedByRoot.keySet()) {
      AbstractVcs vcs = requireNonNull(vcsRoot.getVcs());
      if (vcs.getDiffProvider() != null) {
        vcs.getDiffProvider().preloadBaseRevisions(requireNonNull(vcsRoot.getPath()), changesGroupedByRoot.get(vcsRoot));
      }
    }
  }

  private void rollbackChangesAfterShelve(@NotNull Collection<? extends Change> changes, boolean honorExcludedFromCommit) {
    final String operationName = UIUtil.removeMnemonic(RollbackChangesDialog.operationNameByChanges(myProject, changes));
    boolean modalContext = ApplicationManager.getApplication().isDispatchThread() && LaterInvocator.isInModalContext();
    StopWatch sw = StopWatch.start("Rollback after shelve");
    new RollbackWorker(myProject, operationName, modalContext)
      .doRollback(changes, true, null, VcsBundle.message("shelve.changes.action"), honorExcludedFromCommit);
    sw.report(LOG);
  }

  @NotNull
  private static File getPatchFileInConfigDir(@NotNull File schemePatchDir) {
    return new File(schemePatchDir, DEFAULT_PATCH_NAME + "." + VcsConfiguration.PATCH);
  }

  private void baseRevisionsOfDvcsIntoContext(List<? extends Change> textChanges, CommitContext commitContext) {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    if (dvcsUsedInProject() && VcsConfiguration.getInstance(myProject).INCLUDE_TEXT_INTO_SHELF) {
      Map<FilePath, ContentRevision> toKeep = new HashMap<>();
      for (Change change : textChanges) {
        if (change.getBeforeRevision() == null || change.getAfterRevision() == null) continue;
        if (isBig(change)) continue;
        FilePath filePath = change.getBeforeRevision().getFile();
        AbstractVcs vcs = vcsManager.getVcsFor(filePath);
        if (vcs != null && VcsType.distributed.equals(vcs.getType())) {
          toKeep.put(filePath, change.getBeforeRevision());
        }
      }
      commitContext.putUserData(BaseRevisionTextPatchEP.ourPutBaseRevisionTextKey, true);
      commitContext.putUserData(BaseRevisionTextPatchEP.ourBaseRevisions, toKeep);
    }
  }

  private static boolean isBig(@NotNull Change change) {
    VirtualFile vf = change.getVirtualFile();
    if (vf != null) {
      return isBig(vf.getLength());
    }

    ContentRevision beforeRevision = change.getBeforeRevision();
    if (beforeRevision != null) {
      try {
        String content = beforeRevision.getContent();
        if (content != null && isBig(content.length())) {
          return true;
        }
      }
      catch (VcsException e) {
        LOG.info(e);
      }
    }
    return false;
  }

  private static boolean isBig(long contentLength) {
    return contentLength > VcsConfiguration.ourMaximumFileForBaseRevisionSize;
  }

  private boolean dvcsUsedInProject() {
    return Arrays.stream(ProjectLevelVcsManager.getInstance(myProject).getAllActiveVcss()).
      anyMatch(vcs -> VcsType.distributed.equals(vcs.getType()));
  }

  public ShelvedChangeList importFilePatches(final String fileName,
                                             final List<? extends FilePatch> patches,
                                             final List<PatchEP> patchTransitExtensions)
    throws IOException {
    try {
      File schemePatchDir = generateUniqueSchemePatchDir(fileName, true);
      File patchFile = getPatchFileInConfigDir(schemePatchDir);
      ShelfFileProcessorUtil.savePatchFile(myProject, patchFile, patches, patchTransitExtensions, new CommitContext());
      final ShelvedChangeList changeList = new ShelvedChangeList(patchFile.toString(), fileName.replace('\n', ' '), new SmartList<>(),
                                                                 createShelvedChangesFromFilePatches(myProject, patchFile.getPath(),
                                                                                                     patches));
      changeList.setName(schemePatchDir.getName());
      mySchemeManager.addScheme(changeList, false);
      return changeList;
    }
    finally {
      notifyStateChanged();
    }
  }

  public List<VirtualFile> gatherPatchFiles(final Collection<? extends VirtualFile> files) {
    final List<VirtualFile> result = new ArrayList<>();

    final LinkedList<VirtualFile> filesQueue = new LinkedList<>(files);
    while (!filesQueue.isEmpty()) {
      ProgressManager.checkCanceled();
      final VirtualFile file = filesQueue.removeFirst();
      if (file.isDirectory()) {
        filesQueue.addAll(Arrays.asList(file.getChildren()));
        continue;
      }
      if (PatchFileType.isPatchFile(file)) {
        result.add(file);
      }
    }

    return result;
  }

  @CalledInBackground
  public List<ShelvedChangeList> importChangeLists(final Collection<? extends VirtualFile> files,
                                                   final Consumer<? super VcsException> exceptionConsumer) {
    final List<ShelvedChangeList> result = new ArrayList<>(files.size());
    try {
      final FilesProgress filesProgress = new FilesProgress(files.size(), VcsBundle.message("shelve.import.to.progress"));
      for (VirtualFile file : files) {
        filesProgress.updateIndicator(file);
        final String description = file.getNameWithoutExtension().replace('_', ' ');
        File schemeNameDir = generateUniqueSchemePatchDir(description, true);
        final File patchFile = getPatchFileInConfigDir(schemeNameDir);
        String patchPath = patchFile.getPath();
        try {
          List<? extends FilePatch> filePatches = loadPatchesWithoutContent(myProject, file.getPath(), new CommitContext());
          if (!filePatches.isEmpty()) {
            FileUtil.copy(new File(file.getPath()), patchFile);
            final ShelvedChangeList list =
              new ShelvedChangeList(patchPath, description, new SmartList<>(),
                                    createShelvedChangesFromFilePatches(myProject, patchPath, filePatches),
                                    file.getTimeStamp());
            list.setName(schemeNameDir.getName());
            mySchemeManager.addScheme(list, false);
            result.add(list);
          }
        }
        catch (Exception e) {
          exceptionConsumer.consume(new VcsException(e));
        }
      }
    }
    finally {
      notifyStateChanged();
    }
    return result;
  }

  private ShelvedBinaryFile shelveBinaryFile(@NotNull File schemePatchDir, final Change change) throws IOException {
    final ContentRevision beforeRevision = change.getBeforeRevision();
    final ContentRevision afterRevision = change.getAfterRevision();
    File beforeFile = beforeRevision == null ? null : beforeRevision.getFile().getIOFile();
    File afterFile = afterRevision == null ? null : afterRevision.getFile().getIOFile();
    String shelvedPath = null;
    if (afterFile != null) {
      String shelvedFileName = afterFile.getName();
      String name = FileUtilRt.getNameWithoutExtension(shelvedFileName);
      String extension = FileUtilRt.getExtension(shelvedFileName);
      File shelvedFile = FileUtil.findSequentNonexistentFile(schemePatchDir, name, extension);
      FileUtil.copy(afterRevision.getFile().getIOFile(), shelvedFile);
      shelvedPath = shelvedFile.getPath();
    }
    String beforePath = ChangesUtil.getProjectRelativePath(myProject, beforeFile);
    String afterPath = ChangesUtil.getProjectRelativePath(myProject, afterFile);
    return new ShelvedBinaryFile(beforePath, afterPath, shelvedPath);
  }

  private void notifyStateChanged() {
    if (!myProject.isDisposed()) {
      myBus.syncPublisher(SHELF_TOPIC).stateChanged(new ChangeEvent(this));
    }
  }

  @NotNull
  private File generateUniqueSchemePatchDir(@Nullable final String defaultName, boolean createResourceDirectory) {
    File shelfResourcesDirectory = getShelfResourcesDirectory();
    File dir = suggestPatchName(myProject, defaultName, shelfResourcesDirectory, "");
    if (createResourceDirectory && !dir.exists()) {
      //noinspection ResultOfMethodCallIgnored
      dir.mkdirs();
    }
    return dir;
  }

  @NotNull
  // for create patch only
  public static File suggestPatchName(Project project, @Nullable final String commitMessage, final File file, String extension) {
    @NonNls String defaultPath = shortenAndSanitize(commitMessage);
    while (true) {
      final File nonexistentFile = FileUtil.findSequentNonexistentFile(file, defaultPath,
                                                                       extension == null
                                                                       ? VcsConfiguration.getInstance(project).getPatchFileExtension()
                                                                       : extension);
      if (nonexistentFile.getName().length() >= PatchNameChecker.MAX) {
        defaultPath = defaultPath.substring(0, defaultPath.length() - 1);
        continue;
      }
      return nonexistentFile;
    }
  }

  @NotNull
  private static String shortenAndSanitize(@Nullable String commitMessage) {
    @NonNls String defaultPath = PathUtil.suggestFileName(notNullize(commitMessage));
    if (defaultPath.isEmpty()) {
      defaultPath = "unnamed";
    }
    if (defaultPath.length() > PatchNameChecker.MAX - 10) {
      defaultPath = defaultPath.substring(0, PatchNameChecker.MAX - 10);
    }
    return defaultPath;
  }

  @CalledInAny
  public void unshelveChangeList(final ShelvedChangeList changeList,
                                 @Nullable final List<? extends ShelvedChange> changes,
                                 @Nullable final List<? extends ShelvedBinaryFile> binaryFiles,
                                 @Nullable final LocalChangeList targetChangeList,
                                 boolean showSuccessNotification) {
    unshelveChangeList(changeList, changes, binaryFiles, targetChangeList, showSuccessNotification, isRemoveFilesFromShelf());
  }

  @CalledInAny
  private void unshelveChangeList(final ShelvedChangeList changeList,
                                  @Nullable final List<? extends ShelvedChange> changes,
                                  @Nullable final List<? extends ShelvedBinaryFile> binaryFiles,
                                  @Nullable final LocalChangeList targetChangeList,
                                  boolean showSuccessNotification,
                                  boolean removeFilesFromShelf) {
    unshelveChangeList(changeList, changes, binaryFiles, targetChangeList, showSuccessNotification, false, false, null, null,
                       removeFilesFromShelf);
  }

  @CalledInAny
  public void unshelveChangeList(final ShelvedChangeList changeList,
                                 @Nullable final List<? extends ShelvedChange> changes,
                                 @Nullable final List<? extends ShelvedBinaryFile> binaryFiles,
                                 @Nullable final LocalChangeList targetChangeList,
                                 final boolean showSuccessNotification,
                                 final boolean systemOperation,
                                 final boolean reverse,
                                 final String leftConflictTitle,
                                 final String rightConflictTitle,
                                 boolean removeFilesFromShelf) {
    final List<FilePatch> remainingPatches = new ArrayList<>();

    final CommitContext commitContext = new CommitContext();
    final List<TextFilePatch> textFilePatches;
    try {
      textFilePatches = loadTextPatches(myProject, changeList, changes, remainingPatches, commitContext);
    }
    catch (IOException | PatchSyntaxException e) {
      LOG.info(e);
      PatchApplier.showError(myProject, VcsBundle.message("unshelve.loading.patch.error", e.getMessage()));
      return;
    }

    final List<FilePatch> patches = new ArrayList<>(textFilePatches);

    final List<ShelvedBinaryFile> remainingBinaries = new ArrayList<>();
    final List<ShelvedBinaryFile> binaryFilesToUnshelve = getBinaryFilesToUnshelve(changeList, binaryFiles, remainingBinaries);

    for (final ShelvedBinaryFile shelvedBinaryFile : binaryFilesToUnshelve) {
      patches.add(new ShelvedBinaryFilePatch(shelvedBinaryFile));
    }

    final PatchApplier patchApplier =
      new PatchApplier(myProject, myProject.getBaseDir(),
                       patches, targetChangeList, commitContext, reverse, leftConflictTitle,
                       rightConflictTitle);
    patchApplier.execute(showSuccessNotification, systemOperation);
    if (removeFilesFromShelf) {
      remainingPatches.addAll(patchApplier.getRemainingPatches());
      GuiUtils.invokeLaterIfNeeded(() -> updateListAfterUnshelve(changeList, remainingPatches, remainingBinaries, commitContext),
                                   ModalityState.NON_MODAL, myProject.getDisposed());
    }
  }

  @NotNull
  @CalledInAwt
  Map<ShelvedChangeList, Date> deleteShelves(@NotNull List<? extends ShelvedChangeList> shelvedListsToDelete,
                                             @NotNull List<? extends ShelvedChangeList> shelvedListsFromChanges,
                                             @NotNull List<? extends ShelvedChange> changesToDelete,
                                             @NotNull List<? extends ShelvedBinaryFile> binariesToDelete) {
    // filter changes
    ArrayList<ShelvedChangeList> shelvedListsFromChangesToDelete = new ArrayList<>(shelvedListsFromChanges);
    shelvedListsFromChangesToDelete.removeAll(shelvedListsToDelete);

    if (shelvedListsFromChangesToDelete.size() + binariesToDelete.size() == 0 && shelvedListsToDelete.isEmpty()) {
      return Collections.emptyMap();
    }

    //store original dates to restore if needed
    Map<ShelvedChangeList, Date> deletedListsWithOriginalDate = new HashMap<>();
    for (ShelvedChangeList changeList : shelvedListsToDelete) {
      Date originalDate = changeList.DATE;
      if (changeList.isDeleted()) {
        deleteChangeListCompletely(changeList);
      }
      else {
        markChangeListAsDeleted(changeList);
        deletedListsWithOriginalDate.put(changeList, originalDate);
      }
    }

    for (ShelvedChangeList list : shelvedListsFromChangesToDelete) {
      Date originalDate = list.DATE;
      boolean wasDeleted = list.isDeleted();
      ShelvedChangeList newListWithDeletedChanges = removeChangesFromChangeList(list, changesToDelete, binariesToDelete);
      if (newListWithDeletedChanges != null) {
        deletedListsWithOriginalDate.put(newListWithDeletedChanges, originalDate);
      }
      else if (!wasDeleted) {
        //entire list became deleted because no changes remained
        ShelvedChangeList shelvedChangeList = mySchemeManager.findSchemeByName(list.getName());
        if (shelvedChangeList != null && shelvedChangeList.isDeleted()) {
          deletedListsWithOriginalDate.put(shelvedChangeList, originalDate);
        }
      }
    }
    return deletedListsWithOriginalDate;
  }

  @Nullable
  @CalledInAwt
  private ShelvedChangeList removeChangesFromChangeList(@NotNull ShelvedChangeList list,
                                                        @NotNull List<? extends ShelvedChange> changes,
                                                        @NotNull List<? extends ShelvedBinaryFile> binaryFiles) {
    final ArrayList<ShelvedBinaryFile> remainingBinaries = new ArrayList<>(list.getBinaryFiles());
    remainingBinaries.removeAll(binaryFiles);

    final CommitContext commitContext = new CommitContext();
    final List<FilePatch> remainingPatches = new ArrayList<>();
    try {
      loadTextPatches(myProject, list, changes, remainingPatches, commitContext);
    }
    catch (IOException | PatchSyntaxException e) {
      LOG.info(e);
      VcsImplUtil.showErrorMessage(myProject, e.getMessage(),
                                   VcsBundle.message("shelve.delete.files.from.changelist.error", list.DESCRIPTION));
      return null;
    }
    return saveRemainingPatchesIfNeeded(list, remainingPatches, remainingBinaries, commitContext, true);
  }


  static List<TextFilePatch> loadTextPatches(final Project project,
                                             final ShelvedChangeList changeList,
                                             final List<? extends ShelvedChange> changes,
                                             final List<? super FilePatch> remainingPatches,
                                             final CommitContext commitContext)
    throws IOException, PatchSyntaxException {
    final List<TextFilePatch> textFilePatches = loadPatches(project, changeList.PATH, commitContext);

    if (changes != null) {
      final Iterator<TextFilePatch> iterator = textFilePatches.iterator();
      while (iterator.hasNext()) {
        TextFilePatch patch = iterator.next();
        if (!needUnshelve(patch, changes)) {
          remainingPatches.add(patch);
          iterator.remove();
        }
      }
    }
    return textFilePatches;
  }

  public void setRemoveFilesFromShelf(boolean removeFilesFromShelf) {
    myState.myRemoveFilesFromShelf = removeFilesFromShelf;
  }

  public boolean isRemoveFilesFromShelf() {
    return myState.myRemoveFilesFromShelf;
  }

  private void markDeletedSystemUnshelved() {
    List<ShelvedChangeList> systemUnshelved =
      filter(mySchemeManager.getAllSchemes(), list -> (list.isRecycled()) && list.isMarkedToDelete());
    for (ShelvedChangeList list : systemUnshelved) {
      list.setDeleted(true);
      list.markToDelete(false);
    }
  }

  private void cleanDeletedOlderOneWeek() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.DAY_OF_MONTH, -7);
    clean(list -> (list.isDeleted() && list.DATE.before(new Date(cal.getTimeInMillis()))));
  }

  public void cleanUnshelved(long timeBefore) {
    final Date limitDate = new Date(timeBefore);
    clean(l -> l.isRecycled() && l.DATE.before(limitDate));
  }

  private void clean(@NotNull Condition<? super ShelvedChangeList> condition) {
    final List<ShelvedChangeList> toDelete = filter(mySchemeManager.getAllSchemes(), condition);
    clearShelvedLists(toDelete, true);
  }

  @CalledInAwt
  public void shelveSilentlyUnderProgress(@NotNull List<? extends Change> changes) {
    final List<ShelvedChangeList> result = new ArrayList<>();
    new Task.Backgroundable(myProject, VcsBundle.getString("shelve.changes.progress.title"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        result.addAll(shelveChangesInSeparatedLists(changes));
      }

      @Override
      public void onSuccess() {
        VcsNotifier.getInstance(myProject).notifySuccess(VcsBundle.message("shelve.successful.message"));
        if (result.size() == 1 && isShelfContentActive()) {
          ShelvedChangesViewManager.getInstance(myProject).startEditing(result.get(0));
        }
      }
    }.queue();
  }

  private void rememberShelvingFiles(@NotNull Collection<? extends Change> changes) {
    Set<VirtualFile> fileSet = new HashSet<>();
    fileSet.addAll(map2SetNotNull(changes, Change::getVirtualFile));
    myShelvingFiles = fileSet;
  }

  private void cleanShelvingFiles() {
    myShelvingFiles = null;
  }

  private boolean isShelfContentActive() {
    ToolWindow window = getToolWindowFor(myProject, SHELF);
    return window != null &&
           window.isVisible() &&
           ((ChangesViewContentManager)ChangesViewContentManager.getInstance(myProject)).isContentSelected(SHELF);
  }

  @NotNull
  private List<ShelvedChangeList> shelveChangesInSeparatedLists(@NotNull Collection<? extends Change> changes) {
    List<String> failedChangeLists = new ArrayList<>();
    List<ShelvedChangeList> result = new ArrayList<>();
    List<Change> shelvedChanges = new ArrayList<>();

    try {
      SHELVED_FILES_LOCK.writeLock().lock();
      rememberShelvingFiles(changes);
      List<LocalChangeList> changeLists = ChangeListManager.getInstance(myProject).getChangeLists();
      for (LocalChangeList list : changeLists) {
        HashSet<Change> changeSet = new HashSet<>(list.getChanges());

        List<Change> changesForChangelist = new ArrayList<>();
        for (Change change : changes) {
          boolean inChangelist;
          if (change instanceof ChangeListChange) {
            inChangelist = ((ChangeListChange)change).getChangeListId().equals(list.getId());
          }
          else {
            inChangelist = changeSet.contains(change);
          }

          if (inChangelist) {
            changesForChangelist.add(change);
          }
        }

        if (!changesForChangelist.isEmpty()) {
          try {
            result.add(createShelfFromChanges(changesForChangelist, list.getName(), false, false));
            shelvedChanges.addAll(changesForChangelist);
          }
          catch (Exception e) {
            ProgressManager.checkCanceled();
            LOG.warn(e);
            failedChangeLists.add(list.getName());
          }
        }
      }
    }
    finally {
      cleanShelvingFiles();
      SHELVED_FILES_LOCK.writeLock().unlock();
      notifyStateChanged();
    }

    rollbackChangesAfterShelve(shelvedChanges, false);

    if (!failedChangeLists.isEmpty()) {
      VcsNotifier.getInstance(myProject).notifyError(VcsBundle.message("shelve.failed.title"), VcsBundle
        .message("shelve.failed.message", failedChangeLists.size(), StringUtil.join(failedChangeLists, ",")));
    }
    return result;
  }

  @CalledInAwt
  public static void unshelveSilentlyWithDnd(@NotNull Project project,
                                             @NotNull ShelvedChangeListDragBean shelvedChangeListDragBean,
                                             @Nullable ChangesBrowserNode dropRootNode,
                                             boolean removeFilesFromShelf) {
    FileDocumentManager.getInstance().saveAllDocuments();
    LocalChangeList predefinedChangeList =
      dropRootNode != null ? ObjectUtils.tryCast(dropRootNode.getUserObject(), LocalChangeList.class) : null;
    getInstance(project).unshelveSilentlyAsynchronously(project, shelvedChangeListDragBean.getShelvedChangelists(),
                                                        shelvedChangeListDragBean.getChanges(),
                                                        shelvedChangeListDragBean.getBinaryFiles(), predefinedChangeList,
                                                        removeFilesFromShelf);
  }

  public void unshelveSilentlyAsynchronously(@NotNull final Project project,
                                             @NotNull final List<? extends ShelvedChangeList> selectedChangeLists,
                                             @NotNull final List<? extends ShelvedChange> selectedChanges,
                                             @NotNull final List<? extends ShelvedBinaryFile> selectedBinaryChanges,
                                             @Nullable final LocalChangeList forcePredefinedOneChangelist) {
    unshelveSilentlyAsynchronously(project, selectedChangeLists, selectedChanges, selectedBinaryChanges, forcePredefinedOneChangelist,
                                   isRemoveFilesFromShelf());
  }

  private void unshelveSilentlyAsynchronously(@NotNull final Project project,
                                              @NotNull final List<? extends ShelvedChangeList> selectedChangeLists,
                                              @NotNull final List<? extends ShelvedChange> selectedChanges,
                                              @NotNull final List<? extends ShelvedBinaryFile> selectedBinaryChanges,
                                              @Nullable final LocalChangeList forcePredefinedOneChangelist, boolean removeFilesFromShelf) {
    ProgressManager.getInstance().run(new Task.Backgroundable(project, VcsBundle.getString("unshelve.changes.progress.title"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        for (ShelvedChangeList changeList : selectedChangeLists) {
          List<ShelvedChange> changesForChangelist =
            new ArrayList<>(intersection(requireNonNull(changeList.getChanges()), selectedChanges));
          List<ShelvedBinaryFile> binariesForChangelist =
            new ArrayList<>(intersection(changeList.getBinaryFiles(), selectedBinaryChanges));
          boolean shouldUnshelveAllList = changesForChangelist.isEmpty() && binariesForChangelist.isEmpty();
          unshelveChangeList(changeList, shouldUnshelveAllList ? null : changesForChangelist,
                             shouldUnshelveAllList ? null : binariesForChangelist,
                             forcePredefinedOneChangelist != null ? forcePredefinedOneChangelist : getChangeListUnshelveTo(changeList),
                             true, removeFilesFromShelf);
          ChangeListManagerImpl.getInstanceImpl(myProject).waitForUpdate(VcsBundle.getString("unshelve.changes.progress.title"));
        }
      }
    });
  }

  @NotNull
  private LocalChangeList getChangeListUnshelveTo(@NotNull ShelvedChangeList list) {
    ChangeListManager manager = ChangeListManager.getInstance(myProject);
    LocalChangeList localChangeList = getPredefinedChangeList(list, manager);
    return localChangeList != null ? localChangeList : manager.addChangeList(getChangeListNameForUnshelve(list), "");
  }

  private static List<ShelvedBinaryFile> getBinaryFilesToUnshelve(final ShelvedChangeList changeList,
                                                                  final List<? extends ShelvedBinaryFile> binaryFiles,
                                                                  final List<? super ShelvedBinaryFile> remainingBinaries) {
    if (binaryFiles == null) {
      return new ArrayList<>(changeList.getBinaryFiles());
    }
    ArrayList<ShelvedBinaryFile> result = new ArrayList<>();
    for (ShelvedBinaryFile file : changeList.getBinaryFiles()) {
      if (binaryFiles.contains(file)) {
        result.add(file);
      }
      else {
        remainingBinaries.add(file);
      }
    }
    return result;
  }

  private static boolean needUnshelve(final FilePatch patch, final List<? extends ShelvedChange> changes) {
    for (ShelvedChange change : changes) {
      if (Objects.equals(patch.getBeforeName(), change.getBeforePath())) {
        return true;
      }
    }
    return false;
  }

  private static void writePatchesToFile(final Project project,
                                         final String path,
                                         final List<? extends FilePatch> remainingPatches,
                                         CommitContext commitContext) {
    try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8)) {
      UnifiedDiffWriter.write(project, remainingPatches, writer, "\n", commitContext);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @CalledInAwt
  public void updateListAfterUnshelve(@NotNull ShelvedChangeList listToUpdate,
                                      @NotNull List<? extends FilePatch> patches,
                                      @NotNull List<? extends ShelvedBinaryFile> binaries,
                                      @NotNull CommitContext commitContext) {
    saveRemainingPatchesIfNeeded(listToUpdate, patches, binaries, commitContext, false);
  }

  /**
   * Return newly created shelved list with applied (deleted or unshelved) changes or null if no additional shelved list was created
   * 1. if no changes remained in the original list - delete or mark applied (recycled) entire list - > no new list created, return null;
   * 2. if there are some applied (deleted) changes and something remained it the original list then create separated list for applied
   * changes and delete these changes from the original list - > in this case new list with applied (deleted) changes will be a return value
   */
  @Nullable
  @CalledInAwt
  private ShelvedChangeList saveRemainingPatchesIfNeeded(final ShelvedChangeList changeList,
                                                         final List<? extends FilePatch> remainingPatches,
                                                         final List<? extends ShelvedBinaryFile> remainingBinaries,
                                                         CommitContext commitContext,
                                                         boolean delete) {
    // all changes in the shelved list have been chosen to be applied/deleted
    if (remainingPatches.isEmpty() && remainingBinaries.isEmpty()) {
      if (!delete) {
        recycleChangeList(changeList);
      }
      else if (changeList.isDeleted()) {
        deleteChangeListCompletely(changeList);
      }
      else {
        markChangeListAsDeleted(changeList);
      }
      return null;
    }
    //apply already applied  - do not change anything
    if (!delete && changeList.isRecycled()) return null;

    ShelvedChangeList newlyCreatedList = null;
    if ((delete && changeList.isDeleted())) {
      saveRemainingChangesInList(changeList, remainingPatches, remainingBinaries, commitContext);
    }
    else {
      newlyCreatedList = saveRemainingAndRecycleOthers(changeList, remainingPatches, remainingBinaries, commitContext, delete);
    }
    notifyStateChanged();
    return newlyCreatedList;
  }

  /**
   * Delete applied/deleted changes from original list and create recycled/delete copy with others
   *
   * @return newly created recycled/deleted list or null if no new list was created
   */
  @Nullable
  @CalledInAwt
  private ShelvedChangeList saveRemainingAndRecycleOthers(@NotNull final ShelvedChangeList changeList,
                                                          final List<? extends FilePatch> remainingPatches,
                                                          final List<? extends ShelvedBinaryFile> remainingBinaries,
                                                          CommitContext commitContext,
                                                          boolean delete) {

    try {
      ShelvedChangeList listCopy = createChangelistCopyWithChanges(changeList, generateUniqueSchemePatchDir(changeList.DESCRIPTION, true));
      listCopy.updateDate();
      //changes should be loaded
      saveRemainingChangesInList(changeList, remainingPatches, remainingBinaries, commitContext);

      removeFromListWithChanges(listCopy, requireNonNull(changeList.getChanges()), changeList.getBinaryFiles());
      if (delete) {
        markChangeListAsDeleted(listCopy);
      }
      else {
        recycleChangeList(listCopy);
      }
      saveListAsScheme(listCopy);
      return listCopy;
    }
    catch (IOException e) {
      // do not delete if cannot recycle
      return null;
    }
  }

  private void saveRemainingChangesInList(@NotNull ShelvedChangeList changeList,
                                          List<? extends FilePatch> remainingPatches,
                                          List<? extends ShelvedBinaryFile> remainingBinaries, CommitContext commitContext) {
    writePatchesToFile(myProject, changeList.PATH, remainingPatches, commitContext);

    changeList.getBinaryFiles().retainAll(remainingBinaries);
    changeList.setChanges(createShelvedChangesFromFilePatches(myProject, changeList.PATH, remainingPatches));
  }

  void saveListAsScheme(@NotNull ShelvedChangeList list) {
    if (!list.getBinaryFiles().isEmpty() || !isEmpty(list.getChanges())) {
      // all newly create ShelvedChangeList have to be added to SchemesManger as new scheme
      mySchemeManager.addScheme(list, false);
    }
  }

  @NotNull
  ShelvedChangeList createChangelistCopyWithChanges(@NotNull ShelvedChangeList changeList, @NotNull File targetDir)
    throws IOException {
    final File newPath = getPatchFileInConfigDir(targetDir);
    FileUtil.copy(new File(changeList.PATH), newPath);
    changeList.loadChangesIfNeeded(myProject);

    final ShelvedChangeList listCopy =
      new ShelvedChangeList(newPath.getAbsolutePath(), changeList.DESCRIPTION, copyBinaryFiles(changeList, targetDir),
                            new ArrayList<>(requireNonNull(changeList.getChanges())), changeList.DATE.getTime());
    listCopy.markToDelete(changeList.isMarkedToDelete());
    listCopy.setRecycled(changeList.isRecycled());
    listCopy.setDeleted(changeList.isDeleted());
    listCopy.setName(targetDir.getName());
    return listCopy;
  }

  public void restoreList(@NotNull final ShelvedChangeList shelvedChangeList, @NotNull Date restoreDate) {
    ShelvedChangeList list = mySchemeManager.findSchemeByName(shelvedChangeList.getName());
    if (list == null) return;
    list.setDeleted(false);
    list.DATE = restoreDate;
    notifyStateChanged();
  }

  @NotNull
  public List<ShelvedChangeList> getRecycledShelvedChangeLists() {
    return getRecycled(true);
  }

  public List<ShelvedChangeList> getDeletedLists() {
    return newUnmodifiableList(filter(mySchemeManager.getAllSchemes(), ShelvedChangeList::isDeleted));
  }

  public void clearRecycled() {
    clearShelvedLists(getRecycledShelvedChangeLists(), true);
  }

  void clearShelvedLists(@NotNull List<? extends ShelvedChangeList> shelvedLists, boolean updateView) {
    if (shelvedLists.isEmpty()) return;
    for (ShelvedChangeList list : shelvedLists) {
      deleteResources(list);
      mySchemeManager.removeScheme(list);
    }
    if (updateView) {
      notifyStateChanged();
    }
  }

  @NotNull
  public Collection<VirtualFile> getShelvingFiles() {
    return new HashSet<>(ContainerUtil.notNullize(myShelvingFiles));
  }

  private void removeFromListWithChanges(@NotNull final ShelvedChangeList listCopy,
                                         @NotNull List<? extends ShelvedChange> shelvedChanges,
                                         @NotNull List<? extends ShelvedBinaryFile> shelvedBinaryChanges) {
    //listCopy should contain loaded changes
    removeBinaries(listCopy, shelvedBinaryChanges);
    removeChanges(listCopy, shelvedChanges);

    // create patch file based on filtered changes
    try {
      final CommitContext commitContext = new CommitContext();
      final List<FilePatch> patches = new ArrayList<>();
      List<TextFilePatch> filePatches = loadPatches(myProject, listCopy.PATH, commitContext);
      for (ShelvedChange change : requireNonNull(listCopy.getChanges())) {
        patches.add(find(filePatches, patch -> change.getBeforePath().equals(patch.getBeforeName())));
      }
      writePatchesToFile(myProject, listCopy.PATH, patches, commitContext);
    }
    catch (IOException | PatchSyntaxException e) {
      LOG.info(e);
      // left file as is
    }
  }

  private static void removeChanges(@NotNull ShelvedChangeList list, @NotNull List<? extends ShelvedChange> shelvedChanges) {
    for (Iterator<ShelvedChange> iterator = requireNonNull(list.getChanges()).iterator(); iterator.hasNext(); ) {
      final ShelvedChange change = iterator.next();
      for (ShelvedChange newChange : shelvedChanges) {
        if (Objects.equals(change.getBeforePath(), newChange.getBeforePath()) &&
            Objects.equals(change.getAfterPath(), newChange.getAfterPath())) {
          iterator.remove();
        }
      }
    }
  }

  private static void removeBinaries(@NotNull ShelvedChangeList list, @NotNull List<? extends ShelvedBinaryFile> binaryFiles) {
    for (Iterator<ShelvedBinaryFile> shelvedChangeListIterator = list.getBinaryFiles().iterator();
         shelvedChangeListIterator.hasNext(); ) {
      final ShelvedBinaryFile binaryFile = shelvedChangeListIterator.next();
      for (ShelvedBinaryFile newBinary : binaryFiles) {
        if (Objects.equals(newBinary.BEFORE_PATH, binaryFile.BEFORE_PATH)
            && Objects.equals(newBinary.AFTER_PATH, binaryFile.AFTER_PATH)) {
          shelvedChangeListIterator.remove();
        }
      }
    }
  }

  private void recycleChangeList(@NotNull final ShelvedChangeList changeList) {
    changeList.setRecycled(true);
    changeList.updateDate();
    if (changeList.isMarkedToDelete()) {
      changeList.markToDelete(false);
      changeList.setDeleted(true);
    }
    notifyStateChanged();
  }

  private void deleteChangeListCompletely(@NotNull final ShelvedChangeList changeList) {
    deleteResources(changeList);
    mySchemeManager.removeScheme(changeList);
    notifyStateChanged();
  }

  void markChangeListAsDeleted(@NotNull final ShelvedChangeList changeList) {
    changeList.setDeleted(true);
    changeList.updateDate();
    notifyStateChanged();
  }

  private void deleteResources(@NotNull final ShelvedChangeList changeList) {
    FileUtil.delete(new File(changeList.PATH));
    for (ShelvedBinaryFile binaryFile : changeList.getBinaryFiles()) {
      final String path = binaryFile.SHELVED_PATH;
      if (path != null) {
        FileUtil.delete(new File(path));
      }
    }
    //schema dir may be related to another list, so check that it's empty first
    File schemaDir = new File(getShelfResourcesDirectory(), changeList.getName());
    if (schemaDir.exists() && ArrayUtil.isEmpty(schemaDir.list())) {
      FileUtil.delete(schemaDir);
    }
  }

  public void renameChangeList(final ShelvedChangeList changeList, final String newName) {
    changeList.DESCRIPTION = newName;
    notifyStateChanged();
  }

  @NotNull
  public static List<TextFilePatch> loadPatches(Project project,
                                                final String patchPath,
                                                @Nullable CommitContext commitContext) throws IOException, PatchSyntaxException {
    return loadPatches(project, patchPath, commitContext, true);
  }

  @NotNull
  static List<? extends FilePatch> loadPatchesWithoutContent(Project project,
                                                             final String patchPath,
                                                             @Nullable CommitContext commitContext)
    throws IOException, PatchSyntaxException {
    return loadPatches(project, patchPath, commitContext, false);
  }

  private static List<TextFilePatch> loadPatches(Project project,
                                                 final String patchPath,
                                                 @Nullable CommitContext commitContext,
                                                 boolean loadContent) throws IOException, PatchSyntaxException {
    char[] text = FileUtil.loadFileText(new File(patchPath), CharsetToolkit.UTF8);
    PatchReader reader = new PatchReader(new CharArrayCharSequence(text), loadContent);
    final List<TextFilePatch> textFilePatches = reader.readTextPatches();
    ApplyPatchDefaultExecutor.applyAdditionalInfoBefore(project, reader.getAdditionalInfo(null), commitContext);
    return textFilePatches;
  }

  public boolean isShowRecycled() {
    return myState.myShowRecycled;
  }

  public void setShowRecycled(final boolean showRecycled) {
    myState.myShowRecycled = showRecycled;
  }

  @NotNull
  public Set<String> getGrouping() {
    return myState.groupingKeys;
  }

  public void setGrouping(@NotNull Set<String> grouping) {
    myState.groupingKeys = grouping;
  }

  public static class PostStartupActivity implements StartupActivity.DumbAware {
    @Override
    public void runActivity(@NotNull Project project) {
      getInstance(project).projectOpened();
    }
  }
}
