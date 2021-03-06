// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.mq;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.*;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.log.HgBaseLogParser;

import java.util.Date;
import java.util.EnumMap;
import java.util.Map;

@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class MqPatchDetails {

  public enum MqPatchEnum {
    Name("column.mq.patch.name"),
    Subject("column.mq.patch.subject"),
    Author("column.mq.patch.author"),
    Branch("column.mq.patch.branch"),
    Date("column.mq.patch.date");

    private final String myId;

    MqPatchEnum(@NotNull @NonNls @PropertyKey(resourceBundle = HgBundle.BUNDLE) String id) {
      myId = id;
    }

    @NlsContexts.ColumnName
    public String getColumnName() {
      return HgBundle.message(myId);
    }
  }

  @NotNull private final Map<MqPatchEnum, String> myPatchDetailsPresentationMap = new EnumMap<>(MqPatchEnum.class);

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
