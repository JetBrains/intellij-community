// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.newEditor;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.MultiValuesMap;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

public class OptionsEditorContext {
  CopyOnWriteArraySet<OptionsEditorColleague> myColleagues = new CopyOnWriteArraySet<>();

  Configurable myCurrentConfigurable;
  Set<Configurable> myModified = new CopyOnWriteArraySet<>();
  Map<Configurable, ConfigurationException> myErrors = new THashMap<>();
  private boolean myHoldingFilter;
  private final Map<Configurable,  Configurable> myConfigurableToParentMap = new HashMap<>();
  private final MultiValuesMap<Configurable, Configurable> myParentToChildrenMap = new MultiValuesMap<>();

  ActionCallback fireSelected(@Nullable final Configurable configurable, @NotNull OptionsEditorColleague requestor) {
    if (myCurrentConfigurable == configurable) return ActionCallback.REJECTED;

    final Configurable old = myCurrentConfigurable;
    myCurrentConfigurable = configurable;

    return notify(new ColleagueAction() {
      @Override
      public ActionCallback process(final OptionsEditorColleague colleague) {
        return colleague.onSelected(configurable, old);
      }
    }, requestor);

  }

  ActionCallback fireModifiedAdded(@NotNull final Configurable configurable, @Nullable OptionsEditorColleague requestor) {
    if (myModified.contains(configurable)) return ActionCallback.REJECTED;

    myModified.add(configurable);

    return notify(new ColleagueAction() {
      @Override
      public ActionCallback process(final OptionsEditorColleague colleague) {
        return colleague.onModifiedAdded(configurable);
      }
    }, requestor);

  }

  ActionCallback fireModifiedRemoved(@NotNull final Configurable configurable, @Nullable OptionsEditorColleague requestor) {
    if (!myModified.contains(configurable)) return ActionCallback.REJECTED;

    myModified.remove(configurable);

    return notify(new ColleagueAction() {
      @Override
      public ActionCallback process(final OptionsEditorColleague colleague) {
        return colleague.onModifiedRemoved(configurable);
      }
    }, requestor);
  }

  ActionCallback fireErrorsChanged(final Map<Configurable, ConfigurationException> errors, OptionsEditorColleague requestor) {
    if (myErrors.equals(errors)) return ActionCallback.REJECTED;

    myErrors = errors != null ? errors : new HashMap<>();

    return notify(new ColleagueAction() {
      @Override
      public ActionCallback process(final OptionsEditorColleague colleague) {
        return colleague.onErrorsChanged();
      }
    }, requestor);
  }

  ActionCallback notify(ColleagueAction action, OptionsEditorColleague requestor) {
    final ActionCallback.Chunk chunk = new ActionCallback.Chunk();
    for (OptionsEditorColleague each : myColleagues) {
      if (each != requestor) {
        chunk.add(action.process(each));
      }
    }

    return chunk.getWhenProcessed();
  }

  public void fireReset(final Configurable configurable) {
    if (myModified.contains(configurable)) {
      fireModifiedRemoved(configurable, null);
    }

    if (myErrors.containsKey(configurable)) {
      Map<Configurable, ConfigurationException> newErrors = new THashMap<>();
      newErrors.remove(configurable);
      fireErrorsChanged(newErrors, null);
    }
  }

  public boolean isModified(final Configurable configurable) {
    return myModified.contains(configurable);
  }

  public void setHoldingFilter(final boolean holding) {
    myHoldingFilter = holding;
  }

  public boolean isHoldingFilter() {
    return myHoldingFilter;
  }

  public Configurable getParentConfigurable(final Configurable configurable) {
    return myConfigurableToParentMap.get(configurable);
  }

  public void registerKid(final Configurable parent, final Configurable kid) {
    myConfigurableToParentMap.put(kid,parent);
    myParentToChildrenMap.put(parent, kid);
  }

  public Collection<Configurable> getChildren(final Configurable parent) {
    Collection<Configurable> result = myParentToChildrenMap.get(parent);
    return result == null ? Collections.emptySet() : result;
  }

  interface ColleagueAction {
    ActionCallback process(OptionsEditorColleague colleague);
  }

  public Configurable getCurrentConfigurable() {
    return myCurrentConfigurable;
  }

  public Set<Configurable> getModified() {
    return myModified;
  }

  public Map<Configurable, ConfigurationException> getErrors() {
    return myErrors;
  }

  public void addColleague(@NotNull OptionsEditorColleague colleague) {
    myColleagues.add(colleague);
  }
}