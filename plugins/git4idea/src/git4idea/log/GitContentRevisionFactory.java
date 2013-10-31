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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.ContentRevisionFactory;
import com.intellij.vcs.log.Hash;
import git4idea.GitContentRevision;
import git4idea.GitRevisionNumber;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class GitContentRevisionFactory extends ContentRevisionFactory {

  @NotNull private final Project myProject;

  @SuppressWarnings("UnusedDeclaration")
  private GitContentRevisionFactory(@NotNull Project project) {
    myProject = project;
  }

  public static ContentRevisionFactory getInstance(Project project) {
    return ServiceManager.getService(project, GitContentRevisionFactory.class);
  }

  @NotNull
  @Override
  public ContentRevision createRevision(@NotNull VirtualFile file, @NotNull Hash hash) {
    return GitContentRevision.createRevision(file, new GitRevisionNumber(hash.asString()), myProject);
  }

  @NotNull
  @Override
  public ContentRevision createRevision(@NotNull VirtualFile root, @NotNull String path, @NotNull Hash hash) {
    return GitContentRevision.createRevision(new FilePathImpl(new File(path), false),
                                             new GitRevisionNumber(hash.asString()), myProject, null);
  }

}
