// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.update;

import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static com.intellij.openapi.util.text.StringUtil.join;

/**
 * @deprecated Use {@link git4idea.fetch.GitFetchSupport}
 */
@Deprecated
public final class GitFetchResult {

  private final Type myType;
  private Collection<Exception> myErrors = new ArrayList<>();
  private final Collection<String> myPrunedRefs = new ArrayList<>();

  public enum Type {
    SUCCESS,
    NOT_AUTHORIZED,
    ERROR
  }

  public GitFetchResult(@NotNull Type type) {
    myType = type;
  }

  public static @NotNull GitFetchResult success() {
    return new GitFetchResult(Type.SUCCESS);
  }

  public static @NotNull GitFetchResult error(Collection<Exception> errors) {
    GitFetchResult result = new GitFetchResult(Type.ERROR);
    result.myErrors = errors;
    return result;
  }

  public static @NotNull GitFetchResult error(Exception error) {
    return error(Collections.singletonList(error));
  }

  public static @NotNull GitFetchResult error(@NotNull String errorMessage) {
    return error(new Exception(errorMessage));
  }
  
  public boolean isSuccess() {
    return myType == Type.SUCCESS;
  }

  public boolean isNotAuthorized() {
    return myType == Type.NOT_AUTHORIZED;
  }

  public boolean isError() {
    return myType == Type.ERROR;
  }

  public @NotNull Collection<? extends Exception> getErrors() {
    return myErrors;
  }

  public void addPruneInfo(@NotNull Collection<String> prunedRefs) {
    myPrunedRefs.addAll(prunedRefs);
  }

  public @NotNull Collection<String> getPrunedRefs() {
    return myPrunedRefs;
  }

  public @NotNull @Nls String getAdditionalInfo() {
    if (!myPrunedRefs.isEmpty()) {
      return GitBundle.message("fetch.pruned.obsolete.remote.references", myPrunedRefs.size(), join(myPrunedRefs, ", "));
    }
    return "";
  }

}
