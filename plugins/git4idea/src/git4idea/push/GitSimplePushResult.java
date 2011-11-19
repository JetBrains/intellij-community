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
package git4idea.push;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Kirill Likhodedov
 */
public final class GitSimplePushResult {
  
  private final Type myType;
  private final String myErrorOutput;
  private final Collection<String> myRejectedBranches;

  public enum Type {
    NOT_PUSHED,
    SUCCESS,
    REJECT,
    CANCEL,
    NOT_AUTHORIZED,
    ERROR
  }

  private GitSimplePushResult(@NotNull Type type, @NotNull String errorOutput, @NotNull Collection<String> rejectedBranches) {
    myType = type;
    myErrorOutput = errorOutput;
    myRejectedBranches = rejectedBranches;
  }

  @NotNull
  public static GitSimplePushResult success() {
    return new GitSimplePushResult(Type.SUCCESS, "", Collections.<String>emptyList());
  }
  
  @NotNull
  public static GitSimplePushResult notPushed() {
    return new GitSimplePushResult(Type.NOT_PUSHED, "", Collections.<String>emptyList());
  }

  @NotNull
  public static GitSimplePushResult cancel() {
    return new GitSimplePushResult(Type.CANCEL, "Cancelled by user", Collections.<String>emptyList());
  }

  @NotNull
  public static GitSimplePushResult notAuthorized() {
    return new GitSimplePushResult(Type.NOT_AUTHORIZED, "Couldn't authorize", Collections.<String>emptyList());
  }

  @NotNull
  public static GitSimplePushResult reject(@NotNull Collection<String> rejectedBranches) {
    return new GitSimplePushResult(Type.REJECT, "", rejectedBranches);
  }

  @NotNull
  public static GitSimplePushResult error(@NotNull String errorOutput) {
    return new GitSimplePushResult(Type.ERROR, errorOutput, Collections.<String>emptyList());
  }

  @NotNull
  public String getOutput() {
    return myErrorOutput;
  }

  @NotNull
  public Collection<String> getRejectedBranches() {
    return myRejectedBranches;
  }

  @NotNull
  public Type getType() {
    return myType;
  }

}
