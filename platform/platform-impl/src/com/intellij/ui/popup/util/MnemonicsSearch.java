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
package com.intellij.ui.popup.util;

import com.intellij.openapi.ui.popup.MnemonicNavigationFilter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.popup.WizardPopup;

import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class MnemonicsSearch<T> {

  private final WizardPopup myPopup;
  private final Map<String, T> myChar2ValueMap = new HashMap();

  public MnemonicsSearch(WizardPopup popup) {
    myPopup = popup;
    if (!myPopup.getStep().isMnemonicsNavigationEnabled()) return;

    final MnemonicNavigationFilter filter = myPopup.getStep().getMnemonicNavigationFilter();
    final List<T> values = filter.getValues();
    for (T each : values) {
      final int pos = filter.getMnemonicPos(each);
      if (pos != -1) {
        final String text = filter.getTextFor(each);
        final String charText = text.substring(pos + 1, pos + 2);
        myChar2ValueMap.put(StringUtil.toUpperCase(charText), each);
        myChar2ValueMap.put(charText.toLowerCase(), each);
      }
    }
  }

  public void process(KeyEvent e) {
    if (e.isConsumed() || !StringUtil.isEmptyOrSpaces(myPopup.getSpeedSearch().getFilter())) return;

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
