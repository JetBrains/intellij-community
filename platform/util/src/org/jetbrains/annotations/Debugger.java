// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * @author egor
 */
@ApiStatus.Experimental
public class Debugger {

  /**
   * Advise IDEA Debugger how to render the annotated class
   */
  @Target(ElementType.TYPE)
  public @interface Renderer {
    String text() default "";
    String childrenArray() default "";
    String hasChildren() default "";
  }
}
