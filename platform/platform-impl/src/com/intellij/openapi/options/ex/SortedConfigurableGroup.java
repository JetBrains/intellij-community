// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.ex;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class SortedConfigurableGroup
  extends SearchableConfigurable.Parent.Abstract
  implements SearchableConfigurable, Weighted, ConfigurableGroup, Configurable.NoScroll {

  private final String myId;
  private final String myDisplayName;
  private final String myDescription;
  private final String myHelpTopic;
  int myWeight; // see ConfigurableExtensionPointUtil.getConfigurableToReplace

  List<Configurable> myList = new ArrayList<>();

  SortedConfigurableGroup(String id, String displayName, String description, String helpTopic, int weight) {
    myId = id;
    myDisplayName = displayName;
    myDescription = description;
    myHelpTopic = helpTopic;
    myWeight = weight;
  }

  @Override
  protected Configurable[] buildConfigurables() {
    myList.sort(COMPARATOR);
    Configurable[] result = myList.toArray(new Configurable[0]);
    myList.clear();
    myList = null;
    return result;
  }

  @NotNull
  @Override
  public String getId() {
    return myId;
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return myHelpTopic;
  }

  @Override
  public int getWeight() {
    return myWeight;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return myDisplayName;
  }

  public String getDescription() {
    return myDescription;
  }
}
