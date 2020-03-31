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
package com.intellij.openapi.vcs.impl.projectlevelman;

import com.intellij.openapi.vcs.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OptionsAndConfirmations {
  private final Map<String, VcsShowOptionsSettingImpl> myOptions;
  private final Map<String, VcsShowConfirmationOptionImpl> myConfirmations;

  public OptionsAndConfirmations() {
    myOptions = new LinkedHashMap<>();
    myConfirmations = new LinkedHashMap<>();

    createSettingFor(VcsConfiguration.StandardOption.ADD);
    createSettingFor(VcsConfiguration.StandardOption.REMOVE);
    createSettingFor(VcsConfiguration.StandardOption.CHECKOUT);
    createSettingFor(VcsConfiguration.StandardOption.UPDATE);
    createSettingFor(VcsConfiguration.StandardOption.STATUS);
    createSettingFor(VcsConfiguration.StandardOption.EDIT);

    createConfirmationFor(VcsConfiguration.StandardConfirmation.ADD);
    createConfirmationFor(VcsConfiguration.StandardConfirmation.REMOVE);
  }

  @NotNull
  public VcsShowConfirmationOptionImpl getConfirmation(VcsConfiguration.StandardConfirmation option) {
    return myConfirmations.get(option.getId());
  }

  private void createSettingFor(final VcsConfiguration.StandardOption option) {
    getOrCreateOption(option.getId());
  }

  private void createConfirmationFor(final VcsConfiguration.StandardConfirmation confirmation) {
    String id = confirmation.getId();
    myConfirmations.put(id, new VcsShowConfirmationOptionImpl(id));
  }

  @NotNull
  public VcsShowSettingOption getOptions(VcsConfiguration.StandardOption option) {
    return myOptions.get(option.getId());
  }

  public List<VcsShowOptionsSettingImpl> getAllOptions() {
    return new ArrayList<>(myOptions.values());
  }

  public List<VcsShowConfirmationOptionImpl> getAllConfirmations() {
    return new ArrayList<>(myConfirmations.values());
  }

  @NotNull
  public VcsShowSettingOption getOrCreateCustomOption(@NotNull String vcsActionName, @NotNull AbstractVcs vcs) {
    final VcsShowOptionsSettingImpl option = getOrCreateOption(vcsActionName);
    option.addApplicableVcs(vcs);
    return option;
  }

  @NotNull
  VcsShowOptionsSettingImpl getOrCreateOption(String actionName) {
    if (!myOptions.containsKey(actionName)) {
      myOptions.put(actionName, new VcsShowOptionsSettingImpl(actionName));
    }
    return myOptions.get(actionName);
  }

  @Nullable
  VcsShowConfirmationOptionImpl getConfirmation(String id) {
    return myConfirmations.get(id);
  }

  // open for serialization purposes
  Map<String, VcsShowOptionsSettingImpl> getOptions() {
    return myOptions;
  }

  // open for serialization purposes
  Map<String, VcsShowConfirmationOptionImpl> getConfirmations() {
    return myConfirmations;
  }
}
