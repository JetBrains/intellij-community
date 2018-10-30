/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.impl.HashImpl;
import git4idea.index.GitIndexUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.CalledWithWriteLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class GitIndexManager extends AbstractProjectComponent {
  private static final Logger LOG = Logger.getInstance(GitIndexManager.class);

  private final Collection<Root> myRoots = new ArrayList<>();

  public GitIndexManager(@NotNull Project project) {
    super(project);
  }

  public static GitIndexManager getInstance(@NotNull Project project) {
    return project.getComponent(GitIndexManager.class);
  }

  @Override
  public void projectOpened() {
    updateRoots();

    myProject.getMessageBus().connect().subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, () -> {
      ApplicationManager.getApplication().invokeLater(() -> updateRoots());
    });
  }

  @Override
  public void projectClosed() {
    updateRoots();
  }


  @Nullable
  public GitIndexVirtualFile getVirtualFile(@NotNull GitRepository repository, @NotNull FilePath path) {
    Root root = getRoot(repository);
    return root != null ? root.findFile(path) : null;
  }

  @Nullable
  private Root getRoot(@NotNull GitRepository repository) {
    synchronized (myRoots) {
      return ContainerUtil.find(myRoots, root -> repository.equals(root.getRepository()));
    }
  }


  @CalledInAwt
  private void updateRoots() {
    synchronized (myRoots) {
      WriteAction.run(() -> {
        for (Root root : myRoots) {
          root.destroy();
        }
        myRoots.clear();

        if (myProject.isOpen()) {
          List<GitRepository> repositories = GitRepositoryManager.getInstance(myProject).getRepositories();

          myRoots.addAll(ContainerUtil.map(repositories, Root::new));
        }
      });
    }
  }

  @CalledInAwt
  public void refresh(boolean force) {
    synchronized (myRoots) {
      WriteAction.run(() -> {
        myRoots.forEach(root -> root.refresh(force));
      });
    }
  }

  private static class Root {
    @NotNull private final GitRepository myRepository;
    @NotNull private final MessageBusConnection myConnection;

    @NotNull private final Map<FilePath, GitIndexVirtualFile> myFiles = ContainerUtil.createWeakValueMap();
    private boolean myIsDirty;

    private Root(@NotNull GitRepository repository) {
      myRepository = repository;
      myConnection = myRepository.getProject().getMessageBus().connect();

      myConnection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
        @Override
        public void after(@NotNull List<? extends VFileEvent> events) {
          if (myIsDirty) return;

          boolean indexFileModified = ContainerUtil.exists(events, e -> myRepository.getRepositoryFiles().isIndexFile(e.getPath()));
          if (indexFileModified) {
            myIsDirty = true;
            ApplicationManager.getApplication().invokeLater(() -> {
              WriteAction.run(() -> refresh(false));
            }, ModalityState.any());
          }
        }
      });
    }

    @NotNull
    public GitRepository getRepository() {
      return myRepository;
    }

    @CalledWithWriteLock
    public void destroy() {
      ApplicationManager.getApplication().assertWriteAccessAllowed();

      myConnection.disconnect();

      synchronized (myFiles) {
        myFiles.values().forEach(GitIndexVirtualFile::invalidate);
        myFiles.clear();
      }

      myIsDirty = false;
    }

    @CalledWithWriteLock
    public void refresh(boolean force) {
      ApplicationManager.getApplication().assertWriteAccessAllowed();

      if (!myIsDirty && !force) return;
      myIsDirty = false;

      synchronized (myFiles) {
        List<GitIndexVirtualFile> oldFiles = new ArrayList<>(myFiles.values());

        Map<FilePath, GitIndexUtil.StagedFile> stagedFiles;
        try {
          List<GitIndexUtil.StagedFile> staged = GitIndexUtil.listStaged(myRepository, myFiles.keySet());
          stagedFiles = ContainerUtil.newMapFromValues(staged.iterator(), it -> it.getPath());
        }
        catch (VcsException e) {
          LOG.error(e);
          stagedFiles = Collections.emptyMap();
        }

        myFiles.clear();

        for (GitIndexVirtualFile file : oldFiles) {
          FilePath path = file.getFilePath();
          GitIndexUtil.StagedFile stagedFile = stagedFiles.get(path);
          if (stagedFile == null) {
            file.invalidate();
          }
          else {
            Hash hash = HashImpl.build(stagedFile.getBlobHash());
            file.setHash(hash, stagedFile.isExecutable());
            myFiles.put(path, file);
          }
        }
      }
    }

    @Nullable
    public GitIndexVirtualFile findFile(@NotNull FilePath filePath) {
      try {
        synchronized (myFiles) {
          GitIndexVirtualFile file = myFiles.get(filePath);
          if (file != null && file.isValid()) return file;
        }

        GitIndexUtil.StagedFile stagedFile = GitIndexUtil.listStaged(myRepository, filePath);
        if (stagedFile == null) return null;

        Hash hash = HashImpl.build(stagedFile.getBlobHash());
        GitIndexVirtualFile newFile = new GitIndexVirtualFile(myRepository, filePath, hash, stagedFile.isExecutable());

        synchronized (myFiles) {
          GitIndexVirtualFile file = myFiles.get(filePath);
          if (file != null && file.isValid()) return file;

          myFiles.put(filePath, newFile);
        }

        return newFile;
      }
      catch (VcsException e) {
        LOG.error(e);
        return null;
      }
    }
  }
}
