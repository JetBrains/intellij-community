// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import static java.util.Collections.unmodifiableSet;

@ApiStatus.Internal
public final class OptionsEditorContext {
  CopyOnWriteArraySet<OptionsEditorColleague> myColleagues = new CopyOnWriteArraySet<>();

  Configurable myCurrentConfigurable;
  Set<Configurable> myModified = new CopyOnWriteArraySet<>();
  Map<Configurable, ConfigurationException> myErrors = new HashMap<>();
  private boolean myHoldingFilter;
  private final Map<Configurable,  Configurable> myConfigurableToParentMap = new HashMap<>();
  private final MultiMap<Configurable, Configurable> myParentToChildrenMap = new MultiMap<>();

  @NotNull
  Promise<? super Object> fireSelected(final @Nullable Configurable configurable, @NotNull OptionsEditorColleague requestor) {
    if (myCurrentConfigurable == configurable) {
      return Promises.resolvedPromise();
    }

    final Configurable old = myCurrentConfigurable;
    myCurrentConfigurable = configurable;

    return notify(new ColleagueAction() {
      @Override
      public @NotNull Promise<? super Object> process(final OptionsEditorColleague colleague) {
        return colleague.onSelected(configurable, old);
      }
    }, requestor);
  }

  @NotNull
  Promise<? super Object> fireModifiedAdded(final @NotNull Configurable configurable, @Nullable OptionsEditorColleague requestor) {
    if (myModified.contains(configurable)) {
      return Promises.rejectedPromise();
    }

    myModified.add(configurable);

    return notify(new ColleagueAction() {
      @Override
      public @NotNull Promise<? super Object> process(final OptionsEditorColleague colleague) {
        return colleague.onModifiedAdded(configurable);
      }
    }, requestor);

  }

  @NotNull
  Promise<? super Object> fireModifiedRemoved(final @NotNull Configurable configurable, @Nullable OptionsEditorColleague requestor) {
    if (!myModified.contains(configurable)) {
      return Promises.rejectedPromise();
    }

    myModified.remove(configurable);

    return notify(new ColleagueAction() {
      @Override
      public @NotNull Promise<? super Object> process(final OptionsEditorColleague colleague) {
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
      @Override
      public @NotNull Promise<? super Object> process(final OptionsEditorColleague colleague) {
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
      Map<Configurable, ConfigurationException> newErrors = new HashMap<>(myErrors);
      newErrors.remove(configurable);
      fireErrorsChanged(newErrors, null);
    }
  }

  public boolean isModified(final Configurable configurable) {
    if (myModified.contains(configurable)) return true;

    if (configurable instanceof Configurable.InnerWithModifiableParent inner) {
      for (Configurable parent: inner.getModifiableParents()) {
        if (myModified.contains(parent)) return true;

        for (Configurable modified: myModified) {
          if (modified instanceof ConfigurableWrapper wrapper) {
            UnnamedConfigurable unwrapped = wrapper.getRawConfigurable();
            if (unwrapped != null && unwrapped.equals(parent)) return true;
          }
        }
      }
    }

    return false;
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
    myConfigurableToParentMap.put(kid, parent);
    myParentToChildrenMap.putValue(parent, kid);
  }

  public @NotNull Collection<Configurable> getChildren(final Configurable parent) {
    return myParentToChildrenMap.get(parent);
  }

  interface ColleagueAction {
    @NotNull
    Promise<? super Object> process(OptionsEditorColleague colleague);
  }

  public Configurable getCurrentConfigurable() {
    return myCurrentConfigurable;
  }

  public Set<Configurable> getModified() {
    return unmodifiableSet(myModified);
  }

  public Map<Configurable, ConfigurationException> getErrors() {
    return myErrors;
  }

  public void addColleague(@NotNull OptionsEditorColleague colleague) {
    myColleagues.add(colleague);
  }

  public void reload() {
    myCurrentConfigurable = null;
    myErrors.clear();
    myConfigurableToParentMap.clear();
    myParentToChildrenMap.clear();
  }
}