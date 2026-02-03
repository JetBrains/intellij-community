// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.ex;

import com.intellij.AbstractBundle;
import com.intellij.configurationStore.SerializableScheme;
import com.intellij.openapi.options.ExternalizableSchemeAdapter;
import com.intellij.openapi.options.SchemeState;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtilRt;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

public final class QuickList extends ExternalizableSchemeAdapter implements SerializableScheme {
  public static final String QUICK_LIST_PREFIX = "QuickList.";
  public static final @NonNls String SEPARATOR_ID = QUICK_LIST_PREFIX + "$Separator$";

  private static final String ID_TAG = "id";
  private static final String ACTION_TAG = "action";
  static final String DISPLAY_NAME_TAG = "display";
  private static final String DESCRIPTION_TAG = "description";

  private String myDescription;
  private String[] myActionIds = ArrayUtilRt.EMPTY_STRING_ARRAY;
  private SchemeState schemeState;
  private @Nullable ResourceBundle myBundle;

  /**
   * With read external to be called immediately after in mind
   */
  QuickList() {
    setName("");
  }

  @SuppressWarnings("CopyConstructorMissesField")
  public QuickList(@NotNull QuickList other) {
    setName(other.getName());
    myDescription = StringUtil.nullize(other.myDescription);
    myBundle = other.myBundle;
    myActionIds = other.getActionIds();
  }

  @Override
  public @Nls @NotNull String getDisplayName() {
    if (myBundle != null) {
      return AbstractBundle.messageOrDefault(myBundle, getActionId() + ".text", getName()); //NON-NLS
    }
    return super.getDisplayName();
  }

  public @Nullable String getDescription() {
    if (StringUtil.isEmpty(myDescription) && myBundle != null) {
      myDescription = AbstractBundle.messageOrNull(myBundle, getActionId() + ".description");
    }
    return myDescription;
  }

  void localizeWithBundle(@Nullable ResourceBundle resourceBundle) {
    myBundle = resourceBundle;
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

  @Override
  public @NotNull String toString() {
    return getName() + " " + getDescription();
  }

  public @NotNull String getActionId() {
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

  @Override
  public @NotNull Element writeScheme() {
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

  @Override
  public @Nullable SchemeState getSchemeState() {
    return schemeState;
  }
}