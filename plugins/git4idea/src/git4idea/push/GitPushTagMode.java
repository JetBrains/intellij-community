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
package git4idea.push;

import com.intellij.dvcs.push.VcsPushOptionValue;
import org.jetbrains.annotations.NotNull;

/**
 */
public final class GitPushTagMode implements VcsPushOptionValue {

  public static final GitPushTagMode ALL = new GitPushTagMode("All", "--tags");
  public static final GitPushTagMode FOLLOW = new GitPushTagMode("Current Branch", "--follow-tags");

  @NotNull private String myTitle;
  @NotNull private String myArgument;

  // for deserialization
  @SuppressWarnings("UnusedDeclaration")
  public GitPushTagMode() {
    this(ALL.getTitle(), ALL.getArgument());
  }

  private GitPushTagMode(@NotNull String title, @NotNull String argument) {
    myTitle = title;
    myArgument = argument;
  }

  @NotNull
  public static Object[] getValues() {
    return new Object[] { ALL, FOLLOW };
  }

  @NotNull
  public String getTitle() {
    return myTitle;
  }

  @NotNull
  public String getArgument() {
    return myArgument;
  }

  // for deserialization
  @SuppressWarnings("UnusedDeclaration")
  public void setTitle(@NotNull String title) {
    myTitle = title;
  }

  // for deserialization
  @SuppressWarnings("UnusedDeclaration")
  public void setArgument(@NotNull String argument) {
    myArgument = argument;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GitPushTagMode mode = (GitPushTagMode)o;

    if (!myArgument.equals(mode.myArgument)) return false;
    if (!myTitle.equals(mode.myTitle)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myTitle.hashCode();
    result = 31 * result + myArgument.hashCode();
    return result;
  }
}
