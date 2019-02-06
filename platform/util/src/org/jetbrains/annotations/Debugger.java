// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.annotations;

import org.intellij.lang.annotations.Language;

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
    @Language(value = "JAVA", prefix = "class Renderer{String text(){return ", suffix = ";}}")
    String text() default "";

    @Language(value = "JAVA", prefix = "class Renderer{String[] childrenArray(){return ", suffix = ";}}")
    String childrenArray() default "";

    @Language(value = "JAVA", prefix = "class Renderer{boolean hasChildren(){return ", suffix = ";}}")
    String hasChildren() default "";
  }
}
