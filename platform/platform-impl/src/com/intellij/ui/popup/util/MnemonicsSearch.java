// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.popup.util;

import com.intellij.openapi.ui.popup.MnemonicNavigationFilter;
import com.intellij.openapi.util.text.Strings;
import com.intellij.ui.popup.WizardPopup;
import org.jetbrains.annotations.NotNull;

import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

public abstract class MnemonicsSearch<T> {
  private final WizardPopup myPopup;
  private final Map<String, T> myChar2ValueMap = new HashMap<>();

  public MnemonicsSearch(WizardPopup popup) {
    myPopup = popup;
    if (!myPopup.getStep().isMnemonicsNavigationEnabled()) return;

    @SuppressWarnings("unchecked") MnemonicNavigationFilter<T> filter = myPopup.getStep().getMnemonicNavigationFilter();
    if (filter == null) return;
    for (T each : filter.getValues()) {
      String charText = filter.getMnemonicString(each);
      if (charText != null) {
        myChar2ValueMap.put(Strings.toUpperCase(charText), each);
        myChar2ValueMap.put(Strings.toLowerCase(charText), each);
      }
    }
  }

  public void processKeyEvent(@NotNull KeyEvent e) {
    if (e.isConsumed()) return;
    if (e.getID() != KeyEvent.KEY_TYPED) return;
    if (!Strings.isEmptyOrSpaces(myPopup.getSpeedSearch().getFilter())) return;

    if (Character.isLetterOrDigit(e.getKeyChar())) {
      final String s = Character.toString(e.getKeyChar());
      final T toSelect = myChar2ValueMap.get(s);
      if (toSelect != null) {
        select(toSelect);
        e.consume();
      }
    }
  }

  protected abstract void select(T value);
}
