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
package com.intellij.openapi.actionSystem.ex;

import com.intellij.openapi.options.ExternalizableSchemeAdapter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class QuickList extends ExternalizableSchemeAdapter {
  public static final String QUICK_LIST_PREFIX = "QuickList.";
  public static final String SEPARATOR_ID = QUICK_LIST_PREFIX + "$Separator$";

  private static final String ID_TAG = "id";
  private static final String ACTION_TAG = "action";
  private static final String DISPLAY_NAME_TAG = "display";
  private static final String DESCRIPTION_TAG = "description";

  private String myDescription;
  private String[] myActionIds = ArrayUtil.EMPTY_STRING_ARRAY;

  /**
   * With read external to be called immediately after in mind
   */
  QuickList() {
    myName = "";
  }

  public QuickList(@NotNull String name, @Nullable String description, String[] actionIds) {
    myName = name;
    myDescription = StringUtil.nullize(description);
    myActionIds = actionIds;
  }

  @Nullable
  public String getDescription() {
    return myDescription;
  }

  public void setDescription(@Nullable String value) {
    myDescription = StringUtil.nullize(value);
  }

  public String[] getActionIds() {
    return myActionIds;
  }

  public void setActionIds(@NotNull String[] value) {
    myActionIds = value;
  }

  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof QuickList)) {
      return false;
    }

    QuickList quickList = (QuickList)o;
    return Arrays.equals(myActionIds, quickList.myActionIds) && Comparing.strEqual(myDescription, quickList.myDescription) && myName.equals(quickList.myName);
  }

  public int hashCode() {
    return 29 * myName.hashCode() + Comparing.hashcode(myDescription);
  }

  @Override
  public String toString() {
    return getName() + " " + getDescription();
  }

  @NotNull
  public String getActionId() {
    return QUICK_LIST_PREFIX + getName();
  }

  public void writeExternal(@NotNull Element groupElement) {
    groupElement.setAttribute(DISPLAY_NAME_TAG, myName);
    if (myDescription != null) {
      groupElement.setAttribute(DESCRIPTION_TAG, myDescription);
    }

    for (String actionId : getActionIds()) {
      groupElement.addContent(new Element(ACTION_TAG).setAttribute(ID_TAG, actionId));
    }
  }

  public void readExternal(@NotNull Element element) {
    myName = element.getAttributeValue(DISPLAY_NAME_TAG);
    myDescription = StringUtil.nullize(element.getAttributeValue(DESCRIPTION_TAG));

    List<Element> actionElements = element.getChildren(ACTION_TAG);
    myActionIds = new String[actionElements.size()];
    for (int i = 0, n = actionElements.size(); i < n; i++) {
      myActionIds[i] = actionElements.get(i).getAttributeValue(ID_TAG);
    }
  }
}