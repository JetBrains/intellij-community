// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.newEditor;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

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

  @NotNull
  Promise<? super Object> fireSelected(@Nullable final Configurable configurable, @NotNull OptionsEditorColleague requestor) {
    if (myCurrentConfigurable == configurable) {
      return Promises.rejectedPromise();
    }

    final Configurable old = myCurrentConfigurable;
    myCurrentConfigurable = configurable;

    return notify(new ColleagueAction() {
      @NotNull
      @Override
      public Promise<? super Object> process(final OptionsEditorColleague colleague) {
        return colleague.onSelected(configurable, old);
      }
    }, requestor);
  }

  @NotNull
  Promise<? super Object> fireModifiedAdded(@NotNull final Configurable configurable, @Nullable OptionsEditorColleague requestor) {
    if (myModified.contains(configurable)) {
      return Promises.rejectedPromise();
    }

    myModified.add(configurable);

    return notify(new ColleagueAction() {
      @NotNull
      @Override
      public Promise<? super Object> process(final OptionsEditorColleague colleague) {
        return colleague.onModifiedAdded(configurable);
      }
    }, requestor);

  }

  @NotNull
  Promise<? super Object> fireModifiedRemoved(@NotNull final Configurable configurable, @Nullable OptionsEditorColleague requestor) {
    if (!myModified.contains(configurable)) {
      return Promises.rejectedPromise();
    }

    myModified.remove(configurable);

    return notify(new ColleagueAction() {
      @NotNull
      @Override
      public Promise<? super Object> process(final OptionsEditorColleague colleague) {
        return colleague.onModifiedRemoved(configurable);
      }
    }, requestor);
  }

  @NotNull
  Promise<? super Object> fireErrorsChanged(final Map<Configurable, ConfigurationException> errors, OptionsEditorColleague requestor) {
    if (myErrors.equals(errors)) {
      return Promises.rejectedPromise();
    }

    myErrors = errors != null ? errors : new HashMap<>();

    return notify(new ColleagueAction() {
      @NotNull
      @Override
      public Promise<? super Object> process(final OptionsEditorColleague colleague) {
        return colleague.onErrorsChanged();
      }
    }, requestor);
  }

  @NotNull
  Promise<? super Object> notify(@NotNull ColleagueAction action, OptionsEditorColleague requestor) {
    //noinspection unchecked
    return (Promise<? super Object>)Promises.all(ContainerUtil.mapNotNull(myColleagues, it -> it == requestor ? null : action.process(it)));
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
    @NotNull
    Promise<? super Object> process(OptionsEditorColleague colleague);
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