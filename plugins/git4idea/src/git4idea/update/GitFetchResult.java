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
package git4idea.update;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static com.intellij.openapi.util.text.StringUtil.join;
import static com.intellij.openapi.util.text.StringUtil.pluralize;

/**
 * @author Kirill Likhodedov
 */
public final class GitFetchResult {

  private final Type myType;
  private Collection<Exception> myErrors = new ArrayList<>();
  private Collection<String> myPrunedRefs = new ArrayList<>();

  public enum Type {
    SUCCESS,
    CANCELLED,
    NOT_AUTHORIZED,
    ERROR
  }

  public GitFetchResult(@NotNull Type type) {
    myType = type;
  }

  @NotNull
  public static GitFetchResult success() {
    return new GitFetchResult(Type.SUCCESS);
  }

  @NotNull
  public static GitFetchResult cancel() {
    return new GitFetchResult(Type.CANCELLED);
  }

  @NotNull
  public static GitFetchResult error(Collection<Exception> errors) {
    GitFetchResult result = new GitFetchResult(Type.ERROR);
    result.myErrors = errors;
    return result;
  }

  @NotNull
  public static GitFetchResult error(Exception error) {
    return error(Collections.singletonList(error));
  }

  @NotNull
  public static GitFetchResult error(@NotNull String errorMessage) {
    return error(new Exception(errorMessage));
  }
  
  public boolean isSuccess() {
    return myType == Type.SUCCESS;
  }

  public boolean isCancelled() {
    return myType == Type.CANCELLED;
  }

  public boolean isNotAuthorized() {
    return myType == Type.NOT_AUTHORIZED;
  }

  public boolean isError() {
    return myType == Type.ERROR;
  }

  @NotNull
  public Collection<? extends Exception> getErrors() {
    return myErrors;
  }

  public void addPruneInfo(@NotNull Collection<String> prunedRefs) {
    myPrunedRefs.addAll(prunedRefs);
  }

  @NotNull
  public Collection<String> getPrunedRefs() {
    return myPrunedRefs;
  }

  @NotNull
  public String getAdditionalInfo() {
    if (!myPrunedRefs.isEmpty()) {
      return "Pruned obsolete remote " + pluralize("reference", myPrunedRefs.size()) + ": " + join(myPrunedRefs, ", ");
    }
    return "";
  }

}
