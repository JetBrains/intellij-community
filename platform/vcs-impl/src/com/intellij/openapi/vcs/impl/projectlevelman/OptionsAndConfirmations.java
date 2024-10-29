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

import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@ApiStatus.Internal
public class OptionsAndConfirmations {
  private final Map<@NonNls String, Boolean> myOptionsValues = new HashMap<>();
  private final Map<@NonNls String, VcsShowConfirmationOption.Value> myConfirmationsValues = new HashMap<>();

  private final Map<@NonNls String, PersistentVcsShowSettingOption> myOptions;
  private final Map<@NonNls String, PersistentVcsShowConfirmationOption> myConfirmations;

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

  private void createSettingFor(final VcsConfiguration.StandardOption option) {
    myOptions.put(option.getId(), new PersistentVcsShowSettingOptionImpl(option, this));
  }

  private void createConfirmationFor(final VcsConfiguration.StandardConfirmation confirmation) {
    myConfirmations.put(confirmation.getId(), new PersistentVcsShowConfirmationOptionImpl(confirmation, this));
  }

  @NotNull
  public PersistentVcsShowConfirmationOption getConfirmation(@NotNull VcsConfiguration.StandardConfirmation option) {
    return myConfirmations.get(option.getId());
  }

  @NotNull
  public PersistentVcsShowSettingOption getOption(@NotNull VcsConfiguration.StandardOption option) {
    return myOptions.get(option.getId());
  }

  public List<PersistentVcsShowSettingOption> getAllOptions() {
    return new ArrayList<>(myOptions.values());
  }

  public List<PersistentVcsShowConfirmationOption> getAllConfirmations() {
    return new ArrayList<>(myConfirmations.values());
  }

  @NotNull
  public PersistentVcsShowSettingOption getOrCreateCustomOption(@NotNull String vcsActionName, @NotNull AbstractVcs vcs) {
    return myOptions.computeIfAbsent(vcsActionName, key -> new LegacyVcsShowOptionsSettingImpl(vcsActionName, this));
  }


  void setOptionValue(@NonNls @NotNull String id, boolean value) {
    myOptionsValues.put(id, value);
  }

  void setConfirmationValue(@NonNls @NotNull String id, @NonNls VcsShowConfirmationOption.Value value) {
    myConfirmationsValues.put(id, value);
  }

  boolean getOptionValue(@NonNls @NotNull String id) {
    Boolean value = myOptionsValues.get(id);
    if (value != null) return value;
    return true;
  }

  @NotNull
  VcsShowConfirmationOption.Value getConfirmationValue(@NonNls @NotNull String id) {
    VcsShowConfirmationOption.Value value = myConfirmationsValues.get(id);
    if (value != null) return value;
    return VcsShowConfirmationOption.Value.SHOW_CONFIRMATION;
  }

  Map<String, Boolean> getOptionsValues() {
    return myOptionsValues;
  }

  Map<String, VcsShowConfirmationOption.Value> getConfirmationsValues() {
    return myConfirmationsValues;
  }
}
