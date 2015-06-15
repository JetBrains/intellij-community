/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.zmlx.hg4idea.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.repo.HgRepository;

import java.util.Collection;
import java.util.regex.Pattern;

public class HgBranchReferenceValidator extends HgReferenceValidator {
  private static final Pattern DIGITS_ILLEGAL = Pattern.compile("[0-9]*");  // reference names couldn't contain only digits

  public HgBranchReferenceValidator(@NotNull HgRepository repository) {
    super(repository);
  }

  @Override
  public boolean checkInput(@Nullable String name) {
    if (name != null && DIGITS_ILLEGAL.matcher(name).matches()) {
      myErrorText = "Invalid name for branch/tag";
      return false;
    }
    return super.checkInput(name);
  }

  @Override
  protected boolean hasConflictsWithAnotherNames(@Nullable String name) {
    Collection<String> branches = myRepository.getBranches().keySet();
    String currentBranch = myRepository.getCurrentBranch(); //  branches set doesn't contain uncommitted branch -> need an addition check
    myErrorText = currentBranch.equals(name) || branches.contains(name)
                  ? String.format("A branch with the \'%s\' name already exists", name) : null;
    return myErrorText != null;
  }
}
