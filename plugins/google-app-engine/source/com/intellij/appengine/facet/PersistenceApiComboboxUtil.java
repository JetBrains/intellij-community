// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.appengine.facet;

import com.intellij.appengine.JavaGoogleAppEngineBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.appengine.model.PersistenceApi;

import javax.swing.*;
import java.util.function.Supplier;

public final class PersistenceApiComboboxUtil {
  public static final Supplier<@Nls @NotNull String> NONE_ITEM_SUPPLIER = JavaGoogleAppEngineBundle.messagePointer("persistence.api.item.name.none");

  private PersistenceApiComboboxUtil() {
  }

  public static void setComboboxModel(final JComboBox<String> comboBox, final boolean addNoneItem) {
    final DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
    if (addNoneItem) {
      model.addElement(NONE_ITEM_SUPPLIER.get());
    }
    for (PersistenceApi api : PersistenceApi.values()) {
      model.addElement(api.getDisplayName());
    }
    comboBox.setModel(model);
  }

  @Nullable
  public static PersistenceApi getSelectedApi(final JComboBox<String> comboBox) {
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
