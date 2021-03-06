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
import com.intellij.openapi.ui.ValidationInfo;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * <p>
 * In addition to {@link GitRefNameValidator} checks that the entered branch name doesn't conflict
 * with any existing local or remote branch.
 * </p>
 * <p>Use it when creating new branch.</p>
 * <p>
 * If several repositories are specified (to create a branch in all of them at once, for example), all branches of all repositories are
 * checked for conflicts.
 * </p>
 */
public final class GitNewBranchNameValidator implements InputValidatorEx {

  private final Collection<? extends GitRepository> myRepositories;
  private @Nls String myErrorText;

  private GitNewBranchNameValidator(@NotNull Collection<? extends GitRepository> repositories) {
    myRepositories = repositories;
  }

  public static GitNewBranchNameValidator newInstance(@NotNull Collection<? extends GitRepository> repositories) {
    return new GitNewBranchNameValidator(repositories);
  }

  @Override
  public boolean checkInput(@NotNull String inputString) {
    ValidationInfo info = GitBranchValidatorKt.validateName(myRepositories, inputString);
    myErrorText = info != null ? info.message : null;
    return info == null;
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
