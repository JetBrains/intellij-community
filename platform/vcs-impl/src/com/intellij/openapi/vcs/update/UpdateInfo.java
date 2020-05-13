// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.update;

import com.intellij.openapi.util.Clock;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.util.text.DateFormatUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

final class UpdateInfo {
  private UpdatedFiles myUpdatedFiles;
  private String myDate;
  private ActionInfo myActionInfo;
  @NonNls private static final String DATE_ATTR = "date";
  @NonNls private static final String FILE_INFO_ELEMENTS = "UpdatedFiles";
  @NonNls private static final String ACTION_INFO_ATTRIBUTE_NAME = "ActionInfo";

  UpdateInfo(UpdatedFiles updatedFiles, ActionInfo actionInfo) {
    myActionInfo = actionInfo;
    myUpdatedFiles = updatedFiles;
    myDate = DateFormatUtil.formatPrettyDateTime(Clock.getTime());
  }

  UpdateInfo() {
  }

  public void writeExternal(@NotNull Element element) {
    if (myUpdatedFiles == null) {
      return;
    }

    element.setAttribute(DATE_ATTR, myDate);
    element.setAttribute(ACTION_INFO_ATTRIBUTE_NAME, myActionInfo.getActionName());
    Element filesElement = new Element(FILE_INFO_ELEMENTS);
    myUpdatedFiles.writeExternal(filesElement);
    element.addContent(filesElement);
  }

  public void readExternal(@NotNull Element element) {
    myDate = element.getAttributeValue(DATE_ATTR);
    Element fileInfoElement = element.getChild(FILE_INFO_ELEMENTS);
    if (fileInfoElement == null) {
      return;
    }

    String actionInfoName = element.getAttributeValue(ACTION_INFO_ATTRIBUTE_NAME);

    myActionInfo = getActionInfoByName(actionInfoName);
    if (myActionInfo == null) return;

    UpdatedFiles updatedFiles = UpdatedFiles.create();
    updatedFiles.readExternal(fileInfoElement);
    myUpdatedFiles = updatedFiles;
  }

  private static ActionInfo getActionInfoByName(String actionInfoName) {
    if (ActionInfo.UPDATE.getActionName().equals(actionInfoName)) return ActionInfo.UPDATE;
    if (ActionInfo.STATUS.getActionName().equals(actionInfoName)) return ActionInfo.STATUS;
    return null;
  }

  public UpdatedFiles getFileInformation() {
    return myUpdatedFiles;
  }

  public String getCaption() {
    return VcsBundle.message("toolwindow.title.update.project", myDate);
  }

  public boolean isEmpty() {
    return myUpdatedFiles == null || myUpdatedFiles.isEmpty();
  }

  public ActionInfo getActionInfo() {
    return myActionInfo;
  }
}