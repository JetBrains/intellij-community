/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 22.11.2006
 * Time: 19:59:36
 */
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.lifecycle.PeriodicalTasksCloser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.*;
import com.intellij.openapi.diff.impl.patch.apply.ApplyFilePatchBase;
import com.intellij.openapi.diff.impl.patch.formove.CustomBinaryPatchApplier;
import com.intellij.openapi.diff.impl.patch.formove.PatchApplier;
import com.intellij.openapi.progress.AsynchronousExecution;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
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
import com.intellij.util.SmartList;
import com.intellij.util.continuation.*;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import com.intellij.util.text.CharArrayCharSequence;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.FilesProgress;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.io.*;
import java.util.*;

import static com.intellij.openapi.vcs.changes.shelf.CompoundShelfFileProcessor.SHELF_DIR_NAME;

public class ShelveChangesManager extends AbstractProjectComponent implements JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager");

  public static ShelveChangesManager getInstance(Project project) {
    return PeriodicalTasksCloser.getInstance().safeGetComponent(project, ShelveChangesManager.class);
  }

  private final MessageBus myBus;
  private final List<ShelvedChangeList> myShelvedChangeLists = new ArrayList<ShelvedChangeList>();
  private final List<ShelvedChangeList> myRecycledShelvedChangeLists = new ArrayList<ShelvedChangeList>();

  @NonNls private static final String ATTRIBUTE_SHOW_RECYCLED = "show_recycled";
  private final CompoundShelfFileProcessor myFileProcessor;

  public static final Topic<ChangeListener> SHELF_TOPIC = new Topic<ChangeListener>("shelf updates", ChangeListener.class);
  private boolean myShowRecycled;

  public ShelveChangesManager(final Project project, final MessageBus bus) {
    super(project);
    myBus = bus;
    if (project.isDefault()) {
      myFileProcessor = new CompoundShelfFileProcessor(null, PathManager.getConfigPath() + File.separator + SHELF_DIR_NAME);
    }
    else {
      if (project instanceof ProjectEx && ((ProjectEx)project).getStateStore().getStorageScheme() == StorageScheme.DIRECTORY_BASED) {
        VirtualFile dir = project.getBaseDir();
        String shelfBaseDirPath = dir == null ? "" : dir.getPath() + File.separator + Project.DIRECTORY_STORE_FOLDER;
        myFileProcessor = new CompoundShelfFileProcessor(shelfBaseDirPath);
      }
      else {
        myFileProcessor = new CompoundShelfFileProcessor();
      }
    }
  }

  @Override
  @NonNls
  @NotNull
  public String getComponentName() {
    return "ShelveChangesManager";
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    //noinspection unchecked

    final String showRecycled = element.getAttributeValue(ATTRIBUTE_SHOW_RECYCLED);
    if (showRecycled != null) {
      myShowRecycled = Boolean.parseBoolean(showRecycled);
    } else {
      myShowRecycled = true;
    }

    readExternal(element, myShelvedChangeLists, myRecycledShelvedChangeLists);


  }

  public static void readExternal(final Element element, final List<ShelvedChangeList> changes, final List<ShelvedChangeList> recycled) throws InvalidDataException {
    changes.addAll(ShelvedChangeList.readChanges(element, false, true));

    recycled.addAll(ShelvedChangeList.readChanges(element, true, true));
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    element.setAttribute(ATTRIBUTE_SHOW_RECYCLED, Boolean.toString(myShowRecycled));
    ShelvedChangeList.writeChanges(myShelvedChangeLists, myRecycledShelvedChangeLists, element);

  }

  public List<ShelvedChangeList> getShelvedChangeLists() {
    return Collections.unmodifiableList(myShelvedChangeLists);
  }

  public ShelvedChangeList shelveChanges(final Collection<Change> changes, final String commitMessage, final boolean rollback) throws IOException, VcsException {
    final List<Change> textChanges = new ArrayList<Change>();
    final List<ShelvedBinaryFile> binaryFiles = new ArrayList<ShelvedBinaryFile>();
    for(Change change: changes) {
      if (ChangesUtil.getFilePath(change).isDirectory()) {
        continue;
      }
      if (change.getBeforeRevision() instanceof BinaryContentRevision || change.getAfterRevision() instanceof BinaryContentRevision) {
        binaryFiles.add(shelveBinaryFile(change));
      }
      else {
        textChanges.add(change);
      }
    }

    final ShelvedChangeList changeList;
    try {
      File patchPath = getPatchPath(commitMessage);
      ProgressManager.checkCanceled();
      final List<FilePatch> patches = IdeaTextPatchBuilder.buildPatch(myProject, textChanges, myProject.getBaseDir().getPresentableUrl(), false);
      ProgressManager.checkCanceled();

      CommitContext commitContext = new CommitContext();
      baseRevisionsOfDvcsIntoContext(textChanges, commitContext);
      myFileProcessor.savePathFile(
        new CompoundShelfFileProcessor.ContentProvider(){
            @Override
            public void writeContentTo(final Writer writer, CommitContext commitContext) throws IOException {
              UnifiedDiffWriter.write(myProject, patches, writer, "\n", commitContext);
            }
          },
          patchPath, commitContext);

      changeList = new ShelvedChangeList(patchPath.toString(), commitMessage.replace('\n', ' '), binaryFiles);
      myShelvedChangeLists.add(changeList);
      ProgressManager.checkCanceled();

      if (rollback) {
        final String operationName = UIUtil.removeMnemonic(RollbackChangesDialog.operationNameByChanges(myProject, changes));
        boolean modalContext = ApplicationManager.getApplication().isDispatchThread() && LaterInvocator.isInModalContext();
        new RollbackWorker(myProject, operationName, modalContext).
          doRollback(changes, true, null, VcsBundle.message("shelve.changes.action"));
      }
    }
    finally {
      notifyStateChanged();
    }

    return changeList;
  }

  private void baseRevisionsOfDvcsIntoContext(List<Change> textChanges, CommitContext commitContext) {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    if (vcsManager.dvcsUsedInProject() && VcsConfiguration.getInstance(myProject).INCLUDE_TEXT_INTO_SHELF) {
      final Set<Change> big = SelectFilesToAddTextsToPatchPanel.getBig(textChanges);
      final ArrayList<FilePath> toKeep = new ArrayList<FilePath>();
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

  public ShelvedChangeList importFilePatches(final String fileName, final List<FilePatch> patches, final PatchEP[] patchTransitExtensions) throws IOException {
    try {
      final File patchPath = getPatchPath(fileName);
      myFileProcessor.savePathFile(
        new CompoundShelfFileProcessor.ContentProvider(){
            @Override
            public void writeContentTo(final Writer writer, CommitContext commitContext) throws IOException {
              UnifiedDiffWriter.write(myProject, patches, writer, "\n", patchTransitExtensions, commitContext);
            }
          },
          patchPath, new CommitContext());

      final ShelvedChangeList changeList = new ShelvedChangeList(patchPath.toString(), fileName.replace('\n', ' '), new SmartList<ShelvedBinaryFile>());
      myShelvedChangeLists.add(changeList);
      return changeList;
    } finally {
      notifyStateChanged();
    }
  }

  public List<VirtualFile> gatherPatchFiles(final Collection<VirtualFile> files) {
    final List<VirtualFile> result = new ArrayList<VirtualFile>();

    final LinkedList<VirtualFile> filesQueue = new LinkedList<VirtualFile>(files);
    while (! filesQueue.isEmpty()) {
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
    final List<ShelvedChangeList> result = new ArrayList<ShelvedChangeList>(files.size());
    try {
      final FilesProgress filesProgress = new FilesProgress(files.size(), "Processing ");
      for (VirtualFile file : files) {
        filesProgress.updateIndicator(file);
        final String description = file.getNameWithoutExtension().replace('_', ' ');
        final File patchPath = getPatchPath(description);
        final ShelvedChangeList list = new ShelvedChangeList(patchPath.getPath(), description, new SmartList<ShelvedBinaryFile>(),
                                                             file.getTimeStamp());
        try {
          final List<TextFilePatch> patchesList = loadPatches(myProject, file.getPath(), new CommitContext());
          if (! patchesList.isEmpty()) {
            FileUtil.copy(new File(file.getPath()), patchPath);
            // add only if ok to read patch
            myShelvedChangeLists.add(list);
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
    } finally {
      notifyStateChanged();
    }
    return result;
  }

  private ShelvedBinaryFile shelveBinaryFile(final Change change) throws IOException {
    final ContentRevision beforeRevision = change.getBeforeRevision();
    final ContentRevision afterRevision = change.getAfterRevision();
    File beforeFile = beforeRevision == null ? null : beforeRevision.getFile().getIOFile();
    File afterFile = afterRevision == null ? null : afterRevision.getFile().getIOFile();
    String shelvedPath = null;
    if (afterFile != null) {
      String shelvedName = FileUtil.getNameWithoutExtension(afterFile.getName());
      String shelvedExt = FileUtilRt.getExtension(afterFile.getName());
      File shelvedFile = FileUtil.findSequentNonexistentFile(myFileProcessor.getBaseIODir(), shelvedName, shelvedExt);

      myFileProcessor.saveFile(afterRevision.getFile().getIOFile(), shelvedFile);

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

  private File getPatchPath(@NonNls final String commitMessage) {
    File file = myFileProcessor.getBaseIODir();
    if (!file.exists()) {
      //noinspection ResultOfMethodCallIgnored
      file.mkdirs();
    }

    return suggestPatchName(myProject, commitMessage.length() > PatchNameChecker.MAX ? commitMessage.substring(0, PatchNameChecker.MAX) :
                            commitMessage, file, VcsConfiguration.PATCH);
  }

  public static File suggestPatchName(Project project, final String commitMessage, final File file, String extension) {
    @NonNls String defaultPath = PathUtil.suggestFileName(commitMessage);
    if (defaultPath.isEmpty()) {
      defaultPath = "unnamed";
    }
    if (defaultPath.length() > PatchNameChecker.MAX - 10) {
      defaultPath = defaultPath.substring(0, PatchNameChecker.MAX - 10);
    }
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

  public void unshelveChangeList(final ShelvedChangeList changeList, @Nullable final List<ShelvedChange> changes,
                                           @Nullable final List<ShelvedBinaryFile> binaryFiles, final LocalChangeList targetChangeList) {
    unshelveChangeList(changeList, changes, binaryFiles, targetChangeList, true);
  }

  @AsynchronousExecution
  public void unshelveChangeList(final ShelvedChangeList changeList,
                                 @Nullable final List<ShelvedChange> changes,
                                 @Nullable final List<ShelvedBinaryFile> binaryFiles,
                                 @Nullable final LocalChangeList targetChangeList,
                                 boolean showSuccessNotification) {
    final Continuation continuation = Continuation.createForCurrentProgress(myProject, true, "Unshelve changes");
    final GatheringContinuationContext initContext = new GatheringContinuationContext();
    scheduleUnshelveChangeList(changeList, changes, binaryFiles, targetChangeList, showSuccessNotification, initContext, false,
                               false, null, null);
    continuation.run(initContext.getList());
  }

  @AsynchronousExecution
  public void scheduleUnshelveChangeList(final ShelvedChangeList changeList,
                                         @Nullable final List<ShelvedChange> changes,
                                         @Nullable final List<ShelvedBinaryFile> binaryFiles,
                                         @Nullable final LocalChangeList targetChangeList,
                                         final boolean showSuccessNotification,
                                         final ContinuationContext context,
                                         final boolean systemOperation,
                                         final boolean reverse,
                                         final String leftConflictTitle,
                                         final String rightConflictTitle) {
    context.next(new TaskDescriptor("", Where.AWT) {
      @Override
      public void run(ContinuationContext contextInner) {
        final List<FilePatch> remainingPatches = new ArrayList<FilePatch>();

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

        final List<FilePatch> patches = new ArrayList<FilePatch>(textFilePatches);

        final List<ShelvedBinaryFile> remainingBinaries = new ArrayList<ShelvedBinaryFile>();
        final List<ShelvedBinaryFile> binaryFilesToUnshelve = getBinaryFilesToUnshelve(changeList, binaryFiles, remainingBinaries);

        for (final ShelvedBinaryFile shelvedBinaryFile : binaryFilesToUnshelve) {
          patches.add(new ShelvedBinaryFilePatch(shelvedBinaryFile));
        }

        final BinaryPatchApplier binaryPatchApplier = new BinaryPatchApplier();
        final PatchApplier<ShelvedBinaryFilePatch> patchApplier = new PatchApplier<ShelvedBinaryFilePatch>(myProject, myProject.getBaseDir(),
            patches, targetChangeList, binaryPatchApplier, commitContext, reverse, leftConflictTitle, rightConflictTitle);
        patchApplier.setIsSystemOperation(systemOperation);

        // after patch applier part
        contextInner.next(new TaskDescriptor("", Where.AWT) {
          @Override
          public void run(ContinuationContext context) {
            remainingPatches.addAll(patchApplier.getRemainingPatches());

            if (remainingPatches.isEmpty() && remainingBinaries.isEmpty()) {
              recycleChangeList(changeList);
            }
            else {
              saveRemainingPatches(changeList, remainingPatches, remainingBinaries, commitContext);
            }
          }
        });

        patchApplier.scheduleSelf(showSuccessNotification, contextInner, systemOperation);
      }
    });
  }

  private static List<TextFilePatch> loadTextPatches(final Project project, final ShelvedChangeList changeList, final List<ShelvedChange> changes, final List<FilePatch> remainingPatches, final CommitContext commitContext)
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

  private class BinaryPatchApplier implements CustomBinaryPatchApplier<ShelvedBinaryFilePatch> {
    private final List<FilePatch> myAppliedPatches;

    private BinaryPatchApplier() {
      myAppliedPatches = new ArrayList<FilePatch>();
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
      return new ArrayList<ShelvedBinaryFile>(changeList.getBinaryFiles());
    }
    ArrayList<ShelvedBinaryFile> result = new ArrayList<ShelvedBinaryFile>();
    for(ShelvedBinaryFile file: changeList.getBinaryFiles()) {
      if (binaryFiles.contains(file)) {
        result.add(file);
      } else {
        remainingBinaries.add(file);
      }
    }
    return result;
  }

  private void unshelveBinaryFile(final ShelvedBinaryFile file, @NotNull final VirtualFile patchTarget) throws IOException {
    final Ref<IOException> ex = new Ref<IOException>();
    final Ref<VirtualFile> patchedFileRef = new Ref<VirtualFile>();
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
    for(ShelvedChange change: changes) {
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

  void saveRemainingPatches(final ShelvedChangeList changeList, final List<FilePatch> remainingPatches,
                            final List<ShelvedBinaryFile> remainingBinaries, CommitContext commitContext) {
    final File newPath = getPatchPath(changeList.DESCRIPTION);
    try {
      FileUtil.copy(new File(changeList.PATH), newPath);
    }
    catch (IOException e) {
      // do not delete if cannot recycle
      return;
    }
    final ShelvedChangeList listCopy = new ShelvedChangeList(newPath.getAbsolutePath(), changeList.DESCRIPTION,
                                                             new ArrayList<ShelvedBinaryFile>(changeList.getBinaryFiles()));
    listCopy.DATE = changeList.DATE == null ? null : new Date(changeList.DATE.getTime());

    writePatchesToFile(myProject, changeList.PATH, remainingPatches, commitContext);

    changeList.getBinaryFiles().retainAll(remainingBinaries);
    changeList.clearLoadedChanges();
    recycleChangeList(listCopy, changeList);
    notifyStateChanged();
  }

  public void restoreList(final ShelvedChangeList changeList) {
    myShelvedChangeLists.add(changeList);
    myRecycledShelvedChangeLists.remove(changeList);
    changeList.setRecycled(false);
    notifyStateChanged();
  }

  public List<ShelvedChangeList> getRecycledShelvedChangeLists() {
    return myRecycledShelvedChangeLists;
  }

  public void clearRecycled() {
    for (ShelvedChangeList list : myRecycledShelvedChangeLists) {
      deleteListImpl(list);
    }
    myRecycledShelvedChangeLists.clear();
    notifyStateChanged();
  }

  private void recycleChangeList(final ShelvedChangeList listCopy, final ShelvedChangeList newList) {
    if (newList != null) {
      for (Iterator<ShelvedBinaryFile> shelvedChangeListIterator = listCopy.getBinaryFiles().iterator();
           shelvedChangeListIterator.hasNext();) {
        final ShelvedBinaryFile binaryFile = shelvedChangeListIterator.next();
        for (ShelvedBinaryFile newBinary : newList.getBinaryFiles()) {
          if (Comparing.equal(newBinary.BEFORE_PATH, binaryFile.BEFORE_PATH)
              && Comparing.equal(newBinary.AFTER_PATH, binaryFile.AFTER_PATH)) {
            shelvedChangeListIterator.remove();
          }
        }
      }
      for (Iterator<ShelvedChange> iterator = listCopy.getChanges(myProject).iterator(); iterator.hasNext();) {
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
        final List<FilePatch> patches = new ArrayList<FilePatch>();
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

    if (! listCopy.getBinaryFiles().isEmpty() || ! listCopy.getChanges(myProject).isEmpty()) {
      listCopy.setRecycled(true);
      myRecycledShelvedChangeLists.add(listCopy);
      notifyStateChanged();
    }
  }

  private void recycleChangeList(final ShelvedChangeList changeList) {
    recycleChangeList(changeList, null);
    myShelvedChangeLists.remove(changeList);
    notifyStateChanged();
  }

  public void deleteChangeList(final ShelvedChangeList changeList) {
    deleteListImpl(changeList);
    if (! changeList.isRecycled()) {
      myShelvedChangeLists.remove(changeList);
    } else {
      myRecycledShelvedChangeLists.remove(changeList);
    }
    notifyStateChanged();
  }

  private void deleteListImpl(final ShelvedChangeList changeList) {
    File file = new File(changeList.PATH);
    myFileProcessor.delete(file.getName());

    for(ShelvedBinaryFile binaryFile: changeList.getBinaryFiles()) {
      final String path = binaryFile.SHELVED_PATH;
      if (path != null) {
        File binFile = new File(path);
        myFileProcessor.delete(binFile.getName());
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

  public static class ShelvedBinaryFilePatch extends FilePatch {
    private final ShelvedBinaryFile myShelvedBinaryFile;

    public ShelvedBinaryFilePatch(final ShelvedBinaryFile shelvedBinaryFile) {
      myShelvedBinaryFile = shelvedBinaryFile;
      setBeforeName(myShelvedBinaryFile.BEFORE_PATH);
      setAfterName(myShelvedBinaryFile.AFTER_PATH);
    }

    @Override
    public String getBeforeFileName() {
      String[] pathNameComponents = myShelvedBinaryFile.BEFORE_PATH.replace(File.separatorChar, '/').split("/");
      return pathNameComponents [pathNameComponents.length-1];
    }

    @Override
    public String getAfterFileName() {
      String[] pathNameComponents = myShelvedBinaryFile.AFTER_PATH.replace(File.separatorChar, '/').split("/");
      return pathNameComponents [pathNameComponents.length-1];
    }

    @Override
    public boolean isNewFile() {
      return myShelvedBinaryFile.BEFORE_PATH == null;
    }
    @Override
    public boolean isDeletedFile() {
      return myShelvedBinaryFile.AFTER_PATH == null;
    }

    public ShelvedBinaryFile getShelvedBinaryFile() {
      return myShelvedBinaryFile;
    }
  }

  public boolean isShowRecycled() {
    return myShowRecycled;
  }

  public void setShowRecycled(final boolean showRecycled) {
    myShowRecycled = showRecycled;
    notifyStateChanged();
  }
}
