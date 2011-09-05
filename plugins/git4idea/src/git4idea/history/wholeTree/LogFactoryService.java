/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.history.wholeTree;

import com.intellij.lifecycle.PeriodicalTasksCloser;
import com.intellij.openapi.project.Project;

/**
 * @author irengrig
 */
public class LogFactoryService {
  private final Project myProject;
  private final GitCommitsSequentially myGitCommitsSequentially;

  public static LogFactoryService getInstance(final Project project) {
    return PeriodicalTasksCloser.getInstance().safeGetService(project, LogFactoryService.class);
  }

  public LogFactoryService(final Project project, final GitCommitsSequentially gitCommitsSequentially) {
    myProject = project;
    myGitCommitsSequentially = gitCommitsSequentially;
  }

  public GitLog createComponent(boolean projectScope) {
    return new GitLogAssembler(myProject, projectScope, myGitCommitsSequentially);
  }
}
