/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.VcsRootChecker;
import com.intellij.openapi.vcs.VcsRootError;
import git4idea.GitPlatformFacade;
import git4idea.GitUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Kirill Likhodedov
 */
public class GitRootChecker implements VcsRootChecker {

  @NotNull private final Collection<VcsRootError> myErrors;
  private final boolean myProjectMappingIsInvalid;

  public GitRootChecker(@NotNull Project project, @NotNull GitPlatformFacade platformFacade) {
    myErrors = new GitRootErrorsFinder(project, platformFacade).find();
    myProjectMappingIsInvalid = isProjectMappingInvalid();
  }

  private boolean isProjectMappingInvalid() {
    for (VcsRootError error : myErrors) {
      if (error.getType() == VcsRootError.Type.EXTRA_MAPPING && error.getMapping().equals(VcsDirectoryMapping.PROJECT_CONSTANT)) {
        return  true;
      }
    }
    return false;
  }

  @NotNull
  @Override
  public Collection<String> getUnregisteredRoots() {
    Collection<String> roots = new ArrayList<String>();
    for (VcsRootError error : myErrors) {
      if (error.getType() == VcsRootError.Type.UNREGISTERED_ROOT) {
        roots.add(error.getMapping());
      }
    }
    return roots;
  }

  @Override
  public boolean isInvalidMapping(@NotNull VcsDirectoryMapping mapping) {
    // this information is available in myErrors,
    // but the method may be called in VcsDirectoryConfigurationPanel after adding a mapping (to highlight errors right away)
    // in which case ProjectLevelVcsManager#getAllVcsRoots() is not aware of new roots yet,
    // while GitRootErrorsFinder relies on the set of roots returned from ProjectLevelVcsManager.
    if (mapping.isDefaultMapping()) {
      return myProjectMappingIsInvalid;
    }
    return !new File(mapping.getDirectory(), GitUtil.DOT_GIT).exists();
  }
}
