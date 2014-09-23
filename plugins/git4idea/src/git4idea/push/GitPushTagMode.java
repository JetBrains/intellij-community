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
public class GitPushTagMode implements VcsPushOptionValue {

  public static GitPushTagMode ALL = new GitPushTagMode("All", "--tags");
  public static GitPushTagMode FOLLOW = new GitPushTagMode("Current Branch", "--follow-tags");

  @NotNull private final String myTitle;
  @NotNull private final String myArgument;

  public GitPushTagMode(@NotNull String title, @NotNull String argument) {
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
}
