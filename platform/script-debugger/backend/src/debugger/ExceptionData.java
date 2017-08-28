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
package org.jetbrains.debugger;

import com.intellij.util.ThreeState;
import org.jetbrains.debugger.values.Value;

/**
 * A JavaScript exception data holder for exceptions reported by a JavaScript
 * virtual machine.
 */
public interface ExceptionData {
  /**
   * @return the thrown exception value
   */
  Value getExceptionValue();

  /**
   * @return whether this exception is uncaught
   */
  ThreeState isUncaught();

  /**
   * @return the text of the source line where the exception was thrown or null
   */
  String getSourceText();

  /**
   * @return the exception description (plain text)
   */
  String getExceptionMessage();
}