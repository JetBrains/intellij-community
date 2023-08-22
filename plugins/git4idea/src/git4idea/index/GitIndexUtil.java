// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitUtil;
import git4idea.commands.*;
import git4idea.config.GitVersionSpecialty;
import git4idea.repo.GitRepository;
import git4idea.util.StringScanner;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.diagnostic.Logger.getInstance;

public final class GitIndexUtil {
  private static final Logger LOG = getInstance(GitIndexUtil.class);

  private static final String EXECUTABLE_MODE = "100755";
  private static final String DEFAULT_MODE = "100644";

  @Nullable
  public static StagedFile listStaged(@NotNull GitRepository repository, @NotNull FilePath filePath) throws VcsException {
    List<StagedFile> result = listStaged(repository, Collections.singleton(filePath));
    if (result.size() != 1) return null;
    return result.get(0);
  }

  @NotNull
  public static List<StagedFile> listStaged(@NotNull GitRepository repository, @NotNull Collection<? extends FilePath> filePaths)
    throws VcsException {
    Project project = repository.getProject();
    VirtualFile root = repository.getRoot();

    return listStaged(project, root, filePaths);
  }

  @NotNull
  public static List<StagedFile> listStaged(@NotNull Project project,
                                            @NotNull VirtualFile root,
                                            @NotNull Collection<? extends FilePath> filePaths) throws VcsException {
    List<StagedFile> result = new ArrayList<>();
    GitLineHandler h = new GitLineHandler(project, root, GitCommand.LS_FILES);
    h.addParameters("-s");
    h.endOptions();
    h.addRelativePaths(filePaths);

    h.addLineListener(new GitLineHandlerListener() {
      @Override
      public void onLineAvailable(String line, Key outputType) {
        if (outputType != ProcessOutputTypes.STDOUT) return;
        ContainerUtil.addIfNotNull(result, parseListFilesStagedRecord(root, line));
      }
    });
    Git.getInstance().runCommandWithoutCollectingOutput(h).throwOnError();

    return result;
  }

  @Nullable
  public static StagedFile listTree(@NotNull GitRepository repository,
                                    @NotNull FilePath filePath,
                                    @NotNull VcsRevisionNumber revision) throws VcsException {
    List<StagedFileOrDirectory> result = listTree(repository, Collections.singleton(filePath), revision);
    if (result.size() != 1 || !(result.get(0) instanceof StagedFile)) return null;
    return (StagedFile)result.get(0);
  }

  @NotNull
  public static List<StagedFileOrDirectory> listTree(@NotNull GitRepository repository,
                                                     @NotNull Collection<? extends FilePath> filePaths,
                                                     @NotNull VcsRevisionNumber revision) throws VcsException {
    List<StagedFileOrDirectory> result = new ArrayList<>();
    for (List<String> paths : VcsFileUtil.chunkPaths(repository.getRoot(), filePaths)) {
      result.addAll(listTreeForRawPaths(repository, paths, revision));
    }
    return result;
  }

  @NotNull
  public static List<StagedFileOrDirectory> listTreeForRawPaths(@NotNull GitRepository repository,
                                                                @NotNull List<String> filePaths,
                                                                @NotNull VcsRevisionNumber revision) throws VcsException {
    List<StagedFileOrDirectory> result = new ArrayList<>();
    VirtualFile root = repository.getRoot();

    GitLineHandler h = new GitLineHandler(repository.getProject(), root, GitCommand.LS_TREE);
    h.addParameters(revision.asString());
    h.endOptions();
    h.addParameters(filePaths);

    h.addLineListener(new GitLineHandlerListener() {
      @Override
      public void onLineAvailable(String line, Key outputType) {
        if (outputType != ProcessOutputTypes.STDOUT) return;
        ContainerUtil.addIfNotNull(result, parseListTreeRecord(root, line));
      }
    });
    Git.getInstance().runCommandWithoutCollectingOutput(h).throwOnError();

    return result;
  }

  @Nullable
  private static StagedFile parseListFilesStagedRecord(@NotNull VirtualFile root, @NotNull String line) {
    try {
      StringScanner s = new StringScanner(line);
      String permissions = s.spaceToken();
      String hash = s.spaceToken();
      String stage = s.tabToken();
      String filePath = s.line();

      if (!"0".equals(stage)) return null;

      FilePath path = VcsUtil.getFilePath(root, GitUtil.unescapePath(filePath));
      boolean executable = EXECUTABLE_MODE.equals(permissions);
      return new StagedFile(path, hash, executable);
    }
    catch (VcsException e) {
      LOG.warn(e);
      return null;
    }
  }

  @Nullable
  public static StagedFileOrDirectory parseListTreeRecord(@NotNull VirtualFile root, @NotNull String line) {
    try {
      StringScanner s = new StringScanner(line);
      String permissions = s.spaceToken();
      String type = s.spaceToken();
      String hash = s.tabToken();
      String filePath = GitUtil.unescapePath(s.line());

      if ("tree".equals(type)) return new StagedDirectory(VcsUtil.getFilePath(root, filePath, true));
      if ("commit".equals(type)) return new StagedSubrepo(VcsUtil.getFilePath(root, filePath, true), hash);
      if (!"blob".equals(type)) return null;

      boolean executable = EXECUTABLE_MODE.equals(permissions);
      return new StagedFile(VcsUtil.getFilePath(root, filePath), hash, executable);
    }
    catch (VcsException e) {
      LOG.warn(e);
      return null;
    }
  }

  @NotNull
  public static Hash loadStagedSubmoduleHash(@NotNull GitRepository submodule,
                                             @NotNull GitRepository parentRepo) throws VcsException {
    GitLineHandler h = new GitLineHandler(parentRepo.getProject(), parentRepo.getRoot(), GitCommand.SUBMODULE);
    h.addParameters("status", "--cached");
    h.addRelativeFiles(Collections.singletonList(submodule.getRoot()));
    String out = Git.getInstance().runCommand(h).getOutputOrThrow();

    StringScanner s = new StringScanner(out);
    s.skipChars(1); // status char
    String hash = s.spaceToken();
    return HashImpl.build(hash);
  }

  @Nullable
  public static Hash loadSubmoduleHashAt(@NotNull GitRepository submodule,
                                         @NotNull GitRepository parentRepo,
                                         @NotNull VcsRevisionNumber revisionNumber) throws VcsException {
    FilePath filePath = VcsUtil.getFilePath(submodule.getRoot());
    List<StagedFileOrDirectory> lsTree = listTree(parentRepo, Collections.singletonList(filePath), revisionNumber);
    if (lsTree.size() != 1) {
      LOG.warn(String.format("Unexpected output of ls-tree command for submodule [%s] at [%s]: %s", filePath, revisionNumber, lsTree));
      return null;
    }
    StagedSubrepo tree = ObjectUtils.tryCast(lsTree.get(0), GitIndexUtil.StagedSubrepo.class);
    if (tree == null) {
      LOG.warn(String.format("Unexpected type of ls-tree for submodule [%s] at [%s]: %s", filePath, revisionNumber, tree));
      return null;
    }
    if (!filePath.equals(tree.getPath())) {
      LOG.warn(String.format("Submodule path [%s] doesn't match the ls-tree output path [%s]", tree.getPath(), filePath));
      return null;
    }
    return HashImpl.build(tree.getBlobHash());
  }

  @NotNull
  public static Hash write(@NotNull GitRepository repository,
                           @NotNull FilePath filePath,
                           byte @NotNull [] bytes,
                           boolean executable) throws VcsException {
    return write(repository, filePath, new ByteArrayInputStream(bytes), executable);
  }

  @NotNull
  public static Hash write(@NotNull GitRepository repository, @NotNull FilePath filePath, @NotNull InputStream content,
                           boolean executable) throws VcsException {
    return write(repository.getProject(), repository.getRoot(), filePath, content, executable);
  }

  @NotNull
  public static Hash write(@NotNull Project project, @NotNull VirtualFile root,
                           @NotNull FilePath filePath, @NotNull InputStream content,
                           boolean executable) throws VcsException {
    return write(project, root, filePath, content, executable, false);
  }

  @NotNull
  public static Hash write(@NotNull Project project, @NotNull VirtualFile root,
                           @NotNull FilePath filePath, @NotNull InputStream content,
                           boolean executable, boolean addNewFiles) throws VcsException {
    Hash hash = hashObject(project, root, filePath, content);
    updateIndex(project, root, filePath, hash, executable, addNewFiles);
    return hash;
  }

  @NotNull
  private static Hash hashObject(@NotNull Project project, @NotNull VirtualFile root, @NotNull FilePath filePath,
                                 @NotNull InputStream content) throws VcsException {
    GitLineHandler h = new GitLineHandler(project, root, GitCommand.HASH_OBJECT);
    h.setSilent(true);
    h.addParameters("-w", "--stdin");
    h.addParameters("--path");
    h.addRelativePaths(filePath);
    h.setInputProcessor(GitHandlerInputProcessorUtil.redirectStream(content));
    h.endOptions();
    String output = Git.getInstance().runCommand(h).getOutputOrThrow();
    return HashImpl.build(output.trim());
  }

  public static void updateIndex(@NotNull GitRepository repository,
                                 @NotNull FilePath filePath,
                                 @NotNull Hash blobHash,
                                 boolean isExecutable) throws VcsException {
    updateIndex(repository.getProject(), repository.getRoot(), filePath, blobHash, isExecutable, false);
  }

  public static void updateIndex(@NotNull Project project, @NotNull VirtualFile root, @NotNull FilePath filePath,
                                 @NotNull Hash blobHash,
                                 boolean isExecutable, boolean addNewFiles) throws VcsException {
    String mode = isExecutable ? EXECUTABLE_MODE : DEFAULT_MODE;
    String path = VcsFileUtil.relativePath(root, filePath);

    GitLineHandler h = new GitLineHandler(project, root, GitCommand.UPDATE_INDEX);
    if (addNewFiles) {
      h.addParameters("--add");
    }
    if (GitVersionSpecialty.CACHEINFO_SUPPORTS_SINGLE_PARAMETER_FORM.existsIn(project)) {
      h.addParameters("--cacheinfo", mode + "," + blobHash.asString() + "," + path);
    }
    else {
      h.addParameters("--cacheinfo", mode, blobHash.asString(), path);
    }
    h.endOptions();
    Git.getInstance().runCommandWithoutCollectingOutput(h).throwOnError();
  }

  public static byte @NotNull [] read(@NotNull GitRepository repository, @NotNull String blobHash) throws VcsException {
    Project project = repository.getProject();
    VirtualFile root = repository.getRoot();

    GitBinaryHandler h = new GitBinaryHandler(project, root, GitCommand.SHOW);
    h.setSilent(true);
    h.addParameters(blobHash);
    h.endOptions();
    return h.run();
  }

  public static class StagedFileOrDirectory {
    @NotNull protected final FilePath myPath;

    public StagedFileOrDirectory(@NotNull FilePath path) {
      myPath = path;
    }

    @NotNull
    public FilePath getPath() {
      return myPath;
    }

    @NonNls
    @Override
    public String toString() {
      return "StagedFileOrDirectory[" + myPath + "]";
    }
  }

  public static class StagedFile extends StagedFileOrDirectory {
    @NotNull private final String myBlobHash;
    private final boolean myExecutable;

    public StagedFile(@NotNull FilePath path, @NotNull String blobHash, boolean executable) {
      super(path);
      myBlobHash = blobHash;
      myExecutable = executable;
    }

    @NotNull
    public String getBlobHash() {
      return myBlobHash;
    }

    public boolean isExecutable() {
      return myExecutable;
    }

    @NonNls
    @Override
    public String toString() {
      return "StagedFile[" + myPath + "] at [" + myBlobHash + "]";
    }
  }

  public static class StagedDirectory extends StagedFileOrDirectory {
    public StagedDirectory(@NotNull FilePath path) {
      super(path);
    }
  }

  public static class StagedSubrepo extends StagedFileOrDirectory {
    @NotNull private final String myBlobHash;

    public StagedSubrepo(@NotNull FilePath path, @NotNull String blobHash) {
      super(path);
      myBlobHash = blobHash;
    }

    @NotNull
    public String getBlobHash() {
      return myBlobHash;
    }

    @NonNls
    @Override
    public String toString() {
      return "StagedSubRepo[" + myPath + "] at [" + myBlobHash + "]";
    }
  }
}
