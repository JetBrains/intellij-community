/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.inject.config.ui;

import com.intellij.openapi.project.Project;
import com.intellij.util.ui.UIUtil;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base class for the different configuration panels that tries to simplify the use of
 * of nested forms
 */
public abstract class AbstractInjectionPanel<T extends BaseInjection> implements InjectionPanel<T> {
  private final List<Field> myOtherPanels = new ArrayList<>(3);
  private final List<Runnable> myUpdaters = new ArrayList<>(1);

  protected final Project myProject;

  /**
   * The orignal item - must not be modified unless apply() is called.
   */
  @NotNull
  protected final T myOrigInjection;

  /**
   * Represents the current UI state. Outside access should use {@link #getInjection()}
   */
  private T myEditCopy;

  protected AbstractInjectionPanel(@NotNull T injection, @NotNull Project project) {
    myOrigInjection = injection;
    myProject = project;

    final Field[] declaredFields = getClass().getDeclaredFields();
    for (Field field : declaredFields) {
      if (InjectionPanel.class.isAssignableFrom(field.getType())) {
        field.setAccessible(true);
        myOtherPanels.add(field);
      }
    }
  }

  public final T getInjection() {
    apply(myEditCopy);
    return myEditCopy;
  }

  @SuppressWarnings({"unchecked"})
  public final void init(@NotNull T copy) {
    myEditCopy = copy;

    for (Field panel : myOtherPanels) {
      final InjectionPanel p = getField(panel);
      p.init(copy);
    }
  }

  public final boolean isModified() {
    apply(myEditCopy);

    for (Field panel : myOtherPanels) {
      final InjectionPanel p = getField(panel);
      p.isModified();
    }

    return !myEditCopy.equals(myOrigInjection);
  }

  @SuppressWarnings({"unchecked"})
  public final void apply() {
    for (Field panel : myOtherPanels) {
      getField(panel).apply();
    }

    // auto-generated name should go last
    apply(myOrigInjection);
    if (!myOtherPanels.isEmpty()) {
      myOrigInjection.generatePlaces();
      myEditCopy.copyFrom(myOrigInjection);
    }
  }

  protected abstract void apply(T other);

  @SuppressWarnings({"unchecked"})
  public final void reset() {
    if (!myOtherPanels.isEmpty()) {
      myEditCopy.copyFrom(myOrigInjection);
    }
    for (Field panel : myOtherPanels) {
      getField(panel).reset();
    }
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      public void run() {
        resetImpl();
      }
    });
  }

  protected abstract void resetImpl();

  public void addUpdater(Runnable updater) {
    myUpdaters.add(updater);
    for (Field panel : myOtherPanels) {
      final InjectionPanel field = getField(panel);
      field.addUpdater(updater);
    }
  }

  private InjectionPanel getField(Field field) {
    try {
      return ((InjectionPanel)field.get(this));
    }
    catch (IllegalAccessException e) {
      throw new Error(e);
    }
  }

  protected void updateTree() {
    apply(myEditCopy);
    for (Runnable updater : myUpdaters) {
      updater.run();
    }
  }
}
