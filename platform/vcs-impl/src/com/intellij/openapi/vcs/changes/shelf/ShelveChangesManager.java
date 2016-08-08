/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.concurrency.JobScheduler;
import com.intellij.lifecycle.PeriodicalTasksCloser;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.*;
import com.intellij.openapi.diff.impl.patch.apply.ApplyFilePatchBase;
import com.intellij.openapi.diff.impl.patch.formove.CustomBinaryPatchApplier;
import com.intellij.openapi.diff.impl.patch.formove.PatchApplier;
import com.intellij.openapi.options.NonLazySchemeProcessor;
import com.intellij.openapi.options.SchemeManager;
import com.intellij.openapi.options.SchemeManagerFactory;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchDefaultExecutor;
import com.intellij.openapi.vcs.changes.patch.PatchFileType;
import com.intellij.openapi.vcs.changes.patch.PatchNameChecker;
import com.intellij.openapi.vcs.changes.ui.RollbackChangesDialog;
import com.intellij.openapi.vcs.changes.ui.RollbackWorker;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.PathUtil;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import com.intellij.util.text.CharArrayCharSequence;
import com.intellij.util.text.UniqueNameGenerator;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.FilesProgress;
import org.jdom.Element;
import org.jdom.Parent;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.io.*;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ShelveChangesManager extends AbstractProjectComponent implements JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager");
  @NonNls private static final String ELEMENT_CHANGELIST = "changelist";
  @NonNls private static final String ELEMENT_RECYCLED_CHANGELIST = "recycled_changelist";
  @NonNls private static final String DEFAULT_PATCH_NAME = "shelved";
  @NonNls private static final String REMOVE_FILES_FROM_SHELF_STRATEGY = "remove_strategy";

  @NotNull private final TrackingPathMacroSubstitutor myPathMacroSubstitutor;
  @NotNull private final SchemeManager<ShelvedChangeList> mySchemeManager;
  private ScheduledFuture<?> myCleaningFuture;
  private boolean myRemoveFilesFromShelf;

  public static ShelveChangesManager getInstance(Project project) {
    return PeriodicalTasksCloser.getInstance().safeGetComponent(project, ShelveChangesManager.class);
  }

  private static final String SHELVE_MANAGER_DIR_PATH = "shelf";
  private final MessageBus myBus;

  @NonNls private static final String ATTRIBUTE_SHOW_RECYCLED = "show_recycled";
  @NotNull private final CompoundShelfFileProcessor myFileProcessor;

  public static final Topic<ChangeListener> SHELF_TOPIC = new Topic<>("shelf updates", ChangeListener.class);
  private boolean myShowRecycled;

  public ShelveChangesManager(final Project project, final MessageBus bus) {
    super(project);
    myPathMacroSubstitutor = PathMacroManager.getInstance(myProject).createTrackingSubstitutor();
    myBus = bus;
    mySchemeManager =
      SchemeManagerFactory.getInstance(project).create(SHELVE_MANAGER_DIR_PATH, new NonLazySchemeProcessor<ShelvedChangeList, ShelvedChangeList>() {
        @Nullable
        @Override
        public ShelvedChangeList readScheme(@NotNull Element element, boolean duringLoad) throws InvalidDataException {
          return readOneShelvedChangeList(element);
        }

        @NotNull
        @Override
        public Parent writeScheme(@NotNull ShelvedChangeList scheme) throws WriteExternalException {
          Element child = new Element(ELEMENT_CHANGELIST);
          scheme.writeExternal(child);
          myPathMacroSubstitutor.collapsePaths(child);
          return child;
        }
      });

    myCleaningFuture = JobScheduler.getScheduler().scheduleWithFixedDelay(new Runnable() {
      @Override
      public void run() {
        cleanSystemUnshelvedOlderOneWeek();
      }
    }, 1, 1, TimeUnit.DAYS);
    Disposer.register(project, new Disposable() {
      @Override
      public void dispose() {
        stopCleanScheduler();
      }
    });

    File shelfDirectory = mySchemeManager.getRootDirectory();
    myFileProcessor = new CompoundShelfFileProcessor(shelfDirectory);
    // do not try to ignore when new project created,
    // because it may lead to predefined ignore creation conflict; see ConvertExcludedToIgnoredTest etc
    if (shelfDirectory.exists()) {
      ChangeListManager.getInstance(project).addDirectoryToIgnoreImplicitly(shelfDirectory.getAbsolutePath());
    }
  }

  private void stopCleanScheduler() {
    if(myCleaningFuture!=null){
      myCleaningFuture.cancel(false);
      myCleaningFuture = null;
    }
  }

  @Override
  public void projectOpened() {
    try {
      mySchemeManager.loadSchemes();
      //workaround for ignoring not valid patches, because readScheme doesn't support nullable value as it should be
      filterNonValidShelvedChangeLists();
      cleanSystemUnshelvedOlderOneWeek();
    }
    catch (Exception e) {
      LOG.error("Couldn't read shelf information", e);
    }
  }

  private void filterNonValidShelvedChangeLists() {
    final List<ShelvedChangeList> allSchemes = ContainerUtil.newArrayList(mySchemeManager.getAllSchemes());
    ContainerUtil.process(allSchemes, new Processor<ShelvedChangeList>() {

      @Override
      public boolean process(ShelvedChangeList shelvedChangeList) {
        if (!shelvedChangeList.isValid()) {
          mySchemeManager.removeScheme(shelvedChangeList);
        }
        return true;
      }
    });
  }

  @NotNull
  public File getShelfResourcesDirectory() {
    return myFileProcessor.getBaseDir();
  }

  @NotNull
  private ShelvedChangeList readOneShelvedChangeList(@NotNull Element element) throws InvalidDataException {
    ShelvedChangeList data = new ShelvedChangeList();
    myPathMacroSubstitutor.expandPaths(element);
    data.readExternal(element);
    return data;
  }

  @Override
  @NonNls
  @NotNull
  public String getComponentName() {
    return "ShelveChangesManager";
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    final String showRecycled = element.getAttributeValue(ATTRIBUTE_SHOW_RECYCLED);
    myShowRecycled = showRecycled == null || Boolean.parseBoolean(showRecycled);
    String removeFilesStrategy = JDOMExternalizerUtil.readField(element, REMOVE_FILES_FROM_SHELF_STRATEGY);
    myRemoveFilesFromShelf = removeFilesStrategy != null && Boolean.parseBoolean(removeFilesStrategy);
    migrateOldShelfInfo(element, true);
    migrateOldShelfInfo(element, false);
  }

  //load old shelf information from workspace.xml without moving .patch and binary files into new directory
  private void migrateOldShelfInfo(@NotNull Element element, boolean recycled) throws InvalidDataException {
    for (Element changeSetElement : element.getChildren(recycled ? ELEMENT_RECYCLED_CHANGELIST : ELEMENT_CHANGELIST)) {
      ShelvedChangeList list = readOneShelvedChangeList(changeSetElement);
      if(!list.isValid()) break;
      File uniqueDir = generateUniqueSchemePatchDir(list.DESCRIPTION, false);
      list.setName(uniqueDir.getName());
      list.setRecycled(recycled);
      mySchemeManager.addNewScheme(list, false);
    }
  }

  /**
   * Should be called only once: when Settings Repository plugin runs first time
   *
   * @return collection of non-migrated or not deleted files to show a error somewhere outside
   */
  @NotNull
  public Collection<String> checkAndMigrateOldPatchResourcesToNewSchemeStorage() {
    Collection<String> nonMigratedPaths = ContainerUtil.newArrayList();
    for (ShelvedChangeList list : mySchemeManager.getAllSchemes()) {
      File patchDir = new File(myFileProcessor.getBaseDir(), list.getName());
      nonMigratedPaths.addAll(migrateIfNeededToSchemeDir(list, patchDir));
    }
    return nonMigratedPaths;
  }

  @NotNull
  private static Collection<String> migrateIfNeededToSchemeDir(@NotNull ShelvedChangeList list, @NotNull File targetDirectory) {
    // it should be enough for migration to check if resource directory exists. If any bugs appeared add isAncestor checks for each path
    if (targetDirectory.exists() || !targetDirectory.mkdirs()) return ContainerUtil.emptyList();
    Collection<String> nonMigratedPaths = ContainerUtil.newArrayList();
    //try to move .patch file
    File patchFile = new File(list.PATH);
    if (patchFile.exists()) {
      File newPatchFile = getPatchFileInConfigDir(targetDirectory);
      try {
        FileUtil.copy(patchFile, newPatchFile);
        list.PATH = FileUtil.toSystemIndependentName(newPatchFile.getPath());
        FileUtil.delete(patchFile);
      }
      catch (IOException e) {
        nonMigratedPaths.add(list.PATH);
      }
    }

    for (ShelvedBinaryFile file : list.getBinaryFiles()) {
      if (file.SHELVED_PATH != null) {
        File shelvedFile = new File(file.SHELVED_PATH);
        if (!StringUtil.isEmptyOrSpaces(file.AFTER_PATH) && shelvedFile.exists()) {
          File newShelvedFile = new File(targetDirectory, PathUtil.getFileName(file.AFTER_PATH));
          try {
            FileUtil.copy(shelvedFile, newShelvedFile);
            file.SHELVED_PATH = FileUtil.toSystemIndependentName(newShelvedFile.getPath());
            FileUtil.delete(shelvedFile);
          }
          catch (IOException e) {
            nonMigratedPaths.add(shelvedFile.getPath());
          }
        }
      }
    }
    return nonMigratedPaths;
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    element.setAttribute(ATTRIBUTE_SHOW_RECYCLED, Boolean.toString(myShowRecycled));
    JDOMExternalizerUtil.writeField(element, REMOVE_FILES_FROM_SHELF_STRATEGY, Boolean.toString(isRemoveFilesFromShelf()));
  }

  @NotNull
  public List<ShelvedChangeList> getShelvedChangeLists() {
    return getRecycled(false);
  }

  @NotNull
  private List<ShelvedChangeList> getRecycled(final boolean recycled) {
    return ContainerUtil.newUnmodifiableList(ContainerUtil.filter(mySchemeManager.getAllSchemes(), new Condition<ShelvedChangeList>() {
      @Override
      public boolean value(ShelvedChangeList list) {
        return recycled == list.isRecycled();
      }
    }));
  }

  public ShelvedChangeList shelveChanges(final Collection<Change> changes, final String commitMessage, final boolean rollback)
    throws IOException, VcsException {
    return shelveChanges(changes, commitMessage, rollback, false);
  }

  public ShelvedChangeList shelveChanges(final Collection<Change> changes,
                                         final String commitMessage,
                                         final boolean rollback,
                                         boolean markToBeDeleted)
    throws IOException, VcsException {
    final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    if (progressIndicator != null) {
      progressIndicator.setText(VcsBundle.message("shelve.changes.progress.title"));
    }
    File schemePatchDir = generateUniqueSchemePatchDir(commitMessage, true);
    final List<Change> textChanges = new ArrayList<>();
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

    final ShelvedChangeList changeList;
    try {
      File patchPath = getPatchFileInConfigDir(schemePatchDir);
      ProgressManager.checkCanceled();
      final List<FilePatch> patches =
        IdeaTextPatchBuilder.buildPatch(myProject, textChanges, myProject.getBaseDir().getPresentableUrl(), false);
      ProgressManager.checkCanceled();

      CommitContext commitContext = new CommitContext();
      baseRevisionsOfDvcsIntoContext(textChanges, commitContext);
      myFileProcessor.savePathFile(
        new CompoundShelfFileProcessor.ContentProvider() {
                                     @Override
                                     public void writeContentTo(@NotNull final Writer writer, @NotNull CommitContext commitContext)
                                       throws IOException {
                                       UnifiedDiffWriter.write(myProject, patches, writer, "\n", commitContext);
                                     }
                                   },
                                   patchPath, commitContext);

      changeList = new ShelvedChangeList(patchPath.toString(), commitMessage.replace('\n', ' '), binaryFiles);
      changeList.markToDelete(markToBeDeleted);
      changeList.setName(schemePatchDir.getName());
      ProgressManager.checkCanceled();
      mySchemeManager.addNewScheme(changeList, false);

      if (rollback) {
        final String operationName = UIUtil.removeMnemonic(RollbackChangesDialog.operationNameByChanges(myProject, changes));
        boolean modalContext = ApplicationManager.getApplication().isDispatchThread() && LaterInvocator.isInModalContext();
        if (progressIndicator != null) {
          progressIndicator.startNonCancelableSection();
        }
        new RollbackWorker(myProject, operationName, modalContext).
          doRollback(changes, true, null, VcsBundle.message("shelve.changes.action"));
      }
    }
    finally {
      notifyStateChanged();
    }

    return changeList;
  }

  @NotNull
  private static File getPatchFileInConfigDir(@NotNull File schemePatchDir) {
    return new File(schemePatchDir, DEFAULT_PATCH_NAME + "." + VcsConfiguration.PATCH);
  }

  private void baseRevisionsOfDvcsIntoContext(List<Change> textChanges, CommitContext commitContext) {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    if (vcsManager.dvcsUsedInProject() && VcsConfiguration.getInstance(myProject).INCLUDE_TEXT_INTO_SHELF) {
      final Set<Change> big = SelectFilesToAddTextsToPatchPanel.getBig(textChanges);
      final ArrayList<FilePath> toKeep = new ArrayList<>();
      for (Change change : textChanges) {
        if (change.getBeforeRevision() == null || change.getAfterRevision() == null) continue;
        if (big.contains(change)) continue;
        FilePath filePath = ChangesUtil.getFilePath(change);
        final AbstractVcs vcs = vcsManager.getVcsFor(filePath);
        if (vcs != null && VcsType.distributed.equals(vcs.getType())) {
          toKeep.add(filePath);
        }
      }
      commitContext.putUserData(BaseRevisionTextPatchEP.ourPutBaseRevisionTextKey, true);
      commitContext.putUserData(BaseRevisionTextPatchEP.ourBaseRevisionPaths, toKeep);
    }
  }

  public ShelvedChangeList importFilePatches(final String fileName, final List<FilePatch> patches, final PatchEP[] patchTransitExtensions)
    throws IOException {
    try {
      File schemePatchDir = generateUniqueSchemePatchDir(fileName, true);
      File patchPath = getPatchFileInConfigDir(schemePatchDir);
      myFileProcessor.savePathFile(
        new CompoundShelfFileProcessor.ContentProvider() {
          @Override
          public void writeContentTo(@NotNull final Writer writer, @NotNull CommitContext commitContext)
            throws IOException {
            UnifiedDiffWriter.write(myProject, patches, writer, "\n", patchTransitExtensions, commitContext);
          }
        },
        patchPath, new CommitContext());

      final ShelvedChangeList changeList =
        new ShelvedChangeList(patchPath.toString(), fileName.replace('\n', ' '), new SmartList<>());
      changeList.setName(schemePatchDir.getName());
      mySchemeManager.addNewScheme(changeList, false);
      return changeList;
    }
    finally {
      notifyStateChanged();
    }
  }

  public List<VirtualFile> gatherPatchFiles(final Collection<VirtualFile> files) {
    final List<VirtualFile> result = new ArrayList<>();

    final LinkedList<VirtualFile> filesQueue = new LinkedList<>(files);
    while (!filesQueue.isEmpty()) {
      ProgressManager.checkCanceled();
      final VirtualFile file = filesQueue.removeFirst();
      if (file.isDirectory()) {
        filesQueue.addAll(Arrays.asList(file.getChildren()));
        continue;
      }
      if (PatchFileType.NAME.equals(file.getFileType().getName())) {
        result.add(file);
      }
    }

    return result;
  }

  public List<ShelvedChangeList> importChangeLists(final Collection<VirtualFile> files,
                                                   final Consumer<VcsException> exceptionConsumer) {
    final List<ShelvedChangeList> result = new ArrayList<>(files.size());
    try {
      final FilesProgress filesProgress = new FilesProgress(files.size(), "Processing ");
      for (VirtualFile file : files) {
        filesProgress.updateIndicator(file);
        final String description = file.getNameWithoutExtension().replace('_', ' ');
        File schemeNameDir = generateUniqueSchemePatchDir(description, true);
        final File patchPath = getPatchFileInConfigDir(schemeNameDir);
        final ShelvedChangeList list = new ShelvedChangeList(patchPath.getPath(), description, new SmartList<>(),
                                                             file.getTimeStamp());
        list.setName(schemeNameDir.getName());
        try {
          final List<TextFilePatch> patchesList = loadPatches(myProject, file.getPath(), new CommitContext());
          if (!patchesList.isEmpty()) {
            FileUtil.copy(new File(file.getPath()), patchPath);
            // add only if ok to read patch
            mySchemeManager.addNewScheme(list, false);
            result.add(list);
          }
        }
        catch (IOException e) {
          exceptionConsumer.consume(new VcsException(e));
        }
        catch (PatchSyntaxException e) {
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
      File shelvedFile = new File(schemePatchDir, afterFile.getName());
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
  private File generateUniqueSchemePatchDir(@NotNull final String defaultName, boolean createResourceDirectory) {
    ignoreShelfDirectoryIfFirstShelf();
    String uniqueName = UniqueNameGenerator
      .generateUniqueName(shortenAndSanitize(defaultName), mySchemeManager.getAllSchemeNames());
    File dir = new File(myFileProcessor.getBaseDir(), uniqueName);
    if (createResourceDirectory && !dir.exists()) {
      //noinspection ResultOfMethodCallIgnored
      dir.mkdirs();
    }
    return dir;
  }

  private void ignoreShelfDirectoryIfFirstShelf() {
    File shelfDir = getShelfResourcesDirectory();
    //check that shelf directory wasn't exist before that to ignore it only once
    if (!shelfDir.exists()) {
      ChangeListManager.getInstance(myProject).addDirectoryToIgnoreImplicitly(shelfDir.getAbsolutePath());
    }
  }

  @NotNull
  // for create patch only
  public static File suggestPatchName(Project project, @NotNull final String commitMessage, final File file, String extension) {
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
  private static String shortenAndSanitize(@NotNull String commitMessage) {
    @NonNls String defaultPath = FileUtil.sanitizeFileName(commitMessage);
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
                                 @Nullable final List<ShelvedChange> changes,
                                 @Nullable final List<ShelvedBinaryFile> binaryFiles,
                                 @Nullable final LocalChangeList targetChangeList,
                                 boolean showSuccessNotification) {
    unshelveChangeList(changeList, changes, binaryFiles, targetChangeList, showSuccessNotification, false, false, null, null);
  }

  @CalledInAny
  public void unshelveChangeList(final ShelvedChangeList changeList,
                                 @Nullable final List<ShelvedChange> changes,
                                 @Nullable final List<ShelvedBinaryFile> binaryFiles,
                                 @Nullable final LocalChangeList targetChangeList,
                                 final boolean showSuccessNotification,
                                 final boolean systemOperation,
                                 final boolean reverse,
                                 final String leftConflictTitle,
                                 final String rightConflictTitle) {
    final List<FilePatch> remainingPatches = new ArrayList<>();

    final CommitContext commitContext = new CommitContext();
    final List<TextFilePatch> textFilePatches;
    try {
      textFilePatches = loadTextPatches(myProject, changeList, changes, remainingPatches, commitContext);
    }
    catch (IOException e) {
      LOG.info(e);
      PatchApplier.showError(myProject, "Cannot load patch(es): " + e.getMessage(), true);
      return;
    }
    catch (PatchSyntaxException e) {
      PatchApplier.showError(myProject, "Cannot load patch(es): " + e.getMessage(), true);
      LOG.info(e);
      return;
    }

    final List<FilePatch> patches = new ArrayList<>(textFilePatches);

    final List<ShelvedBinaryFile> remainingBinaries = new ArrayList<>();
    final List<ShelvedBinaryFile> binaryFilesToUnshelve = getBinaryFilesToUnshelve(changeList, binaryFiles, remainingBinaries);

    for (final ShelvedBinaryFile shelvedBinaryFile : binaryFilesToUnshelve) {
      patches.add(new ShelvedBinaryFilePatch(shelvedBinaryFile));
    }

    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        final BinaryPatchApplier binaryPatchApplier = new BinaryPatchApplier();
        final PatchApplier<ShelvedBinaryFilePatch> patchApplier =
          new PatchApplier<>(myProject, myProject.getBaseDir(),
                             patches, targetChangeList, binaryPatchApplier, commitContext, reverse, leftConflictTitle,
                             rightConflictTitle);
        patchApplier.setIsSystemOperation(systemOperation);
        patchApplier.execute(showSuccessNotification, systemOperation);
        if (isRemoveFilesFromShelf() || systemOperation) {
          remainingPatches.addAll(patchApplier.getRemainingPatches());
          if (remainingPatches.isEmpty() && remainingBinaries.isEmpty()) {
            recycleChangeList(changeList);
          }
          else {
            saveRemainingPatches(changeList, remainingPatches, remainingBinaries, commitContext);
          }
        }
      }
    }, ModalityState.defaultModalityState());
  }

  private static List<TextFilePatch> loadTextPatches(final Project project,
                                                     final ShelvedChangeList changeList,
                                                     final List<ShelvedChange> changes,
                                                     final List<FilePatch> remainingPatches,
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
    myRemoveFilesFromShelf = removeFilesFromShelf;
  }

  public boolean isRemoveFilesFromShelf() {
    return myRemoveFilesFromShelf;
  }

  private void cleanSystemUnshelvedOlderOneWeek() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.DAY_OF_MONTH, -7);
    cleanUnshelved(true, cal.getTimeInMillis());
  }

  public void cleanUnshelved(final boolean onlyMarkedToDelete, long timeBefore) {
    final Date limitDate = new Date(timeBefore);
    final List<ShelvedChangeList> toDelete = ContainerUtil.filter(mySchemeManager.getAllSchemes(), new Condition<ShelvedChangeList>() {
      @Override
      public boolean value(ShelvedChangeList list) {
        return (list.isRecycled()) && list.DATE.before(limitDate) && (!onlyMarkedToDelete || list.isMarkedToDelete());
      }
    });
    clearShelvedLists(toDelete);
  }

  private class BinaryPatchApplier implements CustomBinaryPatchApplier<ShelvedBinaryFilePatch> {
    private final List<FilePatch> myAppliedPatches;

    private BinaryPatchApplier() {
      myAppliedPatches = new ArrayList<>();
    }

    @Override
    @NotNull
    public ApplyPatchStatus apply(final List<Pair<VirtualFile, ApplyFilePatchBase<ShelvedBinaryFilePatch>>> patches) throws IOException {
      for (Pair<VirtualFile, ApplyFilePatchBase<ShelvedBinaryFilePatch>> patch : patches) {
        final ShelvedBinaryFilePatch shelvedPatch = patch.getSecond().getPatch();
        unshelveBinaryFile(shelvedPatch.getShelvedBinaryFile(), patch.getFirst());
        myAppliedPatches.add(shelvedPatch);
      }
      return ApplyPatchStatus.SUCCESS;
    }

    @Override
    @NotNull
    public List<FilePatch> getAppliedPatches() {
      return myAppliedPatches;
    }
  }

  private static List<ShelvedBinaryFile> getBinaryFilesToUnshelve(final ShelvedChangeList changeList,
                                                                  final List<ShelvedBinaryFile> binaryFiles,
                                                                  final List<ShelvedBinaryFile> remainingBinaries) {
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

  private void unshelveBinaryFile(final ShelvedBinaryFile file, @NotNull final VirtualFile patchTarget) throws IOException {
    final Ref<IOException> ex = new Ref<>();
    final Ref<VirtualFile> patchedFileRef = new Ref<>();
    final File shelvedFile = file.SHELVED_PATH == null ? null : new File(file.SHELVED_PATH);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        try {
          if (shelvedFile == null) {
            patchTarget.delete(this);
          }
          else {
            patchTarget.setBinaryContent(FileUtil.loadFileBytes(shelvedFile));
            patchedFileRef.set(patchTarget);
          }
        }
        catch (IOException e) {
          ex.set(e);
        }
      }
    });
    if (!ex.isNull()) {
      throw ex.get();
    }
  }

  private static boolean needUnshelve(final FilePatch patch, final List<ShelvedChange> changes) {
    for (ShelvedChange change : changes) {
      if (Comparing.equal(patch.getBeforeName(), change.getBeforePath())) {
        return true;
      }
    }
    return false;
  }

  private static void writePatchesToFile(final Project project,
                                         final String path,
                                         final List<FilePatch> remainingPatches,
                                         CommitContext commitContext) {
    try {
      OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(path), CharsetToolkit.UTF8_CHARSET);
      try {
        UnifiedDiffWriter.write(project, remainingPatches, writer, "\n", commitContext);
      }
      finally {
        writer.close();
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public void saveRemainingPatches(final ShelvedChangeList changeList, final List<FilePatch> remainingPatches,
                                   final List<ShelvedBinaryFile> remainingBinaries, CommitContext commitContext) {
    ShelvedChangeList listCopy;
    try {
      listCopy = !changeList.isRecycled() ? createRecycledChangelist(changeList) : null;
    }
    catch (IOException e) {
      // do not delete if cannot recycle
      return;
    }
    writePatchesToFile(myProject, changeList.PATH, remainingPatches, commitContext);

    changeList.getBinaryFiles().retainAll(remainingBinaries);
    changeList.clearLoadedChanges();
    if (listCopy != null) {
      recycleChangeList(listCopy, changeList);
      // all newly create ShelvedChangeList have to be added to SchemesManger as new scheme
      mySchemeManager.addNewScheme(listCopy, false);
    }
    notifyStateChanged();
  }

  @Nullable
  private ShelvedChangeList createRecycledChangelist(ShelvedChangeList changeList) throws IOException {
    final File newPatchDir = generateUniqueSchemePatchDir(changeList.DESCRIPTION, true);
    final File newPath = getPatchFileInConfigDir(newPatchDir);
    FileUtil.copy(new File(changeList.PATH), newPath);
    final ShelvedChangeList listCopy = new ShelvedChangeList(newPath.getAbsolutePath(), changeList.DESCRIPTION,
                                                             new ArrayList<>(changeList.getBinaryFiles()));
    listCopy.markToDelete(changeList.isMarkedToDelete());
    listCopy.setName(newPatchDir.getName());
    return listCopy;
  }

  public void restoreList(@NotNull final ShelvedChangeList changeList) {
    ShelvedChangeList list = mySchemeManager.findSchemeByName(changeList.getName());
    if (list != null) {
      list.setRecycled(false);
      list.updateDate();
    }
    notifyStateChanged();
  }

  @NotNull
  public List<ShelvedChangeList> getRecycledShelvedChangeLists() {
    return getRecycled(true);
  }

  public void clearRecycled() {
    clearShelvedLists(getRecycledShelvedChangeLists());
  }

  private void clearShelvedLists(@NotNull List<ShelvedChangeList> shelvedLists) {
    if (shelvedLists.isEmpty()) return;
    for (ShelvedChangeList list : shelvedLists) {
      deleteListImpl(list);
      mySchemeManager.removeScheme(list);
    }
    notifyStateChanged();
  }

  private void recycleChangeList(@NotNull final ShelvedChangeList listCopy, @Nullable final ShelvedChangeList newList) {
    if (newList != null) {
      for (Iterator<ShelvedBinaryFile> shelvedChangeListIterator = listCopy.getBinaryFiles().iterator();
           shelvedChangeListIterator.hasNext(); ) {
        final ShelvedBinaryFile binaryFile = shelvedChangeListIterator.next();
        for (ShelvedBinaryFile newBinary : newList.getBinaryFiles()) {
          if (Comparing.equal(newBinary.BEFORE_PATH, binaryFile.BEFORE_PATH)
              && Comparing.equal(newBinary.AFTER_PATH, binaryFile.AFTER_PATH)) {
            shelvedChangeListIterator.remove();
          }
        }
      }
      for (Iterator<ShelvedChange> iterator = listCopy.getChanges(myProject).iterator(); iterator.hasNext(); ) {
        final ShelvedChange change = iterator.next();
        for (ShelvedChange newChange : newList.getChanges(myProject)) {
          if (Comparing.equal(change.getBeforePath(), newChange.getBeforePath()) &&
              Comparing.equal(change.getAfterPath(), newChange.getAfterPath())) {
            iterator.remove();
          }
        }
      }

      // needed only if partial unshelve
      try {
        final CommitContext commitContext = new CommitContext();
        final List<FilePatch> patches = new ArrayList<>();
        for (ShelvedChange change : listCopy.getChanges(myProject)) {
          patches.add(change.loadFilePatch(myProject, commitContext));
        }
        writePatchesToFile(myProject, listCopy.PATH, patches, commitContext);
      }
      catch (IOException e) {
        LOG.info(e);
        // left file as is
      }
      catch (PatchSyntaxException e) {
        LOG.info(e);
        // left file as is
      }
    }

    if (!listCopy.getBinaryFiles().isEmpty() || !listCopy.getChanges(myProject).isEmpty()) {
      listCopy.setRecycled(true);
      listCopy.updateDate();
      notifyStateChanged();
    }
  }

  public void recycleChangeList(@NotNull final ShelvedChangeList changeList) {
    recycleChangeList(changeList, null);
    notifyStateChanged();
  }

  public void deleteChangeList(@NotNull final ShelvedChangeList changeList) {
    deleteListImpl(changeList);
    mySchemeManager.removeScheme(changeList);
    notifyStateChanged();
  }

  private void deleteListImpl(@NotNull final ShelvedChangeList changeList) {
    FileUtil.delete(new File(myFileProcessor.getBaseDir(), changeList.getName()));
    //backward compatibility deletion: if we didn't preform resource migration
    FileUtil.delete(new File(changeList.PATH));
    for (ShelvedBinaryFile binaryFile : changeList.getBinaryFiles()) {
      final String path = binaryFile.SHELVED_PATH;
      if (path != null) {
        FileUtil.delete(new File(path));
      }
    }
  }

  public void renameChangeList(final ShelvedChangeList changeList, final String newName) {
    changeList.DESCRIPTION = newName;
    notifyStateChanged();
  }

  @NotNull
  public static List<TextFilePatch> loadPatches(Project project,
                                                final String patchPath,
                                                CommitContext commitContext) throws IOException, PatchSyntaxException {
    return loadPatches(project, patchPath, commitContext, true);
  }

  @NotNull
  static List<? extends FilePatch> loadPatchesWithoutContent(Project project,
                                                             final String patchPath,
                                                             CommitContext commitContext) throws IOException, PatchSyntaxException {
    return loadPatches(project, patchPath, commitContext, false);
  }

  private static List<TextFilePatch> loadPatches(Project project,
                                                 final String patchPath,
                                                 CommitContext commitContext,
                                                 boolean loadContent) throws IOException, PatchSyntaxException {
    char[] text = FileUtil.loadFileText(new File(patchPath), CharsetToolkit.UTF8);
    PatchReader reader = new PatchReader(new CharArrayCharSequence(text), loadContent);
    final List<TextFilePatch> textFilePatches = reader.readAllPatches();
    final TransparentlyFailedValueI<Map<String, Map<String, CharSequence>>, PatchSyntaxException> additionalInfo = reader.getAdditionalInfo(
      null);
    ApplyPatchDefaultExecutor.applyAdditionalInfoBefore(project, additionalInfo, commitContext);
    return textFilePatches;
  }

  public boolean isShowRecycled() {
    return myShowRecycled;
  }

  public void setShowRecycled(final boolean showRecycled) {
    myShowRecycled = showRecycled;
    notifyStateChanged();
  }
}
