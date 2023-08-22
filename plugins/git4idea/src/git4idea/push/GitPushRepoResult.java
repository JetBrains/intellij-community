// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.push;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitRemoteBranch;
import git4idea.update.GitUpdateResult;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Result of pushing one repository.
 * <p/>
 * Includes information about the number of pushed commits (or -1 if undefined),
 * and tells whether the repository was updated after the push was rejected.
 *
 * @see GitPushNativeResult
 */
public final class GitPushRepoResult {

  public enum Type {
    SUCCESS,
    NEW_BRANCH,
    UP_TO_DATE,
    FORCED,
    REJECTED_NO_FF,
    REJECTED_STALE_INFO,
    REJECTED_OTHER,
    ERROR,
    NOT_PUSHED
  }

  static Comparator<Type> TYPE_COMPARATOR = Comparator.naturalOrder();

  @NotNull private final Type myType;
  private final int myCommits;
  @NotNull private final String mySourceBranch;
  @NotNull private final String myTargetBranch;
  @NotNull private final String myTargetRemote;
  @NotNull private final List<String> myPushedTags;
  @Nullable private final String myError;
  @Nullable private final GitUpdateResult myUpdateResult;

  @NotNull
  public static GitPushRepoResult convertFromNative(@NotNull GitPushNativeResult result,
                                                    @NotNull List<? extends GitPushNativeResult> tagResults,
                                                    int commits,
                                                    @NotNull GitPushSource source,
                                                    @NotNull GitRemoteBranch target) {
    List<String> tags = ContainerUtil.map(tagResults, result1 -> result1.getSourceRef());
    return new GitPushRepoResult(convertType(result), commits, source.getRevision(), target.getFullName(),
                                 target.getRemote().getName(), tags, result.getReason(), null);
  }

  @NotNull
  public static GitPushRepoResult error(@NotNull GitPushSource source, @NotNull GitRemoteBranch target, @NotNull String error) {
    return new GitPushRepoResult(Type.ERROR, -1, source.getRevision(), target.getFullName(),
                                 target.getRemote().getName(), Collections.emptyList(), error, null);
  }

  @NotNull
  public static GitPushRepoResult notPushed(@NotNull GitPushSource source, @NotNull GitRemoteBranch target) {
    return new GitPushRepoResult(Type.NOT_PUSHED, -1, source.getRevision(), target.getFullName(),
                                 target.getRemote().getName(), Collections.emptyList(), null, null);
  }

  @NotNull
  static GitPushRepoResult addUpdateResult(GitPushRepoResult original, GitUpdateResult updateResult) {
    return new GitPushRepoResult(original.getType(), original.getNumberOfPushedCommits(), original.getSourceBranch(),
                                 original.getTargetBranch(), original.getTargetRemote(), original.getPushedTags(),
                                 original.getError(), updateResult);
  }

  private GitPushRepoResult(@NotNull Type type, int pushedCommits, @NotNull String sourceBranch, @NotNull String targetBranch,
                            @NotNull String targetRemote,
                            @NotNull List<String> pushedTags, @Nullable String error, @Nullable GitUpdateResult result) {
    myType = type;
    myCommits = pushedCommits;
    mySourceBranch = sourceBranch;
    myTargetBranch = targetBranch;
    myTargetRemote = targetRemote;
    myPushedTags = pushedTags;
    myError = error;
    myUpdateResult = result;
  }

  @NotNull
  public Type getType() {
    return myType;
  }

  @Nullable
  GitUpdateResult getUpdateResult() {
    return myUpdateResult;
  }

  int getNumberOfPushedCommits() {
    return myCommits;
  }

  /**
   * Returns the branch we were pushing from, in the full-name format, e.g. {@code refs/heads/master} or a revision hash.
   */
  @NotNull
  String getSourceBranch() {
    return mySourceBranch;
  }

  /**
   * Returns the branch we were pushing to, in the full-name format, e.g. {@code refs/remotes/origin/master}.
   */
  @NotNull
  String getTargetBranch() {
    return myTargetBranch;
  }

  @NlsSafe
  @Nullable
  public String getError() {
    return myError;
  }

  @NotNull
  List<@NlsSafe String> getPushedTags() {
    return myPushedTags;
  }

  @NlsSafe
  @NotNull
  public String getTargetRemote() {
    return myTargetRemote;
  }

  @NotNull
  private static Type convertType(@NotNull GitPushNativeResult nativeResult) {
    return switch (nativeResult.getType()) {
      case SUCCESS -> Type.SUCCESS;
      case FORCED_UPDATE -> Type.FORCED;
      case NEW_REF -> Type.NEW_BRANCH;
      case REJECTED -> {
        if (nativeResult.isNonFFUpdate()) yield Type.REJECTED_NO_FF;
        if (nativeResult.isStaleInfo()) yield Type.REJECTED_STALE_INFO;
        yield Type.REJECTED_OTHER;
      }
      case UP_TO_DATE -> Type.UP_TO_DATE;
      case ERROR -> Type.ERROR;
      case DELETED -> throw new IllegalArgumentException("Conversion is not supported: " + nativeResult.getType());
    };
  }

  @NonNls
  @Override
  public String toString() {
    return String.format("%s (%d, '%s'), update: %s}", myType, myCommits, mySourceBranch, myUpdateResult);
  }
}