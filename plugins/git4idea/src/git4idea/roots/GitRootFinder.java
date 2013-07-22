package git4idea.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.VcsRootFinder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtilRt;
import git4idea.GitPlatformFacade;
import git4idea.GitVcs;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Denis Zhdanov
 * @since 7/18/13 6:02 PM
 */
public class GitRootFinder implements VcsRootFinder {

  @NotNull private final Project           myProject;
  @NotNull private final GitPlatformFacade myPlatformFacade;

  public GitRootFinder(@NotNull Project project, @NotNull GitPlatformFacade platformFacade) {
    myProject = project;
    myPlatformFacade = platformFacade;
  }

  @NotNull
  @Override
  public Collection<VcsDirectoryMapping> findRoots(@NotNull VirtualFile root) {
    GitRootDetectInfo info = new GitRootDetector(myProject, myPlatformFacade).detect(root);
    Collection<VirtualFile> roots = info.getRoots();
    if (roots.isEmpty()) {
      return Collections.emptyList();
    }
    Collection<VcsDirectoryMapping> result = ContainerUtilRt.newArrayList();
    for (VirtualFile file : roots) {
      result.add(new VcsDirectoryMapping(file.getPath(), GitVcs.getKey().getName()));
    }
    return result;
  }
}
