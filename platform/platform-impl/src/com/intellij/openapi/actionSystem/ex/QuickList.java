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
package com.intellij.openapi.actionSystem.ex;


import com.intellij.openapi.options.ExternalInfo;
import com.intellij.openapi.options.ExternalizableScheme;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;



public class QuickList implements ExternalizableScheme {

  @NonNls public static final String QUICK_LIST_PREFIX = "QuickList.";

  @NonNls public static final String SEPARATOR_ID = QUICK_LIST_PREFIX + "$Separator$";



  @NonNls private static final String ID_TAG = "id";

  @NonNls private static final String READONLY_TAG = "readonly";

  @NonNls private static final String ACTION_TAG = "action";

  @NonNls private static final String DISPLAY_NAME_TAG = "display";

  @NonNls private static final String DESCRIPTION_TAG = "description";





  private String myDisplayName;

  private String myDescription;

  private String[] myActionIds;

  private boolean myReadonly;

  private final ExternalInfo myExternalInfo = new ExternalInfo();



  /**

   * With read external to be called immediately after in mind

   */

  QuickList() {}



  public QuickList(String displayName, String description, String[] actionIds, boolean isReadonly) {

    myDisplayName = displayName == null ? "" : displayName;

    myDescription = description == null ? "" : description;

    myActionIds = actionIds;

    myReadonly = isReadonly;

  }





  public String getDisplayName() {

    return myDisplayName;

  }



  public String getName() {

    return getDisplayName();

  }



  public boolean isReadonly() {

    return myReadonly;

  }



  public String getDescription() {

    return myDescription;

  }



  public String[] getActionIds() {

    return myActionIds;

  }



  public boolean equals(Object o) {

    if (this == o) return true;

    if (!(o instanceof QuickList)) return false;



    final QuickList quickList = (QuickList)o;



    if (!Arrays.equals(myActionIds, quickList.myActionIds)) return false;

    if (!myDescription.equals(quickList.myDescription)) return false;

    if (!myDisplayName.equals(quickList.myDisplayName)) return false;



    return true;

  }



  public int hashCode() {

    return 29 * myDisplayName.hashCode() + myDescription.hashCode();



  }



  public String getActionId() {

    return QUICK_LIST_PREFIX + getDisplayName();

  }



  public void writeExternal(Element groupElement) {

    groupElement.setAttribute(DISPLAY_NAME_TAG, getDisplayName());

    groupElement.setAttribute(DESCRIPTION_TAG, getDescription());

    groupElement.setAttribute(READONLY_TAG, String.valueOf(isReadonly()));



    for (String actionId : getActionIds()) {

      Element actionElement = new Element(ACTION_TAG);

      actionElement.setAttribute(ID_TAG, actionId);

      groupElement.addContent(actionElement);

    }

  }



  public void readExternal(Element element) {

    myDisplayName = element.getAttributeValue(DISPLAY_NAME_TAG);

    myDescription = element.getAttributeValue(DESCRIPTION_TAG);

    myReadonly = Boolean.valueOf(element.getAttributeValue(READONLY_TAG)).booleanValue();

    List<String> ids = new ArrayList<String>();

    for (Object action : element.getChildren(ACTION_TAG)) {

      Element actionElement = (Element)action;

      ids.add(actionElement.getAttributeValue(ID_TAG));

    }

    myActionIds = ArrayUtil.toStringArray(ids);

  }



  public void setDisplayName(final String name) {

    myDisplayName = name;

  }



  @NotNull

  public ExternalInfo getExternalInfo() {

    return myExternalInfo;

  }



  public void setName(final String newName) {

    setDisplayName(newName);

  }

}
