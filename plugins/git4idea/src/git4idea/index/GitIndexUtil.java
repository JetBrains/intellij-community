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
package git4idea.index;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.diagnostic.Logger.getInstance;

public class GitIndexUtil {
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
  public static List<StagedFile> listStaged(@NotNull GitRepository repository, @NotNull Collection<FilePath> filePaths) throws VcsException {
    List<StagedFile> result = new ArrayList<>();
    VirtualFile root = repository.getRoot();

    GitLineHandler h = new GitLineHandler(repository.getProject(), root, GitCommand.LS_FILES);
    h.addParameters("-s");
    h.endOptions();
    h.addRelativePaths(filePaths);

    h.addLineListener(new GitLineHandlerAdapter() {
      @Override
      public void onLineAvailable(String line, Key outputType) {
        if (outputType != ProcessOutputTypes.STDOUT) return;
        ContainerUtil.addIfNotNull(result, parseListFilesStagedRecord(root, line));
      }
    });
    Git.getInstance().runCommandWithoutCollectingOutput(h).getOutputOrThrow();

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
                                                     @NotNull Collection<FilePath> filePath,
                                                     @NotNull VcsRevisionNumber revision) throws VcsException {
    List<StagedFileOrDirectory> result = new ArrayList<>();
    VirtualFile root = repository.getRoot();

    GitLineHandler h = new GitLineHandler(repository.getProject(), root, GitCommand.LS_TREE);
    h.addParameters(revision.asString());
    h.endOptions();
    h.addRelativePaths(filePath);

    h.addLineListener(new GitLineHandlerAdapter() {
      @Override
      public void onLineAvailable(String line, Key outputType) {
        if (outputType != ProcessOutputTypes.STDOUT) return;
        ContainerUtil.addIfNotNull(result, parseListTreeRecord(root, line));
      }
    });
    Git.getInstance().runCommandWithoutCollectingOutput(h).getOutputOrThrow();

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
      String filePath = s.line();

      FilePath path = VcsUtil.getFilePath(root, GitUtil.unescapePath(filePath));

      if ("tree".equals(type)) return new StagedDirectory(path);
      if ("commit".equals(type)) return new StagedSubrepo(path);
      if (!"blob".equals(type)) return null;

      boolean executable = EXECUTABLE_MODE.equals(permissions);
      return new StagedFile(path, hash, executable);
    }
    catch (VcsException e) {
      LOG.warn(e);
      return null;
    }
  }

  @NotNull
  public static Hash write(@NotNull GitRepository repository,
                           @NotNull FilePath filePath,
                           @NotNull byte[] bytes,
                           boolean executable) throws VcsException {
    return write(repository, filePath, new ByteArrayInputStream(bytes), executable);
  }

  @NotNull
  public static Hash write(@NotNull GitRepository repository,
                           @NotNull FilePath filePath,
                           @NotNull InputStream content,
                           boolean executable) throws VcsException {
    Hash hash = hashObject(repository, filePath, content);
    updateIndex(repository, filePath, hash, executable);
    return hash;
  }

  @NotNull
  private static Hash hashObject(@NotNull GitRepository repository,
                                 @NotNull FilePath filePath,
                                 @NotNull InputStream content) throws VcsException {
    GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.HASH_OBJECT);
    h.setSilent(true);
    h.addParameters("-w", "--stdin");
    h.addParameters("--path", VcsFileUtil.relativePath(repository.getRoot(), filePath));
    h.setInputProcessor(GitHandlerInputProcessorUtil.redirectStream(content));
    h.endOptions();
    String output = Git.getInstance().runCommand(h).getOutputOrThrow();
    return HashImpl.build(output.trim());
  }

  public static void updateIndex(@NotNull GitRepository repository,
                                 @NotNull FilePath filePath,
                                 @NotNull Hash blobHash,
                                 boolean isExecutable) throws VcsException {
    String mode = isExecutable ? EXECUTABLE_MODE : DEFAULT_MODE;
    String path = VcsFileUtil.relativePath(repository.getRoot(), filePath);

    GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.UPDATE_INDEX);
    if (GitVersionSpecialty.CACHEINFO_SUPPORTS_SINGLE_PARAMETER_FORM.existsIn(repository)) {
      h.addParameters("--cacheinfo", mode + "," + blobHash.asString() + "," + path);
    }
    else {
      h.addParameters("--cacheinfo", mode, blobHash.asString(), path);
    }
    h.endOptions();
    Git.getInstance().runCommandWithoutCollectingOutput(h).getOutputOrThrow();
  }

  @NotNull
  public static byte[] read(@NotNull GitRepository repository, @NotNull String blobHash) throws VcsException {
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

    public StagedFileOrDirectory(@NotNull FilePath path) {myPath = path;}

    @NotNull
    public FilePath getPath() {
      return myPath;
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
  }

  public static class StagedDirectory extends StagedFileOrDirectory {
    public StagedDirectory(@NotNull FilePath path) {
      super(path);
    }
  }

  public static class StagedSubrepo extends StagedFileOrDirectory {
    public StagedSubrepo(@NotNull FilePath path) {
      super(path);
    }
  }
}
