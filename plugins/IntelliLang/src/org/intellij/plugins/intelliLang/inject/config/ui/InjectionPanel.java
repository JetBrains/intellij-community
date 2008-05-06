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

import org.intellij.plugins.intelliLang.inject.config.Injection;

import javax.swing.*;

public interface InjectionPanel<T extends Injection> {

  /**
   * Initialize with copied item - should only be called by "terminal" implementations (i.e. classes that aren't
   * aggregated anywhere else) to prevent the presence of multiple copies.
   */
  void init(T copy);

  boolean isModified();

  void reset();

  void apply();

  JPanel getComponent();

  /**
   * Returns item that represents current UI-state.
   */
  T getInjection();

  void addUpdater(Runnable updater);
}
