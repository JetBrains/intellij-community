// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  private final @NotNull Type myType;
  private final int myCommits;
  private final @NotNull String mySourceBranch;
  private final @NotNull String myTargetBranch;
  private final @NotNull String myTargetRemote;
  private final @NotNull List<String> myPushedTags;
  private final @Nullable String myError;
  private final @Nullable GitUpdateResult myUpdateResult;

  public static @NotNull GitPushRepoResult convertFromNative(@NotNull GitPushNativeResult result,
                                                             @NotNull List<? extends GitPushNativeResult> tagResults,
                                                             int commits,
                                                             @NotNull GitPushSource source,
                                                             @NotNull GitRemoteBranch target) {
    List<String> tags = ContainerUtil.map(tagResults, result1 -> result1.getSourceRef());
    return new GitPushRepoResult(convertType(result), commits, source.getRevision(), target.getFullName(),
                                 target.getRemote().getName(), tags, result.getReason(), null);
  }

  public static @NotNull GitPushRepoResult error(@NotNull GitPushSource source, @NotNull GitRemoteBranch target, @NotNull String error) {
    return new GitPushRepoResult(Type.ERROR, -1, source.getRevision(), target.getFullName(),
                                 target.getRemote().getName(), Collections.emptyList(), error, null);
  }

  public static @NotNull GitPushRepoResult notPushed(@NotNull GitPushSource source, @NotNull GitRemoteBranch target) {
    return new GitPushRepoResult(Type.NOT_PUSHED, -1, source.getRevision(), target.getFullName(),
                                 target.getRemote().getName(), Collections.emptyList(), null, null);
  }

  static @NotNull GitPushRepoResult tagPushResult(
    @NotNull GitPushNativeResult result, @NotNull GitPushSource.Tag source, @NotNull GitRemoteBranch target
  ) {
    Type resultType = convertType(result);
    List<String> pushedTags;
    if (resultType == Type.NEW_BRANCH) {
      pushedTags = List.of(source.getTag().getFullName());
    } else {
      pushedTags = Collections.emptyList();
    }
    return new GitPushRepoResult(resultType, -1, source.getRevision(),
                                 target.getFullName(), target.getRemote().getName(), pushedTags,
                                 result.getReason(), null);
  }

  static @NotNull GitPushRepoResult addUpdateResult(GitPushRepoResult original, GitUpdateResult updateResult) {
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

  public @NotNull Type getType() {
    return myType;
  }

  @Nullable
  GitUpdateResult getUpdateResult() {
    return myUpdateResult;
  }

  public int getNumberOfPushedCommits() {
    return myCommits;
  }

  /**
   * Returns the branch we were pushing from, in the full-name format, e.g. {@code refs/heads/master} or a revision hash.
   */
  public @NotNull String getSourceBranch() {
    return mySourceBranch;
  }

  /**
   * Returns the branch we were pushing to, in the full-name format, e.g. {@code refs/remotes/origin/master}.
   */
  public @NotNull String getTargetBranch() {
    return myTargetBranch;
  }

  public @NlsSafe @Nullable String getError() {
    return myError;
  }

  @NotNull
  List<@NlsSafe String> getPushedTags() {
    return myPushedTags;
  }

  public @NlsSafe @NotNull String getTargetRemote() {
    return myTargetRemote;
  }

  private static @NotNull Type convertType(@NotNull GitPushNativeResult nativeResult) {
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

  @Override
  public @NonNls String toString() {
    return String.format("%s (%d, '%s'), update: %s}", myType, myCommits, mySourceBranch, myUpdateResult);
  }
}
