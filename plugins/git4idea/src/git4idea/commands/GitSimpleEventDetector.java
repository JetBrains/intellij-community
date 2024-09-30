// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * @author Kirill Likhodedov
 */
public class GitSimpleEventDetector implements GitLineEventDetector {

  private final @NotNull Event myEvent;
  private boolean myHappened;

  public enum Event {
    CHERRY_PICK_CONFLICT("after resolving the conflicts"), // also applies to revert
    LOCAL_CHANGES_OVERWRITTEN_BY_CHERRY_PICK("would be overwritten by merge"), // also applies to revert
    UNMERGED_PREVENTING_CHECKOUT("you need to resolve your current index first"),
    UNMERGED_PREVENTING_MERGE("is not possible because you have unmerged files"),
    BRANCH_NOT_FULLY_MERGED("is not fully merged"),
    MERGE_CONFLICT("Automatic merge failed; fix conflicts and then commit the result"),
    MERGE_CONFLICT_ON_UNSTASH("conflict"),
    INDEX_CONFLICT_ON_UNSTASH("conflicts in index"),
    ALREADY_UP_TO_DATE("Already up-to-date", "Already up to date"),
    INVALID_REFERENCE("invalid reference:");

    private final List<String> myDetectionStrings;

    Event(@NonNls String @NotNull ... detectionStrings) {
      myDetectionStrings = Arrays.asList(detectionStrings);
    }

    boolean matches(@NotNull @NonNls String line) {
      return ContainerUtil.exists(myDetectionStrings, s -> StringUtil.containsIgnoreCase(line, s));
    }
  }

  public GitSimpleEventDetector(@NotNull Event event) {
    myEvent = event;
  }

  @Override
  public void onLineAvailable(@NotNull String line, Key outputType) {
    if (myEvent.matches(line)) {
      myHappened = true;
    }
  }

  /**
   * @deprecated replaced with {@link #isDetected()}
   */
  @Deprecated
  public boolean hasHappened() {
    return isDetected();
  }

  @Override
  public boolean isDetected() {
    return myHappened;
  }
}
