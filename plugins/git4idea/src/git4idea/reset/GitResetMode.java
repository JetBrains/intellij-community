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

import com.intellij.openapi.util.NlsContexts;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public enum GitResetMode {

  SOFT("git.reset.mode.soft", "--soft", "git.reset.mode.soft.description"),
  MIXED("git.reset.mode.mixed", "--mixed", "git.reset.mode.mixed.description"),
  HARD("git.reset.mode.hard", "--hard", "git.reset.mode.hard.description"),
  KEEP("git.reset.mode.keep", "--keep", "git.reset.mode.keep.description");

  @NotNull private final String myName;
  @NotNull private final String myArgument;
  @NotNull private final String myDescription;

  GitResetMode(@NotNull @PropertyKey(resourceBundle = GitBundle.BUNDLE) String name,
               @NotNull @NonNls String argument,
               @NotNull @PropertyKey(resourceBundle = GitBundle.BUNDLE) String description) {
    myName = name;
    myArgument = argument;
    myDescription = description;
  }

  @NotNull
  public static GitResetMode getDefault() {
    return MIXED;
  }

  @NotNull
  @NlsContexts.RadioButton
  public String getName() {
    return GitBundle.message(myName);
  }

  @NotNull
  public String getArgument() {
    return myArgument;
  }

  @NotNull
  @Nls(capitalization = Nls.Capitalization.Sentence)
  public String getDescription() {
    return GitBundle.message(myDescription);
  }
}
