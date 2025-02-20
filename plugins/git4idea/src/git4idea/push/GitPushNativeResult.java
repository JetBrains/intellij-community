// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.push;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Push result as reported by {@code git push} command.
 *
 * @see GitPushNativeResultParser
 * @see GitPushRepoResult
 */
public final class GitPushNativeResult {
  public static final String NO_FF_REJECT_REASON = "non-fast-forward";
  public static final String FETCH_FIRST_REASON = "fetch first";
  static final String STALE_INFO_REASON = "stale info";
  static final String FAILED_LOCK_REASON = "failed to lock";

  public enum Type {
    SUCCESS,
    FORCED_UPDATE,
    NEW_REF,
    REJECTED,
    DELETED,
    UP_TO_DATE,
    ERROR
  }

  private final @NotNull Type myType;
  private final String mySourceRef;
  private final @Nullable String myReason;
  private final @Nullable String myRange;

  public GitPushNativeResult(@NotNull Type type, String sourceRef) {
    this(type, sourceRef, null, null);
  }

  public GitPushNativeResult(@NotNull Type type, String sourceRef, @Nullable String reason, @Nullable String range) {
    myType = type;
    mySourceRef = sourceRef;
    myReason = reason;
    myRange = range;
  }

  public @NotNull Type getType() {
    return myType;
  }

  public @Nullable String getRange() {
    return myRange;
  }

  public String getSourceRef() {
    return mySourceRef;
  }

  public @Nullable String getReason() {
    return myReason;
  }

  boolean isNonFFUpdate() {
    if (myReason == null || myType != Type.REJECTED) return false;
    return StringUtil.containsIgnoreCase(myReason, NO_FF_REJECT_REASON) ||
           StringUtil.containsIgnoreCase(myReason, FETCH_FIRST_REASON) ||
           StringUtil.containsIgnoreCase(myReason, FAILED_LOCK_REASON);
  }

  boolean isStaleInfo() {
    return myType == Type.REJECTED && myReason != null &&
           StringUtil.containsIgnoreCase(myReason, STALE_INFO_REASON);
  }

  @Override
  public String toString() {
    return String.format("%s: '%s', '%s', '%s'", myType, mySourceRef, myRange, myReason);
  }
}
