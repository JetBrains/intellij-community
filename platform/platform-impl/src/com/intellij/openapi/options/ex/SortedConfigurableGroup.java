/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.options.ex;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Sergey.Malenkov
 */
public final class SortedConfigurableGroup
  extends SearchableConfigurable.Parent.Abstract
  implements SearchableConfigurable, Weighted, ConfigurableGroup, Configurable.NoScroll {

  private final String myId;
  private final String myDisplayName;
  private final String myHelpTopic;
  int myWeight; // see ConfigurableExtensionPointUtil.getConfigurableToReplace

  List<Configurable> myList = ContainerUtil.newArrayList();

  SortedConfigurableGroup(String id, String displayName, String helpTopic, int weight) {
    myId = id;
    myDisplayName = displayName;
    myHelpTopic = helpTopic;
    myWeight = weight;
  }

  @Override
  protected Configurable[] buildConfigurables() {
    Collections.sort(myList, COMPARATOR);
    Configurable[] result = ArrayUtil.toObjectArray(myList, Configurable.class);
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
}
