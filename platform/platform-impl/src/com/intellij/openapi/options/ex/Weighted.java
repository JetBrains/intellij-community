// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.ex;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.DisplayPrioritySortable;

import java.util.Comparator;

/**
 * Allows to sort configurables in generic configurables groups.
 *
 * @see DisplayPrioritySortable
 */
interface Weighted {
  int getWeight();

  Comparator<Configurable> COMPARATOR = (configurable1, configurable2) -> {
    int weight1 = configurable1 instanceof Weighted ? ((Weighted)configurable1).getWeight() : 0;
    int weight2 = configurable2 instanceof Weighted ? ((Weighted)configurable2).getWeight() : 0;
    return weight1 > weight2 ? -1 :
           weight1 < weight2 ? 1 :
           StringUtil.naturalCompare(
             configurable1 == null ? null : configurable1.getDisplayName(),
             configurable2 == null ? null : configurable2.getDisplayName());
  };
}
