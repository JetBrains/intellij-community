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
package git4idea.test;

import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import static git4idea.test.GitExec.*;

/**
 * @author Kirill Likhodedov
 */
public class GitTestScenarioGenerator {

  public static void prepareUnmergedFiles(@NotNull GitRepository... repositories) throws IOException {
    for (GitRepository repository : repositories) {
      String unmergedFile = "unmerged";
      create(repository, unmergedFile, "master content");
      addCommit(repository, unmergedFile);
      
      checkoutFeatureBranch(repository);
      edit(repository, unmergedFile, "feature content");
      addCommit(repository, unmergedFile);
      
      checkout(repository, "master");
      edit(repository, unmergedFile, "master feature");
      addCommit(repository, unmergedFile);
      
      merge(repository, "feature");
      refresh(repository);
    }
  }

  private static void checkoutFeatureBranch(GitRepository repository) throws IOException {
    String branches = branch(repository);
    if (!branches.contains("feature")) {
      checkout(repository, "-b", "feature");
    } else {
      checkout(repository, "feature");
    }
  }
}
