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
package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.TextChange;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Defines a contract for the callbacks for soft wraps management notifications (addition/removal).
 *
 * @author Denis Zhdanov
 * @since Jul 8, 2010 6:50:17 PM
 */
public interface SoftWrapChangeListener {

  /**
   * This method is assumed to be called every new soft wrap is registered.
   *
   * @param softWrap   newly registered soft wrap
   */
  void softWrapAdded(@NotNull TextChange softWrap);

  /**
   * This method is assumed to be called every time soft wrap(s) is removed.
   */
  void softWrapsRemoved();
}
