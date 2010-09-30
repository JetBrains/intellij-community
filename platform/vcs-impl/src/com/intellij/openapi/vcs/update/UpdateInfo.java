/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.update;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.util.text.DateFormatUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

public class UpdateInfo implements JDOMExternalizable {
  private final Project myProject;
  private UpdatedFiles myUpdatedFiles;
  private String myDate;
  private ActionInfo myActionInfo;
  @NonNls private static final String DATE_ATTR = "date";
  @NonNls private static final String FILE_INFO_ELEMENTS = "UpdatedFiles";
  @NonNls private static final String ACTION_INFO_ATTRIBUTE_NAME = "ActionInfo";

  public UpdateInfo(Project project, UpdatedFiles updatedFiles, ActionInfo actionInfo) {
    myProject = project;
    myActionInfo = actionInfo;
    myUpdatedFiles = updatedFiles;
    myDate = DateFormatUtil.formatPrettyDateTime(Clock.getTime());
  }

  public UpdateInfo(Project project) {
    myProject = project;
  }

  public void writeExternal(Element element) throws WriteExternalException {
    if (myUpdatedFiles == null) return;
    element.setAttribute(DATE_ATTR, myDate);
    element.setAttribute(ACTION_INFO_ATTRIBUTE_NAME, myActionInfo.getActionName());
    Element filesElement = new Element(FILE_INFO_ELEMENTS);
    myUpdatedFiles.writeExternal(filesElement);
    element.addContent(filesElement);
  }

  public void readExternal(Element element) throws InvalidDataException {
    myDate = element.getAttributeValue(DATE_ATTR);
    Element fileInfoElement = element.getChild(FILE_INFO_ELEMENTS);
    if (fileInfoElement == null) return;

    String actionInfoName = element.getAttributeValue(ACTION_INFO_ATTRIBUTE_NAME);

    myActionInfo = getActionInfoByName(actionInfoName);
    if (myActionInfo == null) return;

    UpdatedFiles updatedFiles = UpdatedFiles.create();
    updatedFiles.readExternal(fileInfoElement);
    myUpdatedFiles = updatedFiles;

  }

  private ActionInfo getActionInfoByName(String actionInfoName) {
    if (ActionInfo.UPDATE.getActionName().equals(actionInfoName)) return ActionInfo.UPDATE;
    if (ActionInfo.STATUS.getActionName().equals(actionInfoName)) return ActionInfo.STATUS;
    return null;
  }

  public String getHelpId() {
    return null;
  }

  public Project getPoject() {
    return myProject;
  }

  public UpdatedFiles getFileInformation() {
    return myUpdatedFiles;
  }

  public String getCaption() {
    return VcsBundle.message("toolwindow.title.update.project", myDate);
  }

  public boolean isEmpty() {
    if (myUpdatedFiles != null) {
      return myUpdatedFiles.isEmpty();
    } else {
      return true;
    }    
  }

  public ActionInfo getActionInfo() {
    return myActionInfo;
  }
}