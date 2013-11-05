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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.Executor;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

/**
 * Environment to run Git.
 * @author Kirill Likhodedov
 * @deprecated Use {@link Executor}.
 */
@Deprecated
public class GitTestRunEnv {

  private File myRootDir;

  public GitTestRunEnv(@NotNull File rootDir) {
    myRootDir = rootDir;
  }

  public String run(@NotNull String command, String... params) throws IOException {
    return run(false, command, params);
  }

  public String run(boolean silent, @NotNull String command, String... params) throws IOException {
    new Executor().cd(myRootDir.getPath());
    return new GitExecutor().git(command + " " + StringUtil.join(params, " "));
  }

}