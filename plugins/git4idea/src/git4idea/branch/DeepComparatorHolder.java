/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package git4idea.branch;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogUi;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class DeepComparatorHolder implements Disposable {

  @NotNull private final Project myProject;
  @NotNull private final GitRepositoryManager myRepositoryManager;

  @NotNull private final Map<VcsLogUi, DeepComparator> myComparators;

  // initialized by pico-container
  @SuppressWarnings("UnusedDeclaration")
  private DeepComparatorHolder(@NotNull Project project, @NotNull GitRepositoryManager repositoryManager) {
    myProject = project;
    myRepositoryManager = repositoryManager;
    myComparators = ContainerUtil.newHashMap();
    Disposer.register(project, this);
  }

  @NotNull
  public DeepComparator getInstance(@NotNull VcsLogUi ui) {
    DeepComparator comparator = myComparators.get(ui);
    if (comparator == null) {
      comparator = new DeepComparator(myProject, myRepositoryManager, ui, this);
      myComparators.put(ui, comparator);
    }
    return comparator;
  }

  @Override
  public void dispose() {
    myComparators.clear();
  }

}
