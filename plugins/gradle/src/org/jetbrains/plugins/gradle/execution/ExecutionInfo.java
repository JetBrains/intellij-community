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
package org.jetbrains.plugins.gradle.execution;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 * @since 12/1/2015
 */
public class ExecutionInfo {
  private final @Nullable String id;
  private String myDisplayName;
  private long startTime;
  private long endTime;
  private boolean isFailed;
  private boolean isSkipped;
  private boolean isUpToDate;

  public ExecutionInfo(@Nullable String id, String displayName) {
    this.id = id;
    this.myDisplayName = displayName;
  }

  @Nullable
  public String getId() {
    return id;
  }

  public String getDisplayName() {
    return myDisplayName;
  }

  public void setDisplayName(String displayName) {
    this.myDisplayName = displayName;
  }

  public long getStartTime() {
    return startTime;
  }

  public void setStartTime(long startTime) {
    this.startTime = startTime;
  }

  public long getEndTime() {
    return endTime;
  }

  public void setEndTime(long endTime) {
    this.endTime = endTime;
  }

  public boolean isFailed() {
    return isFailed;
  }

  public void setFailed(boolean failed) {
    isFailed = failed;
  }

  public boolean isSkipped() {
    return isSkipped;
  }

  public void setSkipped(boolean skipped) {
    isSkipped = skipped;
  }

  public boolean isUpToDate() {
    return isUpToDate;
  }

  public void setUpToDate(boolean upToDate) {
    isUpToDate = upToDate;
  }

  public boolean isRunning() {
    return endTime <= 0 && !isSkipped && !isFailed;
  }

  @Override
  public String toString() {
    return myDisplayName;
  }
}
