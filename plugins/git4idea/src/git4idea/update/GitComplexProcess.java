/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.update;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.util.continuation.Continuation;
import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.continuation.TaskDescriptor;
import com.intellij.util.continuation.Where;
import git4idea.GitUtil;
import git4idea.GitPlatformFacade;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.util.GitFreezingProcess;

/**
 * This class executes a Git task, surrounding it with a couple of preparation and completion tasks, such as:
 * <ul>
 *   <li>Prohibit saving and syncing on frame deactivation.</li>
 *   <li>{@link ChangeListManager#freeze(com.intellij.util.continuation.ContinuationPause, String) Freeze} the {@link ChangeListManager}</li>
 * </ul>
 *
 * <p>
 *   To use it implement the {@link Operation} interface which runs the needed Git task itself and call
 *   {@link GitComplexProcess#execute(com.intellij.openapi.project.Project, String, git4idea.update.GitComplexProcess.Operation)}
 * </p>
 *
 * @author irengrig
 * @author Kirill Likhodedov
 */
public class GitComplexProcess {

  public interface Operation {
    void run(ContinuationContext continuationContext);
  }

  private final Project myProject;
  private final String myTitle;
  private final Operation myOperation;

  private final String myFreezeReason;
  private final GitRepositoryManager myRepositoryManager;
  private final ChangeListManager myChangeListManager;

  private final TaskDescriptor BLOCK = new TaskDescriptor("", Where.AWT) {
    @Override public void run(ContinuationContext context) {
      GitFreezingProcess.saveAndBlock(ServiceManager.getService(myProject, GitPlatformFacade.class));
    }
  };

  private final TaskDescriptor FREEZE;

  private final TaskDescriptor RELEASE = new TaskDescriptor("", Where.AWT) {
    @Override public void run(ContinuationContext context) {
      myChangeListManager.letGo();
    }

    @Override public boolean isHaveMagicCure() {
      return true;
    }
  };

  private final TaskDescriptor UNBLOCK = new TaskDescriptor("", Where.AWT) {
    @Override public void run(ContinuationContext context) {
      GitFreezingProcess.unblock(ServiceManager.getService(myProject, GitPlatformFacade.class));
    }

    @Override public boolean isHaveMagicCure() {
      return true;
    }
  };

  private final TaskDescriptor UPDATE_REPOSITORIES;

  public static void execute(Project project, String title, Operation operation) {
    new GitComplexProcess(project, title, operation).run();
  }

  private GitComplexProcess(Project project, String title, Operation operation) {
    myProject = project;
    myTitle = title;
    myOperation = operation;
    myFreezeReason = "Local changes are not available until Git " + myTitle + " is finished.";

    myRepositoryManager = GitUtil.getRepositoryManager(myProject);
    myChangeListManager = ChangeListManager.getInstance(myProject);

    // define tasks that need information from constructor

    FREEZE = new TaskDescriptor(myTitle, Where.POOLED) {
      @Override public void run(ContinuationContext context) {
        myChangeListManager.freeze(context, myFreezeReason);
      }
    };

    UPDATE_REPOSITORIES = new TaskDescriptor(myTitle, Where.POOLED) {
      @Override public void run(ContinuationContext context) {
        for (GitRepository repo : myRepositoryManager.getRepositories()) {
          repo.update();
        }
      }
    };
  }

  private void run() {
    Continuation continuation = Continuation.createForCurrentProgress(myProject, true);
    String taskTitle = "Git: " + myTitle;
    TaskDescriptor operation = new TaskDescriptor(taskTitle, Where.POOLED) {
      @Override public void run(final ContinuationContext context) {
        myOperation.run(context);
      }
    };

    final TaskDescriptor[] tasks = {
      BLOCK,
      FREEZE,
      operation,
      UPDATE_REPOSITORIES,
      RELEASE,
      UNBLOCK
    };

    continuation.run(tasks);
  }

}
