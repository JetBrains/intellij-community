// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogObjectsFactory;
import com.intellij.vcs.log.VcsUser;
import git4idea.config.GitConfigUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public final class GitUserRegistry implements Disposable, VcsListener {
  private static final Logger LOG = Logger.getInstance(GitUserRegistry.class);

  private final @NotNull Project myProject;
  private final @NotNull Map<VirtualFile, VcsUser> myUserMap = new ConcurrentHashMap<>();

  public GitUserRegistry(@NotNull Project project) {
    myProject = project;
  }

  public static GitUserRegistry getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GitUserRegistry.class);
  }

  public void activate() {
    myProject.getMessageBus().connect(this).subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, this);
    directoryMappingChanged();
  }

  public @Nullable VcsUser getUser(@NotNull VirtualFile root) {
    return myUserMap.get(root);
  }

  public @Nullable VcsUser getOrReadUser(@NotNull VirtualFile root) {
    VcsUser user = myUserMap.get(root);
    if (user == null) {
      try {
        user = readCurrentUser(myProject, root);
        if (user != null) {
          myUserMap.put(root, user);
        }
      }
      catch (VcsException e) {
        LOG.warn("Could not retrieve user name in " + root, e);
      }
    }
    return user;
  }

  private static @Nullable VcsUser readCurrentUser(@NotNull Project project, @NotNull VirtualFile root) throws VcsException {
    String userName = GitConfigUtil.getValue(project, root, GitConfigUtil.USER_NAME);
    String userEmail = StringUtil.notNullize(GitConfigUtil.getValue(project, root, GitConfigUtil.USER_EMAIL));
    return userName == null ? null : project.getService(VcsLogObjectsFactory.class).createUser(userName, userEmail);
  }

  @Override
  public void dispose() {
    myUserMap.clear();
  }

  @Override
  public void directoryMappingChanged() {
    final VirtualFile[] roots = ProjectLevelVcsManager.getInstance(myProject).getRootsUnderVcs(GitVcs.getInstance(myProject));
    final Collection<VirtualFile> rootsToCheck = ContainerUtil.filter(roots, root -> getUser(root) == null);
    if (!rootsToCheck.isEmpty()) {
      Runnable task = () -> {
        for (VirtualFile root : rootsToCheck) {
          getOrReadUser(root);
        }
      };
      BackgroundTaskUtil.executeOnPooledThread(this, task);
    }
  }
}
