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
package com.intellij.openapi.options.ex;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.util.text.StringUtil;

import java.util.Comparator;

/**
 * @author Sergey.Malenkov
 */
interface Weighted {
  int getWeight();

  Comparator<Configurable> COMPARATOR = new Comparator<Configurable>() {
    @Override
    public int compare(Configurable configurable1, Configurable configurable2) {
      int weight1 = configurable1 instanceof Weighted ? ((Weighted)configurable1).getWeight() : 0;
      int weight2 = configurable2 instanceof Weighted ? ((Weighted)configurable2).getWeight() : 0;
      return weight1 > weight2 ? -1 :
             weight1 < weight2 ? 1 :
             StringUtil.naturalCompare(
               configurable1 == null ? null : configurable1.getDisplayName(),
               configurable2 == null ? null : configurable2.getDisplayName());
    }
  };
}
