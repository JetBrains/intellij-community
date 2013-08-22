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
package com.intellij.platform;

import com.intellij.ide.SearchTopHitProvider;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;

/**
 * @author Konstantin Bulenkov
 */
public class DefaultPlatformTopHitProvider implements SearchTopHitProvider {

  @Override
  public void consumeTopHits(String pattern, Consumer<Object> collector) {
    //todo[kb] move to constant. Now it's better for hotswap
    String[][] ACTION_MATRIX = {
       {"op", "open ", "OpenFile"},
       {"reo", "reopen ", "$LRU"},
       {"new", "new ", "NewGroup"},
       {"new c", "new class ", "NewClass"},
       {"new i", "new interface ", "NewClass"},
       {"new e", "new enum ", "NewClass"},
     };
    final ActionManager actionManager = ActionManager.getInstance();
    for (String[] strings : ACTION_MATRIX) {
      if (StringUtil.isBetween(pattern, strings[0], strings[1])) {
        for (int i = 2; i < strings.length; i++) {
          collector.consume(actionManager.getAction(strings[i]));
        }
      }
    }
  }
}
