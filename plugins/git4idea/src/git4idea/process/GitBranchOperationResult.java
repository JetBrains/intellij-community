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
package git4idea.process;

import org.jetbrains.annotations.Nullable;

/**
* @author Kirill Likhodedov
*/
class GitBranchOperationResult {
  private enum Type {
    SUCCESS, RESOLVABLE, ERROR
  }
  private final Type myType;
  private final String myErrorTitle;
  private final String myErrorDescription;

  GitBranchOperationResult(Type type, @Nullable String errorTitle, @Nullable String errorDescription) {
    myType = type;
    myErrorTitle = errorTitle;
    myErrorDescription = errorDescription;
  }

  public static GitBranchOperationResult error(@Nullable String title, @Nullable String description) {
    return new GitBranchOperationResult(Type.ERROR, title, description);
  }

  public static GitBranchOperationResult resolvable() {
    return new GitBranchOperationResult(Type.RESOLVABLE, null, null);
  }

  public static GitBranchOperationResult success() {
    return new GitBranchOperationResult(Type.SUCCESS, null, null);
  }

  boolean isSuccess() {
    return myType.equals(Type.SUCCESS);
  }

  boolean isResolvable() {
    return myType.equals(Type.RESOLVABLE);
  }

  @Nullable
  String getErrorDescription() {
    return myErrorDescription;
  }

  @Nullable
  String getErrorTitle() {
    return myErrorTitle;
  }
}
