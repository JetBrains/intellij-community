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
package com.intellij.openapi.options;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class CompositeConfigurable<T extends UnnamedConfigurable> extends BaseConfigurable {
  private List<T> myConfigurables;

  @Override
  public void reset() {
    for (T configurable : getConfigurables()) {
      configurable.reset();
    }
  }

  @Override
  public void apply() throws ConfigurationException {
    for (T configurable : getConfigurables()) {
      configurable.apply();
    }
  }

  @Override
  public boolean isModified() {
    for (T configurable : getConfigurables()) {
      if (configurable.isModified()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void disposeUIResources() {
    if (myConfigurables != null) {
      for (final T myConfigurable : myConfigurables) {
        myConfigurable.disposeUIResources();
      }
      myConfigurables = null;
    }
  }

  @NotNull
  protected abstract List<T> createConfigurables();

  @NotNull
  public List<T> getConfigurables() {
    if (myConfigurables == null) {
      myConfigurables = createConfigurables();
    }
    return myConfigurables;
  }
}
