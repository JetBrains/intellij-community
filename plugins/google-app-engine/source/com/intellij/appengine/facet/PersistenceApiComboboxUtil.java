package com.intellij.appengine.facet;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public class PersistenceApiComboboxUtil {
  public static void setComboboxModel(final JComboBox comboBox, final boolean addNoneItem) {
    final DefaultComboBoxModel model = new DefaultComboBoxModel();
    if (addNoneItem) {
      model.addElement("None");
    }
    for (PersistenceApi api : PersistenceApi.values()) {
      model.addElement(api.getName());
    }
    comboBox.setModel(model);
  }

  @Nullable
  public static PersistenceApi getSelectedApi(final JComboBox comboBox) {
    final String apiName = (String)comboBox.getSelectedItem();
    PersistenceApi api = null;
    for (PersistenceApi value : PersistenceApi.values()) {
      if (value.getName().equals(apiName)) {
        api = value;
      }
    }
    return api;
  }
}
