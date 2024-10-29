// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.shelf;

import com.google.common.collect.Lists;
import com.intellij.concurrency.JobScheduler;
import com.intellij.configurationStore.XmlSerializer;
import com.intellij.history.ActivityId;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.components.*;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.*;
import com.intellij.openapi.diff.impl.patch.formove.PatchApplier;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.options.NonLazySchemeProcessor;
import com.intellij.openapi.options.SchemeManager;
import com.intellij.openapi.options.SchemeManagerFactory;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchDefaultExecutor;
import com.intellij.openapi.vcs.changes.patch.PatchFileType;
import com.intellij.openapi.vcs.changes.patch.PatchNameChecker;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vcs.changes.ui.RollbackChangesDialog;
import com.intellij.openapi.vcs.changes.ui.RollbackWorker;
import com.intellij.openapi.vcs.changes.ui.ShelvedChangeListDragBean;
import com.intellij.openapi.vcs.telemetry.VcsTelemetrySpan.Shelve;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.platform.diagnostic.telemetry.helpers.TraceKt;
import com.intellij.project.ProjectKt;
import com.intellij.util.*;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.messages.Topic;
import com.intellij.util.text.CharArrayCharSequence;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.XCollection;
import com.intellij.vcs.ShelveNamePatch;
import com.intellij.vcs.ShelveNameProvider;
import com.intellij.vcs.VcsActivity;
import com.intellij.vcsUtil.FilesProgress;
import com.intellij.vcsUtil.VcsImplUtil;
import com.intellij.vcsUtil.VcsUtil;
import io.opentelemetry.api.trace.Tracer;
import kotlinx.coroutines.CoroutineScope;
import org.jdom.Element;
import org.jdom.Parent;
import org.jetbrains.annotations.*;

import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import static com.intellij.openapi.util.text.StringUtil.notNullize;
import static com.intellij.openapi.vcs.VcsNotificationIdsHolder.SHELVE_FAILED;
import static com.intellij.openapi.vcs.VcsNotificationIdsHolder.SHELVE_SUCCESSFUL;
import static com.intellij.openapi.vcs.changes.ChangeListUtil.getChangeListNameForUnshelve;
import static com.intellij.openapi.vcs.changes.ChangeListUtil.getPredefinedChangeList;
import static com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList.createShelvedChangesFromFilePatches;
import static com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.SHELF;
import static com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.getToolWindowFor;
import static com.intellij.platform.diagnostic.telemetry.helpers.TraceUtil.computeWithSpanThrows;
import static com.intellij.platform.diagnostic.telemetry.helpers.TraceUtil.runWithSpanThrows;

@Service(Service.Level.PROJECT)
@State(name = "ShelveChangesManager", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class ShelveChangesManager implements PersistentStateComponent<Element> {
  public static final String DEFAULT_PROJECT_PRESENTATION_PATH = "<Project>/shelf"; //NON-NLS
  @Topic.ProjectLevel
  public static final Topic<ShelveChangesManagerListener> SHELF_TOPIC = new Topic<>("shelf updates", ShelveChangesManagerListener.class, Topic.BroadcastDirection.NONE);
  private static final Logger LOG = Logger.getInstance(ShelveChangesManager.class);
  private static final @NonNls String ELEMENT_CHANGELIST = "changelist";
  private static final @NonNls String ELEMENT_RECYCLED_CHANGELIST = "recycled_changelist";
  private static final @NonNls String DEFAULT_PATCH_NAME = "shelved";
  private static final String SHELVE_MANAGER_DIR_PATH = "shelf"; //NON-NLS
  private static final Element EMPTY_ELEMENT = new Element("state");
  private final @NotNull PathMacroManager myPathMacroSubstitutor;
  private final ReadWriteLock SHELVED_FILES_LOCK = new ReentrantReadWriteLock(true);
  private final Tracer myTracer = TelemetryManager.getInstance().getTracer(VcsScopeKt.VcsScope);
  private final Project myProject;
  @NotNull final CoroutineScope coroutineScope;
  private State myState = new State();
  private @NotNull SchemeManager<ShelvedChangeList> schemeManager;
  private ScheduledFuture<?> myCleaningFuture;
  private @Nullable Set<VirtualFile> myShelvingFiles;

  ShelveChangesManager(@NotNull Project project, @NotNull CoroutineScope coroutineScope) {
    myPathMacroSubstitutor = PathMacroManager.getInstance(project);
    myProject = project;
    this.coroutineScope = coroutineScope;
    VcsConfiguration vcsConfiguration = VcsConfiguration.getInstance(project);
    schemeManager =
      createShelveSchemeManager(project, vcsConfiguration.USE_CUSTOM_SHELF_PATH ? vcsConfiguration.CUSTOM_SHELF_PATH : null);

    myCleaningFuture = JobScheduler.getScheduler().scheduleWithFixedDelay(() -> cleanDeletedOlderOneWeek(), 1, 1, TimeUnit.DAYS);
    Disposer.register(project, new Disposable() {
      @Override
      public void dispose() {
        stopCleanScheduler();
      }
    });
  }

  @Override
  public @NotNull Element getState() {
    //provide new element if all State fields have their default values  - > to delete existing settings in xml,
    return ObjectUtils.chooseNotNull(XmlSerializer.serialize(myState), EMPTY_ELEMENT);
  }

  @Override
  public void loadState(@NotNull Element state) {
    myState = XmlSerializer.deserialize(state, State.class);
    try {
      migrateOldShelfInfo(state, false);
      migrateOldShelfInfo(state, true);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private void stopCleanScheduler() {
    if (myCleaningFuture != null) {
      myCleaningFuture.cancel(false);
      myCleaningFuture = null;
    }
  }

  private @NotNull SchemeManager<ShelvedChangeList> createShelveSchemeManager(@NotNull Project project,
                                                                              @Nullable String customPath) {
    FilePath customShelfFilePath = customPath != null ? VcsUtil.getFilePath(myPathMacroSubstitutor.expandPath(customPath)) : null;
    //don't collapse custom paths
    final boolean shouldCollapsePath = !VcsConfiguration.getInstance(myProject).USE_CUSTOM_SHELF_PATH;
    return SchemeManagerFactory.getInstance(project)
      .create(customShelfFilePath != null ? customShelfFilePath.getName() : SHELVE_MANAGER_DIR_PATH,
              new NonLazySchemeProcessor<ShelvedChangeList, ShelvedChangeList>() {
                @Override
                public @NotNull ShelvedChangeList readScheme(@NotNull Element element, boolean duringLoad) throws InvalidDataException {
                  return readOneShelvedChangeList(element);
                }

                @Override
                public @NotNull Parent writeScheme(@NotNull ShelvedChangeList scheme) throws WriteExternalException {
                  Element child = new Element(ELEMENT_CHANGELIST);
                  ShelvedChangeList.writeExternal(scheme, child, shouldCollapsePath ? myPathMacroSubstitutor : null);
                  return child;
                }
              }, null, customPath != null ? Paths.get(customPath) : null);
  }

  void projectOpened() {
    try {
      schemeManager.loadSchemes();
      //workaround for ignoring not valid patches, because readScheme doesn't support nullable value as it should be
      filterNonValidShelvedChangeLists();
      markDeletedSystemUnshelved();
      cleanDeletedOlderOneWeek();
      notifyStateChanged();
    }
    catch (Exception e) {
      LOG.error("Couldn't read shelf information", e);
    }
  }

  private void filterNonValidShelvedChangeLists() {
    List<ShelvedChangeList> allSchemes = new ArrayList<>(schemeManager.getAllSchemes());
    for (int i = 0, size = allSchemes.size(); i < size; i++) {
      ShelvedChangeList t = allSchemes.get(i);
      if (!t.isValid()) {
        schemeManager.removeScheme(t);
      }
    }
  }

  public void checkAndMigrateUnderProgress(@NotNull File fromFile, @NotNull File toFile, boolean wasCustom) {
    final SchemeManager<ShelvedChangeList> newSchemeManager = createShelveSchemeManager(myProject, VcsUtil.getFilePath(toFile).getPath());
    newSchemeManager.loadSchemes();
    if (VcsConfiguration.getInstance(myProject).MOVE_SHELVES && fromFile.exists()) {
      new Task.Modal(myProject, VcsBundle.message("shelve.copying.shelves.to.progress"), true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          for (ShelvedChangeList list : schemeManager.getAllSchemes()) {
            if (!list.isValid()) continue;
            try {
              Path newTargetDirectory = suggestPatchName(myProject, list.getDescription(), toFile, "").toPath();
              ShelvedChangeList migratedList = createChangelistCopyWithChanges(list, newTargetDirectory);
              newSchemeManager.addScheme(migratedList, false);
              indicator.checkCanceled();
            }
            catch (IOException e) {
              LOG.error("Can't copy patch file: " + list.getPath());
            }
          }
          clearShelvedLists(schemeManager.getAllSchemes(), false);
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
              vcsConfiguration.CUSTOM_SHELF_PATH = FileUtil.toSystemIndependentName(fromFile.getPath());
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
    SchemeManagerFactory.getInstance(myProject).dispose(schemeManager);
    schemeManager = newSchemeManager;
    notifyStateChanged();
  }

  public @NotNull File getShelfResourcesDirectory() {
    return schemeManager.getRootDirectory();
  }

  private @NotNull ShelvedChangeList readOneShelvedChangeList(@NotNull Element element) throws InvalidDataException {
    return ShelvedChangeList.readExternal(element, myPathMacroSubstitutor);
  }

  //load old shelf information from workspace.xml without moving .patch and binary files into new directory
  private void migrateOldShelfInfo(@NotNull Element element, boolean recycled) throws InvalidDataException, IOException {
    for (Element changeSetElement : element.getChildren(recycled ? ELEMENT_RECYCLED_CHANGELIST : ELEMENT_CHANGELIST)) {
      ShelvedChangeList list = readOneShelvedChangeList(changeSetElement);
      if (!list.isValid()) {
        break;
      }
      Path uniqueDir = generateUniqueSchemePatchDir(list.getDescription(), false);
      list.setName(uniqueDir.getFileName().toString());
      list.setRecycled(recycled);
      schemeManager.addScheme(list, false);
    }
  }

  public @NotNull @Unmodifiable List<ShelvedChangeList> getShelvedChangeLists() {
    return getRecycled(false);
  }

  private @Unmodifiable @NotNull List<ShelvedChangeList> getRecycled(boolean recycled) {
    List<ShelvedChangeList> collection = schemeManager.getAllSchemes();
    if (collection.isEmpty()) {
      return Collections.emptyList();
    }

    List<ShelvedChangeList> result = new ArrayList<>();
    for (ShelvedChangeList t : collection) {
      if (recycled == t.isRecycled() && !t.isDeleted()) {
        result.add(t);
      }
    }
    return List.copyOf(result);
  }

  public @NotNull @Unmodifiable List<ShelvedChangeList> getAllLists() {
    return List.copyOf(schemeManager.getAllSchemes());
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

  private @NotNull ShelvedChangeList createShelfFromChanges(@NotNull Collection<? extends Change> changes,
                                                            String commitMessage,
                                                            boolean markToBeDeleted,
                                                            boolean honorExcludedFromCommit) throws VcsException, IOException {
    if (changes.isEmpty()) {
      LOG.warn("Creating an empty shelved list", new Throwable());
    }
    LOG.debug("Shelving of " + changes.size() + " changes...");

    try {
      return computeWithSpanThrows(myTracer.spanBuilder(Shelve.TotalShelving.getName()).setAttribute("changesSize", changes.size()), __ -> {
        Path schemePatchDir = generateUniqueSchemePatchDir(commitMessage, true);
        List<Change> textChanges = new ArrayList<>();
        final List<ShelvedBinaryFile> binaryFiles = new ArrayList<>();
        for (Change change : changes) {
          if (ChangesUtil.getFilePath(change).getIOFile().isDirectory()) {
            continue;
          }
          if (IdeaTextPatchBuilder.isBinaryRevision(change.getBeforeRevision()) ||
              IdeaTextPatchBuilder.isBinaryRevision(change.getAfterRevision())) {
            binaryFiles.add(shelveBinaryFile(schemePatchDir, change));
          }
          else {
            textChanges.add(change);
          }
        }

        if (textChanges.isEmpty() && binaryFiles.isEmpty()) {
          LOG.warn("Created an empty shelved list, ignored changes: " + changes);
        }

        Path patchFile = getPatchFileInConfigDir(schemePatchDir);
        List<FilePatch> patches = new ArrayList<>(buildAndSavePatchInBatches(patchFile, textChanges, honorExcludedFromCommit));

        ShelvedChangeList changeList = new ShelvedChangeList(patchFile, commitMessage.replace('\n', ' '), binaryFiles,
                                                             createShelvedChangesFromFilePatches(myProject, patchFile, patches));
        changeList.markToDelete(markToBeDeleted);

        if (Registry.is("llm.vcs.shelve.title.generation")) {
          if (ShelveNameProvider.hasDefaultName(commitMessage)) {
            ShelveChangesNameSuggester.INSTANCE.suggestBetterName(myProject, new ShelveNamePatch(Files.readString(patchFile), patches.size()),
                                                                  name -> renameChangeList(changeList, name));
          }
        }

        changeList.setName(schemePatchDir.getFileName().toString());
        ProgressManager.checkCanceled();
        schemeManager.addScheme(changeList, false);
        return changeList;
      });
    }
    catch (IOException | VcsException | RuntimeException e) {
      throw e;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private List<FilePatch> buildAndSavePatchInBatches(@NotNull Path patchFile,
                                                     @NotNull List<Change> textChanges,
                                                     boolean honorExcludedFromCommit) throws IOException, VcsException {
    List<FilePatch> patches = new ArrayList<>();
    if (textChanges.isEmpty()) {
      savePatchFile(myProject, patchFile, patches, null, new CommitContext());
      return patches;
    }

    int batchIndex = 0;
    int baseContentsPreloadSize = Registry.intValue("git.shelve.load.base.in.batches", -1);
    int partitionSize = baseContentsPreloadSize > 0 ? baseContentsPreloadSize : textChanges.size();
    List<List<Change>> partition = Lists.partition(textChanges, partitionSize);
    for (List<Change> list : partition) {
      batchIndex++;
      int finalBatchIndex = batchIndex;
      try {
        runWithSpanThrows(myTracer.spanBuilder(Shelve.BatchShelving.getName()).setAttribute("batch", finalBatchIndex), ignored -> {
          try {
            if (baseContentsPreloadSize > 0) {
              TraceKt.use(myTracer.spanBuilder(Shelve.PreloadingBaseRevisions.getName()).setAttribute("changesSize", list.size()), __ -> {
                preloadBaseRevisions(list);
                return null;
              });
            }

            ProgressManager.checkCanceled();
            runWithSpanThrows(myTracer.spanBuilder(Shelve.BuildingPatches.getName()), __ -> {
              patches.addAll(IdeaTextPatchBuilder
                               .buildPatch(myProject, list, ProjectKt.getStateStore(myProject).getProjectBasePath(), false,
                                           honorExcludedFromCommit));
            });
            ProgressManager.checkCanceled();

            CommitContext commitContext = TraceKt.use(myTracer.spanBuilder(Shelve.StoringBaseRevision.getName()), __ -> {
              CommitContext context = new CommitContext();
              baseRevisionsOfDvcsIntoContext(list, context);
              return context;
            });

            runWithSpanThrows(myTracer.spanBuilder(Shelve.StoringPathFile.getName()), __ -> {
              savePatchFile(myProject, patchFile, patches, null, commitContext);
            });
          }
          finally {
            ProjectLevelVcsManager.getInstance(myProject).getContentRevisionCache().clearConstantCache();
          }
        });
      }
      catch (IOException | VcsException | RuntimeException e) {
        throw e;
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    return patches;
  }

  private void preloadBaseRevisions(@NotNull List<? extends Change> textChanges) {
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
      AbstractVcs vcs = Objects.requireNonNull(vcsRoot.getVcs());
      if (vcs.getDiffProvider() != null) {
        vcs.getDiffProvider().preloadBaseRevisions(Objects.requireNonNull(vcsRoot.getPath()), changesGroupedByRoot.get(vcsRoot));
      }
    }
  }

  private void rollbackChangesAfterShelve(@NotNull Collection<? extends Change> changes, boolean honorExcludedFromCommit) {
    final String operationName = UIUtil.removeMnemonic(RollbackChangesDialog.operationNameByChanges(myProject, changes));
    boolean modalContext = ApplicationManager.getApplication().isDispatchThread() && LaterInvocator.isInModalContext();
    TraceKt.use(myTracer.spanBuilder(Shelve.RollbackAfterShelve.getName()), __ -> {
      new RollbackWorker(myProject, operationName, modalContext)
        .doRollback(changes, true, null, VcsBundle.message("activity.name.shelve"), VcsActivity.Shelve, honorExcludedFromCommit);
      return null;
    });
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
      commitContext.putUserData(BaseRevisionTextPatchEP.ourBaseRevisions, toKeep);
    }
  }

  private boolean dvcsUsedInProject() {
    return ContainerUtil.exists(ProjectLevelVcsManager.getInstance(myProject).getAllActiveVcss(),
                                vcs -> VcsType.distributed.equals(vcs.getType()));
  }

  public @NotNull ShelvedChangeList importFilePatches(String fileName,
                                                      List<? extends FilePatch> patches,
                                                      List<? extends PatchEP> patchTransitExtensions)
    throws IOException {
    try {
      Path schemePatchDir = generateUniqueSchemePatchDir(fileName, true);
      Path patchFile = getPatchFileInConfigDir(schemePatchDir);
      savePatchFile(myProject, patchFile, patches, patchTransitExtensions, new CommitContext());
      ShelvedChangeList changeList = new ShelvedChangeList(patchFile, fileName.replace('\n', ' '), new SmartList<>(),
                                                           createShelvedChangesFromFilePatches(myProject, patchFile, patches));
      changeList.setName(schemePatchDir.getFileName().toString());
      schemeManager.addScheme(changeList, false);
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

  @RequiresBackgroundThread
  public List<ShelvedChangeList> importChangeLists(@NotNull Collection<? extends VirtualFile> files,
                                                   @NotNull Consumer<? super VcsException> exceptionConsumer) {
    final List<ShelvedChangeList> result = new ArrayList<>(files.size());
    try {
      final FilesProgress filesProgress = new FilesProgress(files.size(), VcsBundle.message("shelve.import.to.progress"));
      for (VirtualFile file : files) {
        filesProgress.updateIndicator(file);
        String description = file.getNameWithoutExtension().replace('_', ' ');
        try {
          Path schemeNameDir = generateUniqueSchemePatchDir(description, true);
          Path patchFile = getPatchFileInConfigDir(schemeNameDir);
          List<? extends FilePatch> filePatches = loadPatchesWithoutContent(myProject, file.toNioPath(), new CommitContext());
          if (!filePatches.isEmpty()) {
            Files.copy(file.toNioPath(), patchFile);
            ShelvedChangeList list = new ShelvedChangeList(patchFile, description, new SmartList<>(),
                                                           createShelvedChangesFromFilePatches(myProject, patchFile, filePatches),
                                                           file.getTimeStamp());
            list.setName(schemeNameDir.getFileName().toString());
            schemeManager.addScheme(list, false);
            result.add(list);
          }
        }
        catch (Exception e) {
          exceptionConsumer.accept(new VcsException(e));
        }
      }
    }
    finally {
      notifyStateChanged();
    }
    return result;
  }

  private ShelvedBinaryFile shelveBinaryFile(@NotNull Path schemePatchDir, final Change change) throws IOException {
    final ContentRevision beforeRevision = change.getBeforeRevision();
    final ContentRevision afterRevision = change.getAfterRevision();
    File beforeFile = beforeRevision == null ? null : beforeRevision.getFile().getIOFile();
    File afterFile = afterRevision == null ? null : afterRevision.getFile().getIOFile();
    String shelvedPath = null;
    if (afterFile != null) {
      String shelvedFileName = afterFile.getName();
      String name = FileUtilRt.getNameWithoutExtension(shelvedFileName);
      String extension = FileUtilRt.getExtension(shelvedFileName);
      File shelvedFile = FileUtil.findSequentNonexistentFile(schemePatchDir.toFile(), name, extension);
      FileUtil.copy(afterRevision.getFile().getIOFile(), shelvedFile);
      shelvedPath = shelvedFile.getPath();
    }
    String beforePath = ChangesUtil.getProjectRelativePath(myProject, beforeFile);
    String afterPath = ChangesUtil.getProjectRelativePath(myProject, afterFile);
    return new ShelvedBinaryFile(beforePath, afterPath, shelvedPath);
  }

  private void notifyStateChanged() {
    if (!myProject.isDisposed()) {
      myProject.getMessageBus().syncPublisher(SHELF_TOPIC).shelvedListsChanged();
    }
  }

  private @NotNull Path generateUniqueSchemePatchDir(@Nullable String defaultName, boolean createResourceDirectory) throws IOException {
    File shelfResourcesDirectory = getShelfResourcesDirectory();
    Path dir = suggestPatchName(myProject, defaultName, shelfResourcesDirectory, "").toPath();
    if (createResourceDirectory) {
      Files.createDirectories(dir);
    }
    return dir;
  }

  @CalledInAny
  public void unshelveChangeList(final ShelvedChangeList changeList,
                                 final @Nullable List<ShelvedChange> changes,
                                 final @Nullable List<ShelvedBinaryFile> binaryFiles,
                                 final @Nullable LocalChangeList targetChangeList,
                                 boolean showSuccessNotification) {
    unshelveChangeList(changeList, changes, binaryFiles, targetChangeList, showSuccessNotification, isRemoveFilesFromShelf());
  }

  @CalledInAny
  private ApplyPatchStatus unshelveChangeList(final ShelvedChangeList changeList,
                                              final @Nullable List<ShelvedChange> changes,
                                              final @Nullable List<ShelvedBinaryFile> binaryFiles,
                                              final @Nullable LocalChangeList targetChangeList,
                                              boolean showSuccessNotification,
                                              boolean removeFilesFromShelf) {
    return unshelveChangeList(changeList, changes, binaryFiles, targetChangeList, showSuccessNotification, false, false, null, null,
                              removeFilesFromShelf);
  }

  @CalledInAny
  public ApplyPatchStatus unshelveChangeList(final ShelvedChangeList changeList,
                                             final @Nullable List<ShelvedChange> changes,
                                             final @Nullable List<ShelvedBinaryFile> binaryFiles,
                                             final @Nullable LocalChangeList targetChangeList,
                                             final boolean showSuccessNotification,
                                             final boolean systemOperation,
                                             final boolean reverse,
                                             @NlsContexts.Label String leftConflictTitle,
                                             @NlsContexts.Label String rightConflictTitle,
                                             boolean removeFilesFromShelf) {
    return unshelveChangeList(changeList, changes, binaryFiles, targetChangeList, showSuccessNotification, systemOperation, reverse,
                              leftConflictTitle, rightConflictTitle, removeFilesFromShelf, true);
  }

  @CalledInAny
  public ApplyPatchStatus unshelveChangeList(final ShelvedChangeList changeList,
                                             final @Nullable List<ShelvedChange> changes,
                                             final @Nullable List<ShelvedBinaryFile> binaryFiles,
                                             final @Nullable LocalChangeList targetChangeList,
                                             final boolean showSuccessNotification,
                                             final boolean systemOperation,
                                             final boolean reverse,
                                             @NlsContexts.Label String leftConflictTitle,
                                             @NlsContexts.Label String rightConflictTitle,
                                             boolean removeFilesFromShelf,
                                             boolean reportLocalHistoryActivity) {
    List<FilePatch> remainingPatches = new ArrayList<>();

    CommitContext commitContext = new CommitContext();
    commitContext.putUserData(BaseRevisionTextPatchEP.ourProvideStoredBaseRevisionTextKey, true);

    List<TextFilePatch> textFilePatches;
    try {
      textFilePatches = loadTextPatches(myProject, changeList, changes, remainingPatches, commitContext);
    }
    catch (IOException | PatchSyntaxException e) {
      LOG.info(e);
      PatchApplier.showError(myProject, VcsBundle.message("unshelve.loading.patch.error", e.getMessage()));
      return ApplyPatchStatus.FAILURE;
    }

    List<FilePatch> patches = new ArrayList<>(textFilePatches);

    List<ShelvedBinaryFile> remainingBinaries = new ArrayList<>();
    List<ShelvedBinaryFile> binaryFilesToUnshelve = getBinaryFilesToUnshelve(changeList, binaryFiles, remainingBinaries);

    for (ShelvedBinaryFile shelvedBinaryFile : binaryFilesToUnshelve) {
      patches.add(new ShelvedBinaryFilePatch(shelvedBinaryFile));
    }

    VirtualFile baseDir = LocalFileSystem.getInstance().findFileByNioFile(ProjectKt.getStateStore(myProject).getProjectBasePath());
    ActivityId activityId = reportLocalHistoryActivity ? VcsActivity.Unshelve : null;
    PatchApplier patchApplier = new PatchApplier(myProject, baseDir,
                                                 patches, targetChangeList, commitContext, reverse, leftConflictTitle,
                                                 rightConflictTitle, VcsBundle.message("activity.name.unshelve"), activityId);
    ApplyPatchStatus status = patchApplier.execute(showSuccessNotification, systemOperation);
    if (removeFilesFromShelf) {
      remainingPatches.addAll(patchApplier.getRemainingPatches());
      remainingPatches.addAll(patchApplier.getFailedPatches());
      ModalityUiUtil.invokeLaterIfNeeded(ModalityState.nonModal(), myProject.getDisposed(), () -> {
        updateListAfterUnshelve(changeList, remainingPatches, remainingBinaries, commitContext);
      });
    }
    return status;
  }

  @NotNull
  @RequiresEdt
  Map<ShelvedChangeList, Date> deleteShelves(@NotNull List<ShelvedChangeList> shelvedListsToDelete,
                                             @NotNull List<ShelvedChangeList> shelvedListsFromChanges,
                                             @NotNull List<ShelvedChange> changesToDelete,
                                             @NotNull List<ShelvedBinaryFile> binariesToDelete) {
    // filter changes
    List<ShelvedChangeList> shelvedListsFromChangesToDelete = new ArrayList<>(shelvedListsFromChanges);
    shelvedListsFromChangesToDelete.removeAll(shelvedListsToDelete);

    if (shelvedListsFromChangesToDelete.size() + binariesToDelete.size() == 0 && shelvedListsToDelete.isEmpty()) {
      return Collections.emptyMap();
    }

    //store original dates to restore if needed
    Map<ShelvedChangeList, Date> deletedListsWithOriginalDate = new HashMap<>();
    for (ShelvedChangeList changeList : shelvedListsToDelete) {
      Date originalDate = changeList.getDate();
      if (changeList.isDeleted()) {
        deleteChangeListCompletely(changeList);
      }
      else {
        markChangeListAsDeleted(changeList);
        deletedListsWithOriginalDate.put(changeList, originalDate);
      }
    }

    for (ShelvedChangeList list : shelvedListsFromChangesToDelete) {
      Date originalDate = list.getDate();
      boolean wasDeleted = list.isDeleted();
      ShelvedChangeList newListWithDeletedChanges = removeChangesFromChangeList(list, changesToDelete, binariesToDelete);
      if (newListWithDeletedChanges != null) {
        deletedListsWithOriginalDate.put(newListWithDeletedChanges, originalDate);
      }
      else if (!wasDeleted) {
        //entire list became deleted because no changes remained
        ShelvedChangeList shelvedChangeList = schemeManager.findSchemeByName(list.getName());
        if (shelvedChangeList != null && shelvedChangeList.isDeleted()) {
          deletedListsWithOriginalDate.put(shelvedChangeList, originalDate);
        }
      }
    }
    return deletedListsWithOriginalDate;
  }

  @RequiresEdt
  private @Nullable ShelvedChangeList removeChangesFromChangeList(@NotNull ShelvedChangeList list,
                                                                  @NotNull List<ShelvedChange> changes,
                                                                  @NotNull List<ShelvedBinaryFile> binaryFiles) {
    List<ShelvedBinaryFile> remainingBinaries = new ArrayList<>(list.getBinaryFiles());
    remainingBinaries.removeAll(binaryFiles);

    final CommitContext commitContext = new CommitContext();
    commitContext.putUserData(BaseRevisionTextPatchEP.ourProvideStoredBaseRevisionTextKey, true);

    final List<FilePatch> remainingPatches = new ArrayList<>();
    try {
      loadTextPatches(myProject, list, changes, remainingPatches, commitContext);
    }
    catch (IOException | PatchSyntaxException e) {
      LOG.info(e);
      VcsImplUtil.showErrorMessage(myProject, e.getMessage(),
                                   VcsBundle.message("shelve.delete.files.from.changelist.error", list.getDescription()));
      return null;
    }
    return saveRemainingPatchesIfNeeded(list, remainingPatches, remainingBinaries, commitContext, true);
  }

  public boolean isRemoveFilesFromShelf() {
    return myState.myRemoveFilesFromShelf;
  }

  public void setRemoveFilesFromShelf(boolean removeFilesFromShelf) {
    myState.myRemoveFilesFromShelf = removeFilesFromShelf;
  }

  private void markDeletedSystemUnshelved() {
    List<ShelvedChangeList> systemUnshelved =
      ContainerUtil.filter(schemeManager.getAllSchemes(), list -> (list.isRecycled()) && list.isMarkedToDelete());
    for (ShelvedChangeList list : systemUnshelved) {
      list.setDeleted(true);
      list.markToDelete(false);
    }
  }

  private void cleanDeletedOlderOneWeek() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.DAY_OF_MONTH, -7);
    clean(list -> (list.isDeleted() && list.getDate().before(new Date(cal.getTimeInMillis()))));
  }

  public void cleanUnshelved(long timeBefore) {
    final Date limitDate = new Date(timeBefore);
    clean(l -> l.isRecycled() && l.getDate().before(limitDate));
  }

  private void clean(@NotNull Condition<? super ShelvedChangeList> condition) {
    final List<ShelvedChangeList> toDelete = ContainerUtil.filter(schemeManager.getAllSchemes(), condition);
    clearShelvedLists(toDelete, true);
  }

  @RequiresEdt
  public void shelveSilentlyUnderProgress(@NotNull List<? extends Change> changes, boolean rollbackChanges) {
    final List<ShelvedChangeList> result = new ArrayList<>();
    new Task.Backgroundable(myProject, VcsBundle.message("shelve.changes.progress.title"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        result.addAll(shelveChangesInSeparatedLists(changes, rollbackChanges));
      }

      @Override
      public void onSuccess() {
        VcsNotifier.getInstance(myProject).notifySuccess(SHELVE_SUCCESSFUL, "",
                                                         VcsBundle.message("shelve.successful.message"));
        if (result.size() == 1 && isShelfContentActive()) {
          ShelvedChangesViewManager.getInstance(myProject).startEditing(result.get(0));
        }
      }
    }.queue();
  }

  public void showGotItTooltip(@NotNull Project project, @Nullable Component component) {
    if (component != null) {
      ShelveNameProvider.showGotItTooltip(project, component);
    }
  }

  private void rememberShelvingFiles(@NotNull Collection<? extends Change> changes) {
    myShelvingFiles = new HashSet<>(ContainerUtil.map2SetNotNull(changes, Change::getVirtualFile));
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

  private @NotNull List<ShelvedChangeList> shelveChangesInSeparatedLists(@NotNull Collection<? extends Change> changes,
                                                                         boolean rollbackChanges) {
    List<String> failedChangeLists = new ArrayList<>();
    List<ShelvedChangeList> result = new ArrayList<>();
    List<Change> shelvedChanges = new ArrayList<>();

    try {
      ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
      if (!changeListManager.areChangeListsEnabled()) {
        LOG.warn("Changelists are disabled", new Throwable());
      }

      SHELVED_FILES_LOCK.writeLock().lock();
      rememberShelvingFiles(changes);
      List<LocalChangeList> changeLists = changeListManager.getChangeLists();
      for (LocalChangeList list : changeLists) {
        Set<Change> changeSet = new HashSet<>(list.getChanges());

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

    if (rollbackChanges) {
      rollbackChangesAfterShelve(shelvedChanges, false);
    }

    if (!failedChangeLists.isEmpty()) {
      VcsNotifier.getInstance(myProject).notifyError(
        SHELVE_FAILED,
        VcsBundle.message("shelve.failed.title"),
        VcsBundle.message("shelve.failed.message",
                          failedChangeLists.size(),
                          new HtmlBuilder().appendWithSeparators(HtmlChunk.text(","),
                                                                 ContainerUtil.map(failedChangeLists, HtmlChunk::text))
                            .toString()));
    }
    return result;
  }

  public void unshelveSilentlyAsynchronously(final @NotNull Project project,
                                             final @NotNull List<ShelvedChangeList> selectedChangeLists,
                                             final @NotNull List<ShelvedChange> selectedChanges,
                                             final @NotNull List<ShelvedBinaryFile> selectedBinaryChanges,
                                             final @Nullable LocalChangeList forcePredefinedOneChangelist) {
    unshelveSilentlyAsynchronously(project, selectedChangeLists, selectedChanges, selectedBinaryChanges, forcePredefinedOneChangelist,
                                   isRemoveFilesFromShelf());
  }

  public void unshelveSilentlyAsynchronously(final @NotNull Project project,
                                             final @NotNull List<ShelvedChangeList> selectedChangeLists,
                                             final @NotNull List<ShelvedChange> selectedChanges,
                                             final @NotNull List<ShelvedBinaryFile> selectedBinaryChanges,
                                             final @Nullable LocalChangeList forcePredefinedOneChangelist, boolean removeFilesFromShelf) {
    ProgressManager.getInstance().run(new Task.Backgroundable(project, VcsBundle.message("unshelve.changes.progress.title"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        for (ShelvedChangeList changeList : selectedChangeLists) {
          List<ShelvedChange> changesForChangelist =
            new ArrayList<>(ContainerUtil.intersection(Objects.requireNonNull(changeList.getChanges()), selectedChanges));
          List<ShelvedBinaryFile> binariesForChangelist =
            new ArrayList<>(ContainerUtil.intersection(changeList.getBinaryFiles(), selectedBinaryChanges));
          boolean shouldUnshelveAllList = changesForChangelist.isEmpty() && binariesForChangelist.isEmpty();
          ApplyPatchStatus status = unshelveChangeList(
            changeList,
            shouldUnshelveAllList ? null : changesForChangelist,
            shouldUnshelveAllList ? null : binariesForChangelist,
            forcePredefinedOneChangelist != null ? forcePredefinedOneChangelist : getChangeListUnshelveTo(changeList),
            true,
            removeFilesFromShelf);
          ChangeListManagerEx.getInstanceEx(myProject).waitForUpdate();

          if (status == ApplyPatchStatus.ABORT) {
            break;
          }
        }
      }
    });
  }

  private @Nullable LocalChangeList getChangeListUnshelveTo(@NotNull ShelvedChangeList list) {
    ChangeListManager manager = ChangeListManager.getInstance(myProject);
    if (!manager.areChangeListsEnabled()) return null;
    if (!VcsApplicationSettings.getInstance().CREATE_CHANGELISTS_AUTOMATICALLY) return null;
    LocalChangeList localChangeList = getPredefinedChangeList(list, manager);
    return localChangeList != null ? localChangeList : manager.addChangeList(getChangeListNameForUnshelve(list), "");
  }

  @RequiresEdt
  public void updateListAfterUnshelve(@NotNull ShelvedChangeList listToUpdate,
                                      @NotNull List<? extends FilePatch> patches,
                                      @NotNull List<ShelvedBinaryFile> binaries,
                                      @NotNull CommitContext commitContext) {
    saveRemainingPatchesIfNeeded(listToUpdate, patches, binaries, commitContext, false);
  }

  /**
   * Return newly created shelved list with applied (deleted or unshelved) changes or null if no additional shelved list was created
   * 1. if no changes remained in the original list - delete or mark applied (recycled) entire list - > no new list created, return null;
   * 2. if there are some applied (deleted) changes and something remained it the original list then create separated list for applied
   * changes and delete these changes from the original list - > in this case new list with applied (deleted) changes will be a return value
   */
  @RequiresEdt
  private @Nullable ShelvedChangeList saveRemainingPatchesIfNeeded(final ShelvedChangeList changeList,
                                                                   final List<? extends FilePatch> remainingPatches,
                                                                   final List<ShelvedBinaryFile> remainingBinaries,
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
  @RequiresEdt
  private @Nullable ShelvedChangeList saveRemainingAndRecycleOthers(final @NotNull ShelvedChangeList changeList,
                                                                    final List<? extends FilePatch> remainingPatches,
                                                                    final List<ShelvedBinaryFile> remainingBinaries,
                                                                    CommitContext commitContext,
                                                                    boolean delete) {

    try {
      ShelvedChangeList listCopy = createChangelistCopyWithChanges(changeList, generateUniqueSchemePatchDir(changeList.getDescription(), true));
      listCopy.updateDate();
      //changes should be loaded
      saveRemainingChangesInList(changeList, remainingPatches, remainingBinaries, commitContext);

      removeFromListWithChanges(listCopy, Objects.requireNonNull(changeList.getChanges()), changeList.getBinaryFiles(), commitContext);
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
                                          List<ShelvedBinaryFile> remainingBinaries, CommitContext commitContext) {
    writePatchesToFile(myProject, changeList.getPath(), remainingPatches, commitContext);

    changeList.getBinaryFiles().retainAll(remainingBinaries);
    changeList.setChanges(createShelvedChangesFromFilePatches(myProject, changeList.getPath(), remainingPatches));
  }

  void saveListAsScheme(@NotNull ShelvedChangeList list) {
    if (!list.getBinaryFiles().isEmpty() || !ContainerUtil.isEmpty(list.getChanges())) {
      // all newly create ShelvedChangeList have to be added to SchemesManger as new scheme
      schemeManager.addScheme(list, false);
    }
  }

  @NotNull ShelvedChangeList createChangelistCopyWithChanges(@NotNull ShelvedChangeList changeList, @NotNull Path targetDir)
    throws IOException {
    Path newPath = getPatchFileInConfigDir(targetDir);
    Files.createDirectories(newPath.getParent());
    Files.copy(changeList.getPath(), newPath);
    changeList.loadChangesIfNeeded(myProject);

    ShelvedChangeList listCopy = new ShelvedChangeList(newPath, changeList.getDescription(),
                                                       copyBinaryFiles(changeList, targetDir),
                                                       copyTextFiles(myProject, changeList, newPath),
                                                       changeList.getDate().getTime());
    listCopy.markToDelete(changeList.isMarkedToDelete());
    listCopy.setRecycled(changeList.isRecycled());
    listCopy.setDeleted(changeList.isDeleted());
    listCopy.setName(targetDir.getFileName().toString());
    return listCopy;
  }

  public void restoreList(@NotNull ShelvedChangeList shelvedChangeList, @NotNull Date restoreDate) {
    ShelvedChangeList list = schemeManager.findSchemeByName(shelvedChangeList.getName());
    if (list == null) {
      return;
    }
    list.setDeleted(false);
    list.setDate(restoreDate);
    notifyStateChanged();
  }

  public @NotNull @Unmodifiable List<ShelvedChangeList> getRecycledShelvedChangeLists() {
    return getRecycled(true);
  }

  public @Unmodifiable List<ShelvedChangeList> getDeletedLists() {
    return List.copyOf(ContainerUtil.filter(schemeManager.getAllSchemes(), ShelvedChangeList::isDeleted));
  }

  void clearShelvedLists(@NotNull List<ShelvedChangeList> shelvedLists, boolean updateView) {
    if (shelvedLists.isEmpty()) return;
    for (ShelvedChangeList list : shelvedLists) {
      deleteResources(list);
      schemeManager.removeScheme(list);
    }
    if (updateView) {
      notifyStateChanged();
    }
  }

  public @NotNull Collection<VirtualFile> getShelvingFiles() {
    return new HashSet<>(ContainerUtil.notNullize(myShelvingFiles));
  }

  private void removeFromListWithChanges(final @NotNull ShelvedChangeList listCopy,
                                         @NotNull List<ShelvedChange> shelvedChanges,
                                         @NotNull List<ShelvedBinaryFile> shelvedBinaryChanges,
                                         @NotNull CommitContext commitContext) {
    //listCopy should contain loaded changes
    removeBinaries(listCopy, shelvedBinaryChanges);
    removeChanges(listCopy, shelvedChanges);

    // create patch file based on filtered changes
    try {
      final List<FilePatch> patches = new ArrayList<>();
      List<TextFilePatch> filePatches = loadPatches(myProject, listCopy.getPath(), commitContext);
      for (ShelvedChange change : Objects.requireNonNull(listCopy.getChanges())) {
        patches.add(ContainerUtil.find(filePatches, patch -> change.getBeforePath().equals(patch.getBeforeName())));
      }
      writePatchesToFile(myProject, listCopy.getPath(), patches, commitContext);
    }
    catch (IOException | PatchSyntaxException e) {
      LOG.info(e);
      // left file as is
    }
  }

  private void recycleChangeList(final @NotNull ShelvedChangeList changeList) {
    changeList.setRecycled(true);
    changeList.updateDate();
    if (changeList.isMarkedToDelete()) {
      changeList.markToDelete(false);
      changeList.setDeleted(true);
    }
    notifyStateChanged();
  }

  private void deleteChangeListCompletely(final @NotNull ShelvedChangeList changeList) {
    deleteResources(changeList);
    schemeManager.removeScheme(changeList);
    notifyStateChanged();
  }

  void markChangeListAsDeleted(final @NotNull ShelvedChangeList changeList) {
    changeList.setDeleted(true);
    changeList.updateDate();
    notifyStateChanged();
  }

  private void deleteResources(@NotNull ShelvedChangeList changeList) {
    try {
      Files.deleteIfExists(changeList.getPath());
    }
    catch (IOException ignore) {
    }
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
    changeList.setDescription(newName);
    notifyStateChanged();
  }

  public boolean isShowRecycled() {
    return myState.myShowRecycled;
  }

  public void setShowRecycled(final boolean showRecycled) {
    myState.myShowRecycled = showRecycled;
  }

  public @NotNull Collection<String> getGrouping() {
    return myState.groupingKeys;
  }

  public void setGrouping(@NotNull Collection<String> grouping) {
    myState.groupingKeys.clear();
    myState.groupingKeys.addAll(grouping);
  }

  public static ShelveChangesManager getInstance(@NotNull Project project) {
    return project.getService(ShelveChangesManager.class);
  }

  /**
   * System independent file-path for non-default project
   *
   * @return path to default shelf directory e.g. {@code "<Project>/.idea/shelf"}
   */
  public static @NotNull Path getDefaultShelfPath(@NotNull Project project) {
    IProjectStore store = ProjectKt.getStateStore(project);
    return store.getProjectFilePath().getParent().resolve(ProjectKt.isDirectoryBased(project) ? SHELVE_MANAGER_DIR_PATH
                                                                                              : "." + SHELVE_MANAGER_DIR_PATH);
  }

  /**
   * System independent file-path for non-default project
   *
   * @return path to custom shelf directory if set. Otherwise return default shelf directory e.g. {@code "<Project>/.idea/shelf"}
   */
  public static @NotNull String getShelfPath(@NotNull Project project) {
    VcsConfiguration vcsConfiguration = VcsConfiguration.getInstance(project);
    if (vcsConfiguration.USE_CUSTOM_SHELF_PATH) {
      return Objects.requireNonNull(vcsConfiguration.CUSTOM_SHELF_PATH);
    }
    return getDefaultShelfPath(project).toString().replace(File.separatorChar, '/');
  }

  private static @NotNull List<ShelvedChange> copyTextFiles(@NotNull Project project,
                                                            @NotNull ShelvedChangeList changeList,
                                                            @NotNull Path newPatchPath) {
    List<ShelvedChange> copied = new ArrayList<>();
    for (ShelvedChange change : changeList.getChanges()) {
      copied.add(ShelvedChange.copyToNewPatch(project, newPatchPath, change));
    }
    return copied;
  }

  private static @NotNull List<ShelvedBinaryFile> copyBinaryFiles(@NotNull ShelvedChangeList list, @NotNull Path targetDirectory)
    throws IOException {
    Files.createDirectories(targetDirectory);
    List<ShelvedBinaryFile> copied = new ArrayList<>();
    for (ShelvedBinaryFile file : list.getBinaryFiles()) {
      if (file.SHELVED_PATH != null) {
        Path shelvedFile = Paths.get(file.SHELVED_PATH);
        if (!StringUtil.isEmptyOrSpaces(file.AFTER_PATH) && Files.exists(shelvedFile)) {
          Path newShelvedFile = targetDirectory.resolve(PathUtil.getFileName(file.AFTER_PATH));
          try {
            Files.copy(shelvedFile, newShelvedFile);
            copied.add(new ShelvedBinaryFile(file.BEFORE_PATH, file.AFTER_PATH,
                                             FileUtil.toSystemIndependentName(newShelvedFile.toString())));
          }
          catch (IOException e) {
            LOG.warn("Can't copy binary file: " + list.getPath(), e);
          }
        }
      }
    }
    return copied;
  }

  private static @NotNull Path getPatchFileInConfigDir(@NotNull Path schemePatchDir) {
    return schemePatchDir.resolve(DEFAULT_PATCH_NAME + "." + VcsConfiguration.PATCH);
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

  // for create patch only
  public static @NotNull File suggestPatchName(@NotNull Project project, @Nullable String commitMessage, File file, String extension) {
    @NonNls String defaultPath = shortenAndSanitize(commitMessage);
    while (true) {
      File nonexistentFile = FileUtil.findSequentNonexistentFile(file, defaultPath,
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

  private static @NotNull String shortenAndSanitize(@Nullable String commitMessage) {
    @NonNls String defaultPath = PathUtil.suggestFileName(notNullize(commitMessage));
    if (defaultPath.isEmpty()) {
      defaultPath = "unnamed";
    }
    if (defaultPath.length() > PatchNameChecker.MAX - 10) {
      defaultPath = defaultPath.substring(0, PatchNameChecker.MAX - 10);
    }
    return defaultPath;
  }

  private static List<TextFilePatch> loadTextPatches(@NotNull Project project,
                                                     ShelvedChangeList changeList,
                                                     List<ShelvedChange> changes,
                                                     List<? super FilePatch> remainingPatches,
                                                     CommitContext commitContext)
    throws IOException, PatchSyntaxException {
    List<TextFilePatch> textFilePatches = loadPatches(project, changeList.getPath(), commitContext);

    if (changes != null) {
      textFilePatches = ContainerUtil.filter(textFilePatches, patch -> {
        if (needUnshelve(patch, changes)) {
          return true;
        }
        else {
          remainingPatches.add(patch);
          return false;
        }
      });
    }
    return textFilePatches;
  }

  @ApiStatus.Internal
  @RequiresEdt
  public static void unshelveSilentlyWithDnd(@NotNull Project project,
                                             @NotNull ShelvedChangeListDragBean shelvedChangeListDragBean,
                                             @Nullable LocalChangeList targetChangeList,
                                             boolean removeFilesFromShelf) {
    FileDocumentManager.getInstance().saveAllDocuments();
    getInstance(project).unshelveSilentlyAsynchronously(project, shelvedChangeListDragBean.getShelvedChangelists(),
                                                        shelvedChangeListDragBean.getChanges(),
                                                        shelvedChangeListDragBean.getBinaryFiles(), targetChangeList,
                                                        removeFilesFromShelf);
  }

  private static List<ShelvedBinaryFile> getBinaryFilesToUnshelve(final ShelvedChangeList changeList,
                                                                  final List<ShelvedBinaryFile> binaryFiles,
                                                                  final List<? super ShelvedBinaryFile> remainingBinaries) {
    if (binaryFiles == null) {
      return new ArrayList<>(changeList.getBinaryFiles());
    }

    List<ShelvedBinaryFile> result = new ArrayList<>();
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

  private static boolean needUnshelve(final FilePatch patch, final List<ShelvedChange> changes) {
    for (ShelvedChange change : changes) {
      if (Objects.equals(patch.getBeforeName(), change.getBeforePath())) {
        return true;
      }
    }
    return false;
  }

  private static void writePatchesToFile(@Nullable Project project,
                                         @NotNull Path path,
                                         @NotNull List<? extends FilePatch> remainingPatches,
                                         @Nullable CommitContext commitContext) {
    try (BufferedWriter writer = Files.newBufferedWriter(path)) {
      UnifiedDiffWriter.write(project, remainingPatches, writer, "\n", commitContext);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private static void removeChanges(@NotNull ShelvedChangeList list, @NotNull List<ShelvedChange> shelvedChanges) {
    for (Iterator<ShelvedChange> iterator = Objects.requireNonNull(list.getChanges()).iterator(); iterator.hasNext(); ) {
      final ShelvedChange change = iterator.next();

      boolean toRemove = ContainerUtil.exists(shelvedChanges, newChange ->
        Objects.equals(change.getBeforePath(), newChange.getBeforePath()) &&
        Objects.equals(change.getAfterPath(), newChange.getAfterPath())
      );
      if (toRemove) {
        iterator.remove();
      }
    }
  }

  private static void removeBinaries(@NotNull ShelvedChangeList list, @NotNull List<ShelvedBinaryFile> binaryFiles) {
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

  public static @NotNull List<TextFilePatch> loadPatches(Project project,
                                                         @NotNull Path patchPath,
                                                         @Nullable CommitContext commitContext) throws IOException, PatchSyntaxException {
    return loadPatches(project, patchPath, commitContext, true);
  }

  static @NotNull List<? extends FilePatch> loadPatchesWithoutContent(@NotNull Project project,
                                                                      @NotNull Path patchPath,
                                                                      @Nullable CommitContext commitContext)
    throws IOException, PatchSyntaxException {
    return loadPatches(project, patchPath, commitContext, false);
  }

  private static List<TextFilePatch> loadPatches(@NotNull Project project,
                                                 @NotNull Path patchPath,
                                                 @Nullable CommitContext commitContext,
                                                 boolean loadContent) throws IOException, PatchSyntaxException {
    char[] text;
    try (Reader reader = new InputStreamReader(Files.newInputStream(patchPath), StandardCharsets.UTF_8)) {
      text = FileUtilRt.loadText(reader, (int)Files.size(patchPath));
    }
    PatchReader reader = new PatchReader(new CharArrayCharSequence(text), loadContent);
    List<TextFilePatch> textFilePatches = reader.readTextPatches();
    ApplyPatchDefaultExecutor.applyAdditionalInfoBefore(project, reader.getAdditionalInfo(null), commitContext);
    return textFilePatches;
  }

  private static void savePatchFile(@NotNull Project project,
                                    @NotNull Path patchFile,
                                    @NotNull List<? extends FilePatch> patches,
                                    @Nullable List<? extends PatchEP> extensions,
                                    @NotNull CommitContext context) throws IOException {
    try (Writer writer = Files.newBufferedWriter(patchFile)) {
      UnifiedDiffWriter.write(project, ProjectKt.getStateStore(project).getProjectBasePath(), patches, writer, "\n", context, extensions);
    }
  }

  public static final class State {
    @OptionTag("remove_strategy")
    public boolean myRemoveFilesFromShelf;
    @Attribute("show_recycled")
    public boolean myShowRecycled;
    @XCollection
    public TreeSet<String> groupingKeys = new TreeSet<>();
  }
}
