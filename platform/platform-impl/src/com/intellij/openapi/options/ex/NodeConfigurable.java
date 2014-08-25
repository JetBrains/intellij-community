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
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public final class NodeConfigurable extends SearchableConfigurable.Parent.Abstract {
  private final ArrayList<Configurable> myConfigurables = new ArrayList<Configurable>();
  private final String myId;
  private final int myWeight;

  public NodeConfigurable(@NotNull String id, int weight) {
    myId = id;
    myWeight = weight;
  }

  public int getGroupWeight() {
    return myWeight;
  }

  public void add(Configurable configurable) {
    if (configurable != null) {
      super.disposeUIResources();
      myConfigurables.add(configurable);
    }
  }

  @Override
  public void disposeUIResources() {
    super.disposeUIResources();
    myConfigurables.clear();
  }

  @NotNull
  @Override
  public String getId() {
    return "node.configurable." + myId;
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return "node.configurable." + myId + ".help.topic";
  }

  @Nls
  @Override
  public String getDisplayName() {
    return OptionsBundle.message("node.configurable." + myId + ".display.name");
  }

  @Override
  protected Configurable[] buildConfigurables() {
    int size = myConfigurables.size();
    return size == 0 ? null : myConfigurables.toArray(new Configurable[size]);
  }
}
