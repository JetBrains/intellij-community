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
package git4idea.reset;

import org.jetbrains.annotations.NotNull;

public enum GitResetMode {

  SOFT("Soft", "--soft", "<nobr>Files won't change, differences will be staged for commit.</nobr>"),
  MIXED("Mixed", "--mixed", "<nobr>Files won't change, differences won't be staged.</nobr>"),
  HARD("Hard", "--hard", "<nobr>Files will be reverted to the state of the selected commit.</nobr><br/>" +
                         "<nobr>Warning: any local changes will be lost.</nobr>"),
  KEEP("Keep", "--keep", "<nobr>Files will be reverted to the state of the selected commit,</nobr><br/>" +
                         "<nobr>but local changes will be kept intact.</nobr>");

  @NotNull private final String myName;
  @NotNull private final String myArgument;
  @NotNull private final String myDescription;

  GitResetMode(@NotNull String name, @NotNull String argument, @NotNull String description) {
    myName = name;
    myArgument = argument;
    myDescription = description;
  }

  @NotNull
  public static GitResetMode getDefault() {
    return MIXED;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String getArgument() {
    return myArgument;
  }

  @NotNull
  public String getDescription() {
    return myDescription;
  }

}
