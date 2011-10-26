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
package git4idea.validators;

import com.intellij.openapi.ui.InputValidatorEx;
import git4idea.GitBranch;
import git4idea.repo.GitRepository;

import java.util.HashSet;
import java.util.Set;

/**
 * <p>
 *   In addition to {@link git4idea.validators.GitRefNameValidator} checks that the entered branch name doesn't conflict
 *   with any existing local or remote branch.
 * </p>
 * <p>Use it when creating new branch.</p>
 *
 * @author Kirill Likhodedov
 */
public final class GitNewBranchNameValidator implements InputValidatorEx {

  private final GitRepository myRepository;
  private String myErrorText;
  private Set<String> myLocalNames = new HashSet<String>();
  private Set<String> myRemoteNames = new HashSet<String>();

  private GitNewBranchNameValidator(GitRepository repository) {
    myRepository = repository;
    cacheBranchNames();
  }

  private void cacheBranchNames() {
    for (GitBranch branch : myRepository.getBranches().getLocalBranches()) {
      myLocalNames.add(branch.getName());
    }
    for (GitBranch branch : myRepository.getBranches().getRemoteBranches()) {
      myRemoteNames.add(branch.getName());
    }
  }

  public static GitNewBranchNameValidator newInstance(GitRepository repository) {
    return new GitNewBranchNameValidator(repository);
  }

  @Override
  public boolean checkInput(String inputString) {
    if (!GitRefNameValidator.getInstance().checkInput(inputString)){
      myErrorText = "Invalid name for branch";
      return false;
    }
    return checkBranchConflict(inputString);
  }

  private boolean checkBranchConflict(String inputString) {
    if (myLocalNames.contains(inputString)) {
      myErrorText = "Branch " + inputString + " already exists";
      return false;
    }
    if (myRemoteNames.contains(inputString)) {
      myErrorText = "Branch name " + inputString + " clashes with remote branch with the same name";
      return false;
    }
    myErrorText = null;
    return true;
  }

  @Override
  public boolean canClose(String inputString) {
    return checkInput(inputString);
  }

  @Override
  public String getErrorText(String inputString) {
    return myErrorText;
  }
}
