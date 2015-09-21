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
package git4idea.branch;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static java.util.Collections.singletonList;

public class GitRebaseParams {

  public enum Mode {
    STANDARD,
    CONTINUE,
    SKIP,
    ABORT;

    @NotNull
    @SuppressWarnings("StringToUpperCaseOrToLowerCaseWithoutLocale")
    String asArgument() {
      if (this == STANDARD) return "";
      return "--" + this.name().toLowerCase();
    }
  }

  @Nullable private final String myBase;
  @NotNull private final Mode myMode;

  @NotNull
  public static GitRebaseParams abort() {
    return new GitRebaseParams(null, Mode.ABORT);
  }

  public GitRebaseParams(@NotNull String base) {
    this(base, Mode.STANDARD);
  }

  private GitRebaseParams(@Nullable String base, @NotNull Mode mode) {
    myBase = base;
    myMode = mode;
  }

  @NotNull
  public GitRebaseParams withMode(@NotNull Mode mode) {
    return mode == myMode ? this : new GitRebaseParams(myBase, mode);
  }

  @NotNull
  public String getBase() {
    return StringUtil.notNullize(myBase);
  }

  @NotNull
  public Mode getMode() {
    return myMode;
  }

  @NotNull
  public List<String> getCommandLineArguments() {
    if (myMode != Mode.STANDARD) {
      return singletonList(myMode.asArgument());
    }
    List<String> args = ContainerUtil.newArrayList();
    args.add(myBase);
    return args;
  }
}
