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
package org.jetbrains.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * @author egor
 */
public class Debugger {

  /**
   * Advise IDEA Debugger to capture stack frames and use as a key:
   * <ul>
   * <li>{@code keyExpression} (if specified)</li>
   * <li>annotated parameter value</li>
   * <li>{@code this} value (if used on a method)</li>
   * </ul>
   */
  @Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.PARAMETER})
  public @interface Capture {
    String keyExpression() default "";
  }

  /**
   * Advise IDEA Debugger to replace the stack frames with the captured information and use as a key:
   * <ul>
   * <li>{@code keyExpression} (if specified)</li>
   * <li>annotated parameter value</li>
   * <li>{@code this} value (if used on a method)</li>
   * </ul>
   */
  @Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.PARAMETER})
  public @interface Insert {
    String keyExpression() default "";
    String group() default "";
  }
}
