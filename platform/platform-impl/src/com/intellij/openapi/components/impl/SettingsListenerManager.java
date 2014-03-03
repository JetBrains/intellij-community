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
package com.intellij.openapi.components.impl;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.SettingsChangeListener;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ex.ConfigurableWrapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 3/3/14
 * Time: 4:15 PM
 */
public class SettingsListenerManager {

  public List<SettingsChangeListener> listeners = null;

  public static SettingsListenerManager getInstance() {
    return ServiceManager.getService(SettingsListenerManager.class);
  }

  public void add(SettingsChangeListener listener){
    if (listeners == null) {
      listeners = new ArrayList<SettingsChangeListener>();
    }
    listeners.add(listener);
  }

  public void fireSettingsChanged(Collection<Configurable> configurables) {
    if (listeners == null) return;
    Collection<String> modified = new ArrayList<String>();
    for (Configurable configurable : configurables) {
      modified.add(((ConfigurableWrapper)configurable).getId());
    }
    for (SettingsChangeListener listener : listeners) {
      listener.onChange(modified);
    }
  }
}
