/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.actionSystem.impl.actionholder;

import com.intellij.openapi.actionSystem.AnAction;

class IdActionRef<T extends AnAction> extends ActionRef<T> {
  private final String myId;

  public IdActionRef(String id) {
    myId = id;
  }

  public T getAction() {
    T action = (T)getManager().getAction(myId);
    if (action != null) return action;
    throw new IllegalStateException("There's no registered action with id=" + myId);
  }
}
