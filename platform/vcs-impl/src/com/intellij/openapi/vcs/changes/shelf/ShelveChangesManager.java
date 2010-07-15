/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.*;
import com.intellij.openapi.diff.impl.patch.apply.ApplyFilePatchBase;
import com.intellij.openapi.diff.impl.patch.formove.CustomBinaryPatchApplier;
import com.intellij.openapi.diff.impl.patch.formove.PatchApplier;
import com.intellij.openapi.options.StreamProvider;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.patch.PatchNameChecker;
import com.intellij.openapi.vcs.changes.ui.RollbackWorker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import com.intellij.util.text.CharArrayCharSequence;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.io.*;
import java.util.*;

public class ShelveChangesManager implements ProjectComponent, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager");

  @NonNls private static final String PATCH_EXTENSION = "patch";

  public static ShelveChangesManager getInstance(Project project) {
    return project.getComponent(ShelveChangesManager.class);
  }

  private final Project myProject;
  private final MessageBus myBus;
  private final List<ShelvedChangeList> myShelvedChangeLists = new ArrayList<ShelvedChangeList>();
  private final List<ShelvedChangeList> myRecycledShelvedChangeLists = new ArrayList<ShelvedChangeList>();

  @NonNls private static final String ATTRIBUTE_SHOW_RECYCLED = "show_recycled";
  private final CompoundShelfFileProcesor myFileProcessor;

  public static final Topic<ChangeListener> SHELF_TOPIC = new Topic<ChangeListener>("shelf updates", ChangeListener.class);
  private boolean myShowRecycled;

  public ShelveChangesManager(final Project project, final MessageBus bus) {
    myProject = project;
    myBus = bus;
    if (!project.isDefault()) {
      myFileProcessor = new CompoundShelfFileProcesor("shelf");
    }
    else {
      myFileProcessor = new CompoundShelfFileProcesor(new StreamProvider[]{}, PathManager.getConfigPath() + File.separator + "shelf");
    }
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "ShelveChangesManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

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

  private static void readList(final List<Element> children, final List<ShelvedChangeList> sink) throws InvalidDataException {
    for(Element child: children) {
      ShelvedChangeList data = new ShelvedChangeList();
      data.readExternal(child);
      if (new File(data.PATH).exists()) {
        sink.add(data);
      }
    }
  }

  public static void readExternal(final Element element, final List<ShelvedChangeList> changes, final List<ShelvedChangeList> recycled) throws InvalidDataException {
    changes.addAll(ShelvedChangeList.readChanges(element, false, true));

    recycled.addAll(ShelvedChangeList.readChanges(element, true, true));
  }

  public void writeExternal(Element element) throws WriteExternalException {
    element.setAttribute(ATTRIBUTE_SHOW_RECYCLED, Boolean.toString(myShowRecycled));
    ShelvedChangeList.writeChanges(myShelvedChangeLists, myRecycledShelvedChangeLists, element);

  }

  public List<ShelvedChangeList> getShelvedChangeLists() {
    return Collections.unmodifiableList(myShelvedChangeLists);
  }

  /*public ShelvedChangeList shelveChanges(final Collection<Change> changes, final String commitMessage, final Runnable callAfterReverted) throws IOException, VcsException {

  }*/

  public ShelvedChangeList shelveChanges(final Collection<Change> changes, final String commitMessage) throws IOException, VcsException {
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

    final ProgressIndicator ind = ProgressManager.getInstance().getProgressIndicator();
    final ShelvedChangeList changeList;
    try {
      File patchPath = getPatchPath(commitMessage);
      if (ind != null && ind.isCanceled()) {
        throw new ProcessCanceledException();
      }
      final List<FilePatch> patches = IdeaTextPatchBuilder.buildPatch(myProject, textChanges, myProject.getBaseDir().getPresentableUrl(), false);
      if (ind != null && ind.isCanceled()) {
        throw new ProcessCanceledException();
      }

      myFileProcessor.savePathFile(
        new CompoundShelfFileProcesor.ContentProvider(){
            public void writeContentTo(final Writer writer) throws IOException {
              UnifiedDiffWriter.write(patches, writer, "\n");
            }
          },
          patchPath);

      changeList = new ShelvedChangeList(patchPath.toString(), commitMessage.replace('\n', ' '), binaryFiles);
      myShelvedChangeLists.add(changeList);
      if (ind != null && ind.isCanceled()) {
        throw new ProcessCanceledException();
      }

      new RollbackWorker(myProject, false).doRollback(changes, true, null, VcsBundle.message("shelve.changes.action"));
    }
    finally {
      notifyStateChanged();
    }

    return changeList;
  }

  private ShelvedBinaryFile shelveBinaryFile(final Change change) throws IOException {
    final ContentRevision beforeRevision = change.getBeforeRevision();
    final ContentRevision afterRevision = change.getAfterRevision();
    File beforeFile = beforeRevision == null ? null : beforeRevision.getFile().getIOFile();
    File afterFile = afterRevision == null ? null : afterRevision.getFile().getIOFile();
    String shelvedPath = null;
    if (afterFile != null) {
      String shelvedName = FileUtil.getNameWithoutExtension(afterFile.getName());
      String shelvedExt = FileUtil.getExtension(afterFile.getName());
      File shelvedFile = FileUtil.findSequentNonexistentFile(myFileProcessor.getBaseIODir(), shelvedName, shelvedExt);

      myFileProcessor.saveFile(afterRevision.getFile().getIOFile(), shelvedFile);

      shelvedPath = shelvedFile.getPath();
    }
    String beforePath = ChangesUtil.getProjectRelativePath(myProject, beforeFile);
    String afterPath = ChangesUtil.getProjectRelativePath(myProject, afterFile);
    return new ShelvedBinaryFile(beforePath, afterPath, shelvedPath);
  }

  private void notifyStateChanged() {
    myBus.syncPublisher(SHELF_TOPIC).stateChanged(new ChangeEvent(this));
  }

  private File getPatchPath(@NonNls final String commitMessage) {
    File file = myFileProcessor.getBaseIODir();
    if (!file.exists()) {
      file.mkdirs();
    }

    return suggestPatchName(commitMessage.length() > PatchNameChecker.MAX ? (commitMessage.substring(0, PatchNameChecker.MAX)) :
                            commitMessage, file);
  }

  public static File suggestPatchName(final String commitMessage, final File file) {
    @NonNls String defaultPath = PathUtil.suggestFileName(commitMessage);
    if (defaultPath.length() == 0) {
      defaultPath = "unnamed";
    }
    return FileUtil.findSequentNonexistentFile(file, defaultPath, PATCH_EXTENSION);
  }

  public void unshelveChangeList(final ShelvedChangeList changeList, @Nullable final List<ShelvedChange> changes,
                                           @Nullable final List<ShelvedBinaryFile> binaryFiles, final LocalChangeList targetChangeList) {
    unshelveChangeList(changeList, changes, binaryFiles, targetChangeList, true);
  }

  public void unshelveChangeList(final ShelvedChangeList changeList,
                                 @Nullable final List<ShelvedChange> changes,
                                 @Nullable final List<ShelvedBinaryFile> binaryFiles,
                                 final LocalChangeList targetChangeList,
                                 boolean showSuccessNotification) {
    List<FilePatch> remainingPatches = new ArrayList<FilePatch>();

    final List<TextFilePatch> textFilePatches;
    try {
      textFilePatches = loadTextPatches(changeList, changes, remainingPatches);
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

    final BinaryPatchApplier binaryPatchApplier = new BinaryPatchApplier(binaryFilesToUnshelve.size());
    final PatchApplier<ShelvedBinaryFilePatch> patchApplier = new PatchApplier<ShelvedBinaryFilePatch>(myProject, myProject.getBaseDir(), patches, targetChangeList, binaryPatchApplier);
    patchApplier.execute(showSuccessNotification);
    remainingPatches.addAll(patchApplier.getRemainingPatches());

    if ((remainingPatches.size() == 0) && remainingBinaries.isEmpty()) {
      recycleChangeList(changeList);
    }
    else {
      saveRemainingPatches(changeList, remainingPatches, remainingBinaries);
    }
  }

  private static List<TextFilePatch> loadTextPatches(final ShelvedChangeList changeList, final List<ShelvedChange> changes, final List<FilePatch> remainingPatches)
      throws IOException, PatchSyntaxException {
    final List<TextFilePatch> textFilePatches;
    textFilePatches = loadPatches(changeList.PATH);

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

    private BinaryPatchApplier(final int binaryCount) {
      myAppliedPatches = new ArrayList<FilePatch>();
    }

    @NotNull
    public ApplyPatchStatus apply(final List<Pair<VirtualFile, ApplyFilePatchBase<ShelvedBinaryFilePatch>>> patches) throws IOException {
      for (Pair<VirtualFile, ApplyFilePatchBase<ShelvedBinaryFilePatch>> patch : patches) {
        final ShelvedBinaryFilePatch shelvedPatch = patch.getSecond().getPatch();
        unshelveBinaryFile(shelvedPatch.getShelvedBinaryFile(), patch.getFirst());
        myAppliedPatches.add(shelvedPatch);
      }
      return ApplyPatchStatus.SUCCESS;
    }

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

  @Nullable
  private FilePath unshelveBinaryFile(final ShelvedBinaryFile file, @NotNull final VirtualFile patchTarget) throws IOException {
    final Ref<FilePath> result = new Ref<FilePath>();
    final Ref<IOException> ex = new Ref<IOException>();
    final Ref<VirtualFile> patchedFileRef = new Ref<VirtualFile>();
    final File shelvedFile = file.SHELVED_PATH == null ? null : new File(file.SHELVED_PATH);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          result.set(new FilePathImpl(patchTarget));
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
    return result.get();
  }

  private static boolean needUnshelve(final FilePatch patch, final List<ShelvedChange> changes) {
    for(ShelvedChange change: changes) {
      if (Comparing.equal(patch.getBeforeName(), change.getBeforePath())) {
        return true;
      }
    }
    return false;
  }

  private static void writePatchesToFile(final String path, final List<FilePatch> remainingPatches) {
    OutputStreamWriter writer;
    try {
      writer = new OutputStreamWriter(new FileOutputStream(path));
      try {
        UnifiedDiffWriter.write(remainingPatches, writer, "\n");
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
                                    final List<ShelvedBinaryFile> remainingBinaries) {
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
    listCopy.DATE = (changeList.DATE == null) ? null : new Date(changeList.DATE.getTime());

    writePatchesToFile(changeList.PATH, remainingPatches);

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
      for (Iterator<ShelvedChange> iterator = listCopy.getChanges().iterator(); iterator.hasNext();) {
        final ShelvedChange change = iterator.next();
        for (ShelvedChange newChange : newList.getChanges()) {
          if (Comparing.equal(change.getBeforePath(), newChange.getBeforePath()) &&
              Comparing.equal(change.getAfterPath(), newChange.getAfterPath())) {
            iterator.remove();
          }
        }
      }

      // needed only if partial unshelve
      try {
        final List<FilePatch> patches = new ArrayList<FilePatch>();
        for (ShelvedChange change : listCopy.getChanges()) {
          patches.add(change.loadFilePatch());
        }
        writePatchesToFile(listCopy.PATH, patches);
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

    if ((! listCopy.getBinaryFiles().isEmpty()) || (! listCopy.getChanges().isEmpty())) {
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

  public static List<TextFilePatch> loadPatches(final String patchPath) throws IOException, PatchSyntaxException {
    char[] text = FileUtil.loadFileText(new File(patchPath));
    PatchReader reader = new PatchReader(new CharArrayCharSequence(text));
    return reader.readAllPatches();
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

    public boolean isNewFile() {
      return myShelvedBinaryFile.BEFORE_PATH == null;
    }
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
