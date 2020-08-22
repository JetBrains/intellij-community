// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.appengine.facet;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.appengine.model.PersistenceApi;

import javax.swing.*;

public final class PersistenceApiComboboxUtil {
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
