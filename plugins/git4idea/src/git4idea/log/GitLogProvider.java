/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package git4idea.log;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.vcs.log.CommitData;
import com.intellij.vcs.log.CommitParents;
import com.intellij.vcs.log.VcsLogProvider;
import org.hanuna.gitalk.git.reader.CommitDataReader;
import org.hanuna.gitalk.git.reader.CommitParentsReader;
import org.hanuna.gitalk.git.reader.RefReader;
import com.intellij.vcs.log.Ref;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * @author Kirill Likhodedov
 */
public class GitLogProvider implements VcsLogProvider {

  private final Project myProject;

  public GitLogProvider(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public List<CommitParents> readNextBlock(@NotNull VirtualFile root, @NotNull Consumer<String> statusUpdater) throws IOException {
    return new CommitParentsReader(myProject, root, false).readNextBlock(statusUpdater);
  }

  @NotNull
  @Override
  public List<CommitData> readCommitsData(@NotNull VirtualFile root, @NotNull List<String> hashes) {
    return CommitDataReader.readCommitsData(myProject, hashes, root);
  }

  @Override
  public Collection<? extends Ref> readAllRefs(@NotNull VirtualFile root) throws IOException {
    return new RefReader(myProject, false).readAllRefs(root);
  }
}
