/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.testFramework.propertyBased;

import java.util.List;

/**
 * @author peter
 */
public interface MadTestingAction {
  
  void performAction();

  static void runActions(List<? extends MadTestingAction> list) {
    for (int i = 0; i < list.size(); i++) {
      MadTestingAction action = list.get(i);
      if (i > 0 && action == list.get(i - 1)) continue;

      action.performAction();
    }
  }
}
