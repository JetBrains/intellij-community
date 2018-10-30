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
package git4idea.staging;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.LocalTimeCounter;
import com.intellij.vcs.log.Hash;
import com.intellij.vcsUtil.VcsFileUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.index.GitIndexUtil;
import git4idea.repo.GitRepository;
import git4idea.util.GitFileUtils;
import org.jetbrains.annotations.CalledWithWriteLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static com.intellij.openapi.diagnostic.Logger.getInstance;

class GitIndexVirtualFile extends VirtualFile {
  private static final Logger LOG = getInstance(GitIndexVirtualFile.class);

  @NotNull private final GitRepository myRepository;
  @NotNull private final FilePath myFilePath;

  @NotNull private Hash myHash;
  private boolean myExecutable;

  private boolean myValid = true;

  private long myModificationStamp = LocalTimeCounter.currentTime();

  GitIndexVirtualFile(@NotNull GitRepository repository,
                      @NotNull FilePath path,
                      @NotNull Hash hash,
                      boolean executable) {
    myRepository = repository;
    myFilePath = path;
    myHash = hash;
    myExecutable = executable;
  }

  @NotNull
  public GitRepository getRepository() {
    return myRepository;
  }

  @NotNull
  public FilePath getFilePath() {
    return myFilePath;
  }

  @NotNull
  public Hash getHash() {
    return myHash;
  }

  public boolean isExecutable() {
    return myExecutable;
  }


  @NotNull
  @Override
  public GitIndexFileSystem getFileSystem() {
    return GitIndexFileSystem.getInstance();
  }

  @Override
  public VirtualFile getParent() {
    return null;
  }

  @Override
  public VirtualFile[] getChildren() {
    return VirtualFile.EMPTY_ARRAY;
  }

  @Override
  public boolean isWritable() {
    return true;
  }

  @Override
  public boolean isDirectory() {
    return false;
  }


  @NotNull
  @Override
  public String getName() {
    return myFilePath.getName();
  }

  @NotNull
  @Override
  public String getPath() {
    return getRepository().getRoot().getPath() + myFilePath.getPath();
  }

  @Override
  public long getTimeStamp() {
    return 0;
  }

  @Override
  public long getLength() {
    try {
      GitLineHandler h = new GitLineHandler(myRepository.getProject(), myRepository.getRoot(), GitCommand.CAT_FILE);
      h.setSilent(true);
      h.addParameters("-s");
      h.addParameters(myHash.asString());
      h.endOptions();
      String output = Git.getInstance().runCommand(h).getOutputOrThrow();
      return Integer.valueOf(output.trim());
    }
    catch (VcsException e) {
      return 0;
    }
  }


  @Override
  public boolean isValid() {
    return myValid && !Disposer.isDisposed(myRepository);
  }

  public void invalidate() {
    myValid = false;
  }

  @Override
  public void refresh(boolean asynchronous, boolean recursive, @Nullable Runnable postRunnable) {
    if (postRunnable != null) {
      postRunnable.run();
    }
  }


  @NotNull
  @Override
  public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream() {
      @Override
      public void close() throws IOException {
        try {
          // TODO: do not load whole content into memory
          byte[] content = toByteArray();
          Hash newHash = GitIndexUtil.write(myRepository, myFilePath, content, myExecutable);
          setHash(requestor, newHash, newModificationStamp);
        }
        catch (VcsException e) {
          throw new IOException(e);
        }
      }
    };
    return VfsUtilCore.outputStreamAddingBOM(outputStream, this);
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return VfsUtilCore.byteStreamSkippingBOM(contentsToByteArray(), this);
  }

  @NotNull
  @Override
  public byte[] contentsToByteArray() throws IOException {
    try {
      Project project = myRepository.getProject();
      VirtualFile root = myRepository.getRoot();

      return GitFileUtils.getFileContent(project, root, "", VcsFileUtil.relativePath(root, myFilePath));
    }
    catch (VcsException e) {
      throw new IOException(e);
    }
  }


  @Override
  public long getModificationStamp() {
    return myModificationStamp;
  }

  @CalledWithWriteLock
  public void setHash(@NotNull Hash value, boolean executable) {
    myExecutable = executable;
    setHash(null, value, LocalTimeCounter.currentTime());
  }

  @CalledWithWriteLock
  public void setHash(@Nullable Object requestor, @NotNull Hash value, long newModificationStamp) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    if (myHash.equals(value)) return;

    getFileSystem().fireBeforeContentsChange(requestor, this);
    long oldStamp = myModificationStamp;

    myHash = value;

    myModificationStamp = newModificationStamp > 0 ? newModificationStamp : LocalTimeCounter.currentTime();
    getFileSystem().fireContentsChanged(requestor, this, oldStamp);
  }
}
