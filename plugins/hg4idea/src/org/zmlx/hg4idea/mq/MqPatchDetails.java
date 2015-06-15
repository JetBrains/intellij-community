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
package org.zmlx.hg4idea.mq;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.log.HgBaseLogParser;

import java.util.Date;
import java.util.Map;

@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class MqPatchDetails {

  public enum MqPatchEnum {Name, Subject, Author, Branch, Date}

  @NotNull private final Map<MqPatchEnum, String> myPatchDetailsPresentationMap = ContainerUtil.newEnumMap(MqPatchEnum.class);

  @Nullable private final String myNodeId;
  @Nullable private final String myParent;
  @Nullable private final Date myDate;
  @Nullable private final VirtualFile myRoot;
  @Nullable private final String myBranch;
  @Nullable private final String myMessage;
  @Nullable private final String myUser;

  public static final MqPatchDetails EMPTY_PATCH_DETAILS = new MqPatchDetails(null, null, null, null, null, null, null);

  public MqPatchDetails(@Nullable String nodeId,
                        @Nullable String parent,
                        @Nullable Date date,
                        @Nullable VirtualFile root,
                        @Nullable String branch,
                        @Nullable String message,
                        @Nullable String user) {
    myNodeId = nodeId;
    myParent = parent;
    myDate = date;
    myRoot = root;
    myBranch = branch;
    myMessage = message != null ? message.trim() : null;
    myUser = user;
    createPresentationModel();
  }

  @Nullable
  public String getNodeId() {
    return myNodeId;
  }

  @Nullable
  public String getParent() {
    return myParent;
  }

  @Nullable
  public Date getDate() {
    return myDate;
  }

  @Nullable
  public String getBranch() {
    return myBranch;
  }

  @Nullable
  public String getMessage() {
    return myMessage;
  }

  @Nullable
  public String getUser() {
    return myUser;
  }

  private void createPresentationModel() {
    if (myDate != null) {
      myPatchDetailsPresentationMap.put(MqPatchEnum.Date, DateFormatUtil.formatDateTime(myDate));
    }
    myPatchDetailsPresentationMap.put(MqPatchEnum.Author, myUser);
    myPatchDetailsPresentationMap.put(MqPatchEnum.Branch, myBranch);
    if (myMessage != null) {
      myPatchDetailsPresentationMap.put(MqPatchEnum.Subject, HgBaseLogParser.extractSubject(myMessage));
    }
  }

  @Nullable
  public String getPresentationDataFor(MqPatchEnum field) {
    return myPatchDetailsPresentationMap.get(field);
  }
}
