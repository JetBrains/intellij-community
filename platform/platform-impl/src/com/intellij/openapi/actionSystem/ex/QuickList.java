// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.ex;

import com.intellij.configurationStore.SerializableScheme;
import com.intellij.openapi.options.ExternalizableSchemeAdapter;
import com.intellij.openapi.options.SchemeState;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtilRt;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class QuickList extends ExternalizableSchemeAdapter implements SerializableScheme {
  public static final String QUICK_LIST_PREFIX = "QuickList.";
  @NonNls public static final String SEPARATOR_ID = QUICK_LIST_PREFIX + "$Separator$";

  private static final String ID_TAG = "id";
  private static final String ACTION_TAG = "action";
  static final String DISPLAY_NAME_TAG = "display";
  private static final String DESCRIPTION_TAG = "description";

  private String myDescription;
  private String[] myActionIds = ArrayUtilRt.EMPTY_STRING_ARRAY;
  private SchemeState schemeState;

  /**
   * With read external to be called immediately after in mind
   */
  QuickList() {
    setName("");
  }

  public QuickList(@NotNull String name, @Nullable String description, String[] actionIds) {
    setName(name);
    myDescription = StringUtil.nullize(description);
    myActionIds = actionIds;
  }

  @Nullable
  public String getDescription() {
    return myDescription;
  }

  public void setDescription(@Nullable String value) {
    myDescription = StringUtil.nullize(value);
    schemeState = SchemeState.POSSIBLY_CHANGED;
  }

  public String[] getActionIds() {
    return myActionIds;
  }

  public void setActionIds(String @NotNull [] value) {
    myActionIds = value;
    schemeState = SchemeState.POSSIBLY_CHANGED;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof QuickList quickList)) {
      return false;
    }

    return Arrays.equals(myActionIds, quickList.myActionIds) && Comparing.strEqual(myDescription, quickList.myDescription) && getName().equals(quickList.getName());
  }

  @Override
  public int hashCode() {
    return 29 * getName().hashCode() + Comparing.hashcode(myDescription);
  }

  @NotNull
  @Override
  public String toString() {
    return getName() + " " + getDescription();
  }

  @NotNull
  public String getActionId() {
    return QUICK_LIST_PREFIX + getName();
  }

  public void readExternal(@NotNull Element element) {
    setName(element.getAttributeValue(DISPLAY_NAME_TAG));
    myDescription = StringUtil.nullize(element.getAttributeValue(DESCRIPTION_TAG));

    List<Element> actionElements = element.getChildren(ACTION_TAG);
    myActionIds = new String[actionElements.size()];
    for (int i = 0, n = actionElements.size(); i < n; i++) {
      myActionIds[i] = actionElements.get(i).getAttributeValue(ID_TAG);
    }
  }

  @NotNull
  @Override
  public Element writeScheme() {
    Element element = new Element("list");
    element.setAttribute(DISPLAY_NAME_TAG, getName());
    if (myDescription != null) {
      element.setAttribute(DESCRIPTION_TAG, myDescription);
    }

    for (String actionId : getActionIds()) {
      element.addContent(new Element(ACTION_TAG).setAttribute(ID_TAG, actionId));
    }

    schemeState = SchemeState.UNCHANGED;
    return element;
  }

  @Nullable
  @Override
  public SchemeState getSchemeState() {
    return schemeState;
  }
}