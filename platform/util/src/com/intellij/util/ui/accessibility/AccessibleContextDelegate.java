/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.ui.accessibility;

import org.jetbrains.annotations.NotNull;

import javax.accessibility.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.util.Locale;

/**
 * AccessibleContext implementation that delegates all calls to another context.
 *
 * This is useful when a component needs to expose an AccessibleContext where most
 * of the implementation comes from another context while at the same a specific subset
 * of the behavior can be overridden.
 */
public class AccessibleContextDelegate extends AbstractAccessibleContextDelegate {
  private AccessibleContext myContext;

  public AccessibleContextDelegate(@NotNull AccessibleContext context) {
    myContext = context;
  }

  @NotNull
  @Override
  protected AccessibleContext getDelegate() {
    return myContext;
  }
}
