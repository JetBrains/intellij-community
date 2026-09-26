// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.mq;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;
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

    public @NlsContexts.ColumnName String getColumnName() {
      return HgBundle.message(myId);
    }
  }

  private final @NotNull Map<MqPatchEnum, String> myPatchDetailsPresentationMap = new EnumMap<>(MqPatchEnum.class);

  private final @Nullable String myNodeId;
  private final @Nullable String myParent;
  private final @Nullable Date myDate;
  private final @Nullable VirtualFile myRoot;
  private final @Nullable String myBranch;
  private final @Nullable String myMessage;
  private final @Nullable String myUser;

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

  public @Nullable String getNodeId() {
    return myNodeId;
  }

  public @Nullable String getParent() {
    return myParent;
  }

  public @Nullable Date getDate() {
    return myDate;
  }

  public @Nullable String getBranch() {
    return myBranch;
  }

  public @Nullable String getMessage() {
    return myMessage;
  }

  public @Nullable String getUser() {
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

  public @Nullable String getPresentationDataFor(MqPatchEnum field) {
    return myPatchDetailsPresentationMap.get(field);
  }
}
