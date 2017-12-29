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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.*;
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

import static com.intellij.util.ObjectUtils.notNull;

public class GitIndexUtil {
  private static final String EXECUTABLE_MODE = "100755";
  private static final String DEFAULT_MODE = "100644";

  @Nullable
  public static StagedFile list(@NotNull GitRepository repository, @NotNull FilePath filePath) throws VcsException {
    List<StagedFile> result = list(repository, Collections.singleton(filePath));
    if (result.size() != 1) return null;
    return result.get(0);
  }

  @NotNull
  public static List<StagedFile> list(@NotNull GitRepository repository, @NotNull Collection<FilePath> filePaths) throws VcsException {
    List<StagedFile> result = new ArrayList<>();

    GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.LS_FILES);
    h.addParameters("-s");
    h.endOptions();
    h.addRelativePaths(filePaths);

    h.addLineListener(new GitLineHandlerAdapter() {
      @Override
      public void onLineAvailable(String line, Key outputType) {
        if (outputType != ProcessOutputTypes.STDOUT) return;
        ContainerUtil.addIfNotNull(result, parseRecord(repository.getRoot(), line));
      }
    });
    Git.getInstance().runCommandWithoutCollectingOutput(h).getOutputOrThrow();

    return result;
  }

  @Nullable
  private static StagedFile parseRecord(@NotNull VirtualFile root, @NotNull String line) {
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
      return null;
    }
  }

  @NotNull
  public static String write(@NotNull GitRepository repository,
                             @NotNull FilePath filePath,
                             @NotNull byte[] bytes,
                             boolean executable) throws VcsException {
    return write(repository, filePath, new ByteArrayInputStream(bytes), executable);
  }

  @NotNull
  public static String write(@NotNull GitRepository repository,
                             @NotNull FilePath filePath,
                             @NotNull InputStream content,
                             boolean executable) throws VcsException {
    String hash = hashObject(repository, filePath, content);
    updateIndex(repository, filePath, hash, executable);
    return hash;
  }

  @NotNull
  private static String hashObject(@NotNull GitRepository repository,
                                   @NotNull FilePath filePath,
                                   @NotNull InputStream content) throws VcsException {
    GitSimpleHandler h = new GitSimpleHandler(repository.getProject(), repository.getRoot(), GitCommand.HASH_OBJECT);
    h.setSilent(true);
    h.addParameters("-w", "--stdin");
    h.addParameters("--path", VcsFileUtil.relativePath(repository.getRoot(), filePath));
    h.setInputProcessor(out -> {
      try {
        FileUtil.copy(content, out);
      }
      finally {
        out.close();
      }
    });
    h.endOptions();
    String output = h.run();

    if (!h.errors().isEmpty()) {
      notNull(GitVcs.getInstance(repository.getProject())).showErrors(h.errors(), "Applying index modifications");
      throw h.errors().get(0);
    }
    return output.trim();
  }

  private static void updateIndex(@NotNull GitRepository repository,
                                  @NotNull FilePath filePath,
                                  @NotNull String blobHash,
                                  boolean isExecutable) throws VcsException {
    String mode = isExecutable ? EXECUTABLE_MODE : DEFAULT_MODE;
    String path = VcsFileUtil.relativePath(repository.getRoot(), filePath);

    GitSimpleHandler h = new GitSimpleHandler(repository.getProject(), repository.getRoot(), GitCommand.UPDATE_INDEX);
    h.setSilent(true);
    h.addParameters("--cacheinfo", mode + "," + blobHash + "," + path);
    h.endOptions();
    h.run();
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

  public static class StagedFile {
    @NotNull private final FilePath myPath;
    @NotNull private final String myBlobHash;
    private final boolean myExecutable;

    public StagedFile(@NotNull FilePath path, @NotNull String blobHash, boolean executable) {
      myPath = path;
      myBlobHash = blobHash;
      myExecutable = executable;
    }

    @NotNull
    public FilePath getPath() {
      return myPath;
    }

    @NotNull
    public String getBlobHash() {
      return myBlobHash;
    }

    public boolean isExecutable() {
      return myExecutable;
    }
  }
}
