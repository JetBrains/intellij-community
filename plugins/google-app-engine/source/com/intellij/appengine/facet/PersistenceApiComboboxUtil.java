/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.appengine.facet;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.appengine.model.PersistenceApi;

import javax.swing.*;

/**
 * @author nik
 */
public class PersistenceApiComboboxUtil {
  @NonNls public static final String NONE_ITEM = "None";

  private PersistenceApiComboboxUtil() {
  }

  public static void setComboboxModel(final JComboBox comboBox, final boolean addNoneItem) {
    final DefaultComboBoxModel model = new DefaultComboBoxModel();
    if (addNoneItem) {
      model.addElement(NONE_ITEM);
    }
    for (PersistenceApi api : PersistenceApi.values()) {
      model.addElement(api.getDisplayName());
    }
    comboBox.setModel(model);
  }

  @Nullable
  public static PersistenceApi getSelectedApi(final JComboBox comboBox) {
    final String apiName = (String)comboBox.getSelectedItem();
    PersistenceApi api = null;
    for (PersistenceApi value : PersistenceApi.values()) {
      if (value.getDisplayName().equals(apiName)) {
        api = value;
      }
    }
    return api;
  }
}
