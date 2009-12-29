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

package com.intellij.util.ui;

import com.intellij.openapi.util.Pair;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * @author yole
 */
public class MappingListCellRenderer extends DefaultListCellRenderer {
  private final Map<Object, String> myValueMap;

  public MappingListCellRenderer(final Map<Object, String> valueMap) {
    myValueMap = valueMap;
  }

  public MappingListCellRenderer(final Pair<Object, String>... valuePairs) {
    myValueMap = new HashMap<Object, String>();
    for (Pair<Object, String> valuePair : valuePairs) {
      myValueMap.put(valuePair.getFirst(), valuePair.getSecond());
    }
  }

  public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    String newValue = myValueMap.get(value);
    if (newValue != null) {
      setText(newValue);
    }
    return this;
  }
}
