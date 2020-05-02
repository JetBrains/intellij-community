// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch.formove;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.diff.impl.patch.apply.ApplyFilePatchBase;
import com.intellij.openapi.diff.impl.patch.apply.ApplyFilePatchFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vcs.changes.patch.RelativePathCalculator;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PathsVerifier {
  // in
  private final Project myProject;
  private final VirtualFile myBaseDirectory;
  private final List<? extends FilePatch> myPatches;
  // temp
  private final Map<VirtualFile, MovedFileData> myMovedFiles;
  private final List<FilePath> myBeforePaths;
  private final List<VirtualFile> myCreatedDirectories;
  // out
  private final List<PatchAndFile> myTextPatches;
  private final List<PatchAndFile> myBinaryPatches;
  @NotNull private final List<VirtualFile> myWritableFiles;
  private final ProjectLevelVcsManager myVcsManager;
  private final List<FilePatch> mySkipped;
  private DelayedPrecheckContext myDelayedPrecheckContext;
  private final List<FilePath> myAddedPaths;
  private final List<FilePath> myDeletedPaths;
  private boolean myIgnoreContentRootsCheck;

  public PathsVerifier(@NotNull Project project,
                       @NotNull VirtualFile baseDirectory,
                       @NotNull List<? extends FilePatch> patches) {
    myProject = project;
    myBaseDirectory = baseDirectory;
    myPatches = patches;

    myMovedFiles = new HashMap<>();
    myBeforePaths = new ArrayList<>();
    myCreatedDirectories = new ArrayList<>();
    myTextPatches = new ArrayList<>();
    myBinaryPatches = new ArrayList<>();
    myWritableFiles = new ArrayList<>();
    myVcsManager = ProjectLevelVcsManager.getInstance(myProject);
    mySkipped = new ArrayList<>();

    myAddedPaths = new ArrayList<>();
    myDeletedPaths = new ArrayList<>();
  }

  // those to be moved to CL: target + created dirs
  public List<FilePath> getDirectlyAffected() {
    final List<FilePath> affected = new ArrayList<>();
    addAllFilePath(myCreatedDirectories, affected);
    addAllFilePath(myWritableFiles, affected);
    affected.addAll(myBeforePaths);
    return affected;
  }

  // old parents of moved files
  public List<VirtualFile> getAllAffected() {
    final List<VirtualFile> affected = new ArrayList<>();
    affected.addAll(myCreatedDirectories);
    affected.addAll(myWritableFiles);

    // after files' parent
    for (VirtualFile file : myMovedFiles.keySet()) {
      final VirtualFile parent = file.getParent();
      if (parent != null) {
        affected.add(parent);
      }
    }
    // before..
    for (FilePath path : myBeforePaths) {
      final FilePath parent = path.getParentPath();
      if (parent != null) {
        VirtualFile parentFile = parent.getVirtualFile();
        if (parentFile != null) {
          affected.add(parentFile);
        }
      }
    }
    return affected;
  }

  private static void addAllFilePath(final Collection<? extends VirtualFile> files, final Collection<? super FilePath> paths) {
    for (VirtualFile file : files) {
      paths.add(VcsUtil.getFilePath(file));
    }
  }

  List<FilePatch> nonWriteActionPreCheck() {
    List<String> failedMessages = new ArrayList<>();
    List<FilePatch> failedToApply = new ArrayList<>();
    myDelayedPrecheckContext = new DelayedPrecheckContext(myProject);
    for (FilePatch patch : myPatches) {
      final CheckPath checker = getChecker(patch);
      if (!checker.canBeApplied(myDelayedPrecheckContext)) {
        ContainerUtil.addIfNotNull(failedMessages, checker.getErrorMessage());
        failedToApply.add(patch);
      }
    }
    if (!failedMessages.isEmpty()) {
      PatchApplier.showError(myProject, StringUtil.join(failedMessages, "\n"));
    }
    final Collection<FilePatch> skipped = myDelayedPrecheckContext.doDelayed();
    mySkipped.addAll(skipped);
    myPatches.removeAll(skipped);
    myPatches.removeAll(failedToApply);
    return failedToApply;
  }

  List<FilePatch> getSkipped() {
    return mySkipped;
  }

  List<FilePatch> execute() {
    List<String> failedMessages = new ArrayList<>();
    List<FilePatch> failedPatches = new ArrayList<>();
    for (FilePatch patch : myPatches) {
      final CheckPath checker = getChecker(patch);
      if (!checker.check()) {
        ContainerUtil.addIfNotNull(failedMessages, checker.getErrorMessage());
        failedPatches.add(checker.getPatch());
      }
    }
    if (!failedMessages.isEmpty()) {
      PatchApplier.showError(myProject, StringUtil.join(failedMessages, "\n"));
    }
    myPatches.removeAll(failedPatches);
    return failedPatches;
  }

  private CheckPath getChecker(final FilePatch patch) {
    final String beforeFileName = patch.getBeforeName();
    final String afterFileName = patch.getAfterName();

    if (beforeFileName == null || patch.isNewFile()) {
      return new CheckAdded(patch);
    }
    else if (afterFileName == null || patch.isDeletedFile()) {
      return new CheckDeleted(patch);
    }
    else if (!beforeFileName.equals(afterFileName)) {
      return new CheckMoved(patch);
    }
    else {
      return new CheckModified(patch);
    }
  }

  public Collection<FilePath> getToBeAdded() {
    return myAddedPaths;
  }

  public Collection<FilePath> getToBeDeleted() {
    return myDeletedPaths;
  }

  @NotNull
  public Collection<FilePatch> filterBadFileTypePatches() {
    List<PatchAndFile> failedTextPatches =
      ContainerUtil.findAll(myTextPatches, textPatch -> !isFileTypeOk(textPatch.getFile()));
    myTextPatches.removeAll(failedTextPatches);
    return ContainerUtil.map(failedTextPatches, patchInfo -> patchInfo.getApplyPatch().getPatch());
  }

  private boolean isFileTypeOk(@NotNull VirtualFile file) {
    if (file.isDirectory()) {
      PatchApplier.showError(myProject, VcsBundle.message("patch.apply.file.type.directory.error", file.getPresentableName()));
      return false;
    }
    FileType fileType = file.getFileType();
    if (fileType == FileTypes.UNKNOWN) {
      fileType = FileTypeChooser.associateFileType(file.getName());
      if (fileType == null) {
        PatchApplier
          .showError(myProject, VcsBundle.message("patch.apply.file.type.undefined.error", file.getPresentableName()));
        return false;
      }
    }
    if (fileType.isBinary()) {
      PatchApplier.showError(myProject, VcsBundle.message("patch.apply.file.type.binary.error", file.getPresentableName()));
      return false;
    }
    return true;
  }

  private class CheckModified extends CheckDeleted {
    private CheckModified(final FilePatch path) {
      super(path);
    }

    @Override
    protected boolean precheck(VirtualFile beforeFile, VirtualFile afterFile, DelayedPrecheckContext context) {
      if (beforeFile == null) {
        setErrorMessage(fileNotFoundMessage(myBeforeName));
      }
      return beforeFile != null;
    }
  }

  private class CheckDeleted extends CheckPath {
    protected CheckDeleted(final FilePatch path) {
      super(path);
    }

    @Override
    protected boolean precheck(final VirtualFile beforeFile, final VirtualFile afterFile, DelayedPrecheckContext context) {
      if (beforeFile == null) {
        context.addSkip(getMappedFilePath(myBeforeName), myPatch);
      }
      return true;
    }

    @Override
    protected boolean check() {
      final VirtualFile beforeFile = getMappedFile(myBeforeName);
      if (! checkExistsAndValid(beforeFile, myBeforeName)) {
        return false;
      }
      addPatch(myPatch, beforeFile);
      FilePath filePath = VcsUtil.getFilePath(beforeFile.getParent(), beforeFile.getName(), beforeFile.isDirectory());
      if (myPatch.isDeletedFile() || myPatch.getAfterName() == null) {
        myDeletedPaths.add(filePath);
      }
      myBeforePaths.add(filePath);
      return true;
    }
  }

  private class CheckAdded extends CheckPath {
    private CheckAdded(final FilePatch path) {
      super(path);
    }

    @Override
    protected boolean precheck(final VirtualFile beforeFile, final VirtualFile afterFile, DelayedPrecheckContext context) {
      if (afterFile != null) {
        context.addOverrideExisting(myPatch, VcsUtil.getFilePath(afterFile));
      }
      return true;
    }

    @Override
    public boolean check() {
      final String[] pieces = RelativePathCalculator.split(myAfterName);
      final VirtualFile parent;
      try {
        parent = makeSureParentPathExists(pieces);
      }
      catch (IOException e) {
        setErrorMessage(cantCreateFileMessage(myAfterName, e));
        return false;
      }
      if (parent == null) {
        setErrorMessage(fileNotFoundMessage(myAfterName));
        return false;
      }
      String name = pieces[pieces.length - 1];
      File afterFile = new File(parent.getPath(), name);
      final VirtualFile file;
      try {
        //if user already accepted overwriting, we shouldn't have created a new one
        file = myDelayedPrecheckContext.getOverridenPaths().contains(VcsUtil.getFilePath(afterFile))
               ? parent.findChild(name)
               : createFile(parent, name);
      }
      catch (IOException e) {
        setErrorMessage(cantCreateFileMessage(myAfterName, e));
        return false;
      }
      if (file == null) {
        setErrorMessage(fileNotFoundMessage(myAfterName));
        return false;
      }
      myAddedPaths.add(VcsUtil.getFilePath(file));
      if (!checkExistsAndValid(file, myAfterName)) {
        return false;
      }
      addPatch(myPatch, file);
      return true;
    }
  }

  private class CheckMoved extends CheckPath {
    private CheckMoved(final FilePatch path) {
      super(path);
    }

    // before exists; after does not exist
    @Override
    protected boolean precheck(final VirtualFile beforeFile, final VirtualFile afterFile, final DelayedPrecheckContext context) {
      if (beforeFile == null) {
        setErrorMessage(fileNotFoundMessage(myBeforeName));
      } else if (afterFile != null) {
        setErrorMessage(fileAlreadyExists(afterFile.getPath()));
      }
      return beforeFile != null && afterFile == null;
    }

    @Override
    public boolean check() {
      final String[] pieces = RelativePathCalculator.split(myAfterName);
      final VirtualFile afterFileParent;
      try {
        afterFileParent = makeSureParentPathExists(pieces);
      }
      catch (IOException e) {
        setErrorMessage(cantCreateFileMessage(myAfterName, e));
        return false;
      }
      if (afterFileParent == null) {
        setErrorMessage(fileNotFoundMessage(myAfterName));
        return false;
      }
      final VirtualFile beforeFile = getMappedFile(myBeforeName);
      if (!checkExistsAndValid(beforeFile, myBeforeName)) {
        return false;
      }
      myMovedFiles.put(beforeFile, new MovedFileData(afterFileParent, beforeFile, myPatch.getAfterFileName()));
      addPatch(myPatch, beforeFile);
      return true;
    }
  }

  private abstract class CheckPath {
    protected final String myBeforeName;
    protected final String myAfterName;
    protected final FilePatch myPatch;
    private String myErrorMessage;

    CheckPath(final FilePatch path) {
      myPatch = path;
      myBeforeName = path.getBeforeName();
      myAfterName = path.getAfterName();
    }

    public String getErrorMessage() {
      return myErrorMessage;
    }

    public void setErrorMessage(final String errorMessage) {
      myErrorMessage = errorMessage;
    }

    public boolean canBeApplied(DelayedPrecheckContext context) {
      final VirtualFile beforeFile = getMappedFile(myBeforeName);
      final VirtualFile afterFile = getMappedFile(myAfterName);
      return precheck(beforeFile, afterFile, context);
    }

    protected abstract boolean precheck(final VirtualFile beforeFile,
                                        final VirtualFile afterFile,
                                        DelayedPrecheckContext context);

    protected abstract boolean check();

    @Contract("null, _ -> false")
    protected boolean checkExistsAndValid(@Nullable VirtualFile file, final String name) {
      if (file == null) {
        setErrorMessage(fileNotFoundMessage(name));
        return false;
      }
      return checkModificationValid(file, name);
    }

    protected boolean checkModificationValid(@NotNull VirtualFile file, final String name) {
      if (ApplicationManager.getApplication().isUnitTestMode() && myIgnoreContentRootsCheck) return true;
      // security check to avoid overwriting system files with a patch
      if (!inContent(file) && myVcsManager.getVcsRootFor(file) == null) {
        setErrorMessage(VcsBundle.message("patch.apply.outside.content.root.message", name));
        return false;
      }
      return true;
    }

    @Nullable
    protected VirtualFile getMappedFile(String path) {
      return PathMerger.getFile(myBaseDirectory, path);
    }

    protected FilePath getMappedFilePath(String path) {
      return PathMerger.getFile(VcsUtil.getFilePath(myBaseDirectory), path);
    }

    private boolean inContent(VirtualFile file) {
      return myVcsManager.isFileInContent(file);
    }

    public FilePatch getPatch() {
      return myPatch;
    }
  }

  private void addPatch(@NotNull FilePatch patch, @NotNull VirtualFile file) {
    if (patch instanceof TextFilePatch) {
      myTextPatches.add(new PatchAndFile(file, ApplyFilePatchFactory.create((TextFilePatch)patch)));
    }
    else {
      myBinaryPatches.add(new PatchAndFile(file, ApplyFilePatchFactory.createGeneral(patch)));
    }
    myWritableFiles.add(file);
  }

  private static String cantCreateFileMessage(final String path, IOException e) {
    return VcsBundle.message("cannot.create.directory.for.patch", path, e.getMessage());
  }

  private static String fileNotFoundMessage(final String path) {
    return VcsBundle.message("cannot.find.file.to.patch", path);
  }

  private static String fileAlreadyExists(final String path) {
    return VcsBundle.message("cannot.apply.file.already.exists", path);
  }

  private static VirtualFile createFile(final VirtualFile parent, final String name) throws IOException {
    return parent.createChildData(PatchApplier.class, name);
    /*final Ref<IOException> ioExceptionRef = new Ref<IOException>();
    final Ref<VirtualFile> result = new Ref<VirtualFile>();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          result.set(parent.createChildData(PatchApplier.class, name));
        }
        catch (IOException e) {
          ioExceptionRef.set(e);
        }
      }
    });
    if (! ioExceptionRef.isNull()) {
      throw ioExceptionRef.get();
    }
    return result.get();*/
  }

  private static VirtualFile moveFile(final VirtualFile file, final VirtualFile newParent) throws IOException {
    file.move(FilePatch.class, newParent);
    return file;
    /*final Ref<IOException> ioExceptionRef = new Ref<IOException>();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          file.move(FilePatch.class, newParent);
        }
        catch (IOException e) {
          ioExceptionRef.set(e);
        }
      }
    });
    if (! ioExceptionRef.isNull()) {
      throw ioExceptionRef.get();
    }
    return file;*/
  }

  @Nullable
  private VirtualFile makeSureParentPathExists(final String[] pieces) throws IOException {
    VirtualFile child = myBaseDirectory;

    final int size = pieces.length - 1;
    for (int i = 0; i < size; i++) {
      final String piece = pieces[i];
      if (StringUtil.isEmptyOrSpaces(piece)) {
        continue;
      }
      if ("..".equals(piece)) {
        child = child.getParent();
        continue;
      }

      VirtualFile nextChild = child.findChild(piece);
      if (nextChild == null) {
        nextChild = VfsUtil.createDirectories(child.getPath() + '/' + piece);
        if (nextChild == null) throw new IOException("Can't create directory: " + piece);
        myCreatedDirectories.add(nextChild);
      }
      child = nextChild;
    }
    return child;
  }

  public List<PatchAndFile> getTextPatches() {
    return myTextPatches;
  }

  public List<PatchAndFile> getBinaryPatches() {
    return myBinaryPatches;
  }

  @NotNull
  public List<VirtualFile> getWritableFiles() {
    return myWritableFiles;
  }

  public void doMoveIfNeeded(final VirtualFile file) throws IOException {
    final MovedFileData movedFile = myMovedFiles.get(file);
    if (movedFile != null) {
      myBeforePaths.add(VcsUtil.getFilePath(file));
      ApplicationManager.getApplication().runWriteAction((ThrowableComputable<VirtualFile, IOException>)() -> movedFile.doMove());
    }
  }

  private static class MovedFileData {
    private final VirtualFile myNewParent;
    private final VirtualFile myCurrent;
    private final String myNewName;

    private MovedFileData(@NotNull final VirtualFile newParent, @NotNull final VirtualFile current, @NotNull final String newName) {
      myNewParent = newParent;
      myCurrent = current;
      myNewName = newName;
    }

    public VirtualFile getCurrent() {
      return myCurrent;
    }

    public VirtualFile getNewParent() {
      return myNewParent;
    }

    public String getNewName() {
      return myNewName;
    }

    public VirtualFile doMove() throws IOException {
      final VirtualFile oldParent = myCurrent.getParent();
      boolean needRename = !Objects.equals(myCurrent.getName(), myNewName);
      boolean needMove = !myNewParent.equals(oldParent);
      if (needRename) {
        if (needMove) {
          File oldParentFile = VfsUtilCore.virtualToIoFile(oldParent);
          File targetAfterRenameFile = new File(oldParentFile, myNewName);
          if (targetAfterRenameFile.exists() && myCurrent.exists()) {
            // if there is a conflict during first rename we have to rename to third name, then move, then rename to final target
            performRenameWithConflicts(oldParentFile);
            return myCurrent;
          }
        }
        myCurrent.rename(PatchApplier.class, myNewName);
      }
      if (needMove) {
        myCurrent.move(PatchApplier.class, myNewParent);
      }
      return myCurrent;
    }

    private void performRenameWithConflicts(@NotNull File oldParent) throws IOException {
      File tmpFileWithUniqueName = FileUtil.createTempFile(oldParent, "tempFileToMove", null, false); //NON-NLS
      File newParentFile = VfsUtilCore.virtualToIoFile(myNewParent);
      File destFile = new File(newParentFile, tmpFileWithUniqueName.getName());
      while (destFile.exists()) {
        destFile = new File(newParentFile,
                            FileUtil.createTempFile(oldParent, FileUtilRt.getNameWithoutExtension(destFile.getName()), null, false)
                              .getName());
      }
      myCurrent.rename(PatchApplier.class, destFile.getName());
      myCurrent.move(PatchApplier.class, myNewParent);
      myCurrent.rename(PatchApplier.class, myNewName);
    }
  }

  private static class DelayedPrecheckContext {
    private final Map<FilePath, FilePatch> mySkipDeleted;
    private final Map<FilePath, FilePatch> myOverrideExisting;
    private final List<FilePath> myOverridenPaths;
    private final Project myProject;

    private DelayedPrecheckContext(final Project project) {
      myProject = project;
      myOverrideExisting = new HashMap<>();
      mySkipDeleted = new HashMap<>();
      myOverridenPaths = new ArrayList<>();
    }

    public void addSkip(final FilePath path, final FilePatch filePatch) {
      mySkipDeleted.put(path, filePatch);
    }

    public void addOverrideExisting(final FilePatch patch, final FilePath filePath) {
      if (! myOverrideExisting.containsKey(filePath)) {
        myOverrideExisting.put(filePath, patch);
      }
    }

    // returns those to be skipped
    public Collection<FilePatch> doDelayed() {
      final List<FilePatch> result = new ArrayList<>();
      if (!myOverrideExisting.isEmpty()) {
        ApplicationManager.getApplication().invokeAndWait(() -> {
          final String title = VcsBundle.message("patch.apply.overwrite.existing.title");
          List<FilePath> files = new ArrayList<>(myOverrideExisting.keySet());
          Collection<FilePath> selected = AbstractVcsHelper.getInstance(myProject).selectFilePathsToProcess(
            files, title, VcsBundle.message("patch.apply.overwrite.existing.files.prompt"), title,
            VcsBundle.getString("patch.apply.overwrite.existing.file.prompt"),
            VcsShowConfirmationOption.STATIC_SHOW_CONFIRMATION,
            CommonBundle.message("button.overwrite"), IdeBundle.message("button.cancel"));
          if (selected != null) {
            for (FilePath path : selected) {
              myOverrideExisting.remove(path);
            }
          }
          result.addAll(myOverrideExisting.values());
          if (selected != null) {
            myOverridenPaths.addAll(selected);
          }
        });
      }
      result.addAll(mySkipDeleted.values());
      return result;
    }

    public List<FilePath> getOverridenPaths() {
      return myOverridenPaths;
    }

    public Collection<FilePath> getAlreadyDeletedPaths() {
      return mySkipDeleted.keySet();
    }
  }

  public void setIgnoreContentRootsCheck(boolean ignoreContentRootsCheck) {
    myIgnoreContentRootsCheck = ignoreContentRootsCheck;
  }

  public static class PatchAndFile {
    private final VirtualFile myFile;
    private final ApplyFilePatchBase<?> myPatch;

    public PatchAndFile(VirtualFile file, ApplyFilePatchBase<?> patch) {
      myFile = file;
      myPatch = patch;
    }

    public VirtualFile getFile() {
      return myFile;
    }

    public ApplyFilePatchBase<?> getApplyPatch() {
      return myPatch;
    }
  }
}
