// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.ex;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SortedConfigurableGroup
  extends SearchableConfigurable.Parent.Abstract
  implements SearchableConfigurable, Weighted, ConfigurableGroup, Configurable.NoScroll {

  private final String myId;
  private final @NlsContexts.ConfigurableName String myDisplayName;
  private final @NlsContexts.DetailedDescription String myDescription;
  private final String myHelpTopic;
  int myWeight; // see ConfigurableExtensionPointUtil.getConfigurableToReplace

  List<Configurable> myList = new ArrayList<>();

  public SortedConfigurableGroup(@NonNls @NotNull String id,
                                 @NlsContexts.ConfigurableName @NotNull String displayName,
                                 @NlsContexts.DetailedDescription @Nullable String description,
                                 @NonNls @Nullable String helpTopic,
                                 int weight) {
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

  @Override
  public @NonNls @NotNull String getId() {
    return myId;
  }

  @Override
  public @NonNls @Nullable String getHelpTopic() {
    return myHelpTopic;
  }

  @Override
  public int getWeight() {
    return myWeight;
  }

  @Override
  public @NlsContexts.ConfigurableName String getDisplayName() {
    return myDisplayName;
  }

  public @NlsContexts.DetailedDescription String getDescription() {
    return myDescription;
  }
}
