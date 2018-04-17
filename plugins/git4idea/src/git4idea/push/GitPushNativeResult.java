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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Push result as reported by {@code git push} command.
 *
 * @see GitPushNativeResultParser
 * @see GitPushRepoResult
 */
public class GitPushNativeResult {

  public static final String NO_FF_REJECT_REASON = "non-fast-forward";
  static final String FETCH_FIRST_REASON = "fetch first";

  public enum Type {
    SUCCESS,
    FORCED_UPDATE,
    NEW_REF,
    REJECTED,
    DELETED,
    UP_TO_DATE,
    ERROR
  }

  @NotNull private final Type myType;
  private final String mySourceRef;
  @Nullable private final String myReason;
  @Nullable private final String myRange;

  public GitPushNativeResult(@NotNull Type type, String sourceRef) {
    this(type, sourceRef, null, null);
  }

  public GitPushNativeResult(@NotNull Type type, String sourceRef, @Nullable String reason, @Nullable String range) {
    myType = type;
    mySourceRef = sourceRef;
    myReason = reason;
    myRange = range;
  }

  @NotNull
  public Type getType() {
    return myType;
  }

  @Nullable
  public String getRange() {
    return myRange;
  }

  public String getSourceRef() {
    return mySourceRef;
  }

  @Nullable
  public String getReason() {
    return myReason;
  }

  boolean isNonFFUpdate() {
    return myType == Type.REJECTED && (NO_FF_REJECT_REASON.equals(myReason) || FETCH_FIRST_REASON.equals(myReason));
  }

  @Override
  public String toString() {
    return String.format("%s: '%s', '%s', '%s'", myType, mySourceRef, myRange, myReason);
  }
}
