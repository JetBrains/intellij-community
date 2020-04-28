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

import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class BalloonLayoutData {
  public String groupId;
  public String id;
  @Nullable public String displayId;
  public MergeInfo mergeData;

  public boolean showFullContent;

  public boolean welcomeScreen;
  public NotificationType type;

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

  public long fadeoutTime;

  public Color textColor;
  public Color fillColor;
  public Color borderColor;

  public boolean isExpandable;


  @NotNull
  public static BalloonLayoutData createEmpty() {
    BalloonLayoutData layoutData = new BalloonLayoutData();
    layoutData.groupId = "";
    layoutData.showSettingButton = false;
    return layoutData;
  }

  @NotNull
  public static Ref<BalloonLayoutData> fullContent() {
    BalloonLayoutData layoutData = createEmpty();
    layoutData.showFullContent = true;
    return new Ref<>(layoutData);
  }

  @NotNull
  public MergeInfo merge() {
    return new MergeInfo(mergeData, new ID(id, displayId));
  }

  @NotNull
  public List<String> getMergeIds() {
    List<ID> linkIds = mergeData.linkIds;
    List<String> ids = new ArrayList<>(linkIds.size());
    for (ID linkId : linkIds) {
      ids.add(linkId.notificationId);
    }
    ids.add(id);
    return ids;
  }

  public static class ID {
    @NotNull final String notificationId;
    @Nullable final String notificationDisplayId;

    public ID(@NotNull String notificationId, @Nullable String notificationDisplayId) {
      this.notificationId = notificationId;
      this.notificationDisplayId = notificationDisplayId;
    }
  }

  public static class MergeInfo {
    public List<ID> linkIds;
    public int count;

    public MergeInfo(@Nullable MergeInfo info, @NotNull ID linkId) {
      if (info == null) {
        linkIds = new ArrayList<>();
        count = 1;
      }
      else {
        linkIds = info.linkIds;
        count = info.count + 1;
      }
      linkIds.add(linkId);
    }
  }
}