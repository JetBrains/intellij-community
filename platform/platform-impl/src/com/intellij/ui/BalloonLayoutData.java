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
package com.intellij.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexander Lobas
 */
public class BalloonLayoutData {
  public String groupId;
  public String id;
  public MergeInfo mergeData;

  public boolean showFullContent;

  public int height;
  public int twoLineHeight;
  public int fullHeight;
  public int maxScrollHeight;

  public boolean showMinSize;

  public Runnable closeAll;
  public Runnable doLayout;

  public boolean showSettingButton = true;
  public Computable<Boolean> showActions;

  public Project project;

  public BalloonLayoutConfiguration configuration;

  public Runnable lafHandler;

  @NotNull
  public MergeInfo merge() {
    return mergeData == null ? new MergeInfo(id, 1) : new MergeInfo(mergeData.linkId, mergeData.count + 1);
  }

  public static class MergeInfo {
    public String linkId;
    public int count;

    public MergeInfo(@NotNull String linkId, int count) {
      this.linkId = linkId;
      this.count = count;
    }
  }
}