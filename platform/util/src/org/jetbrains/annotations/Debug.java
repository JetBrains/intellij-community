// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.annotations;

import org.intellij.lang.annotations.Language;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@ApiStatus.Experimental
public class Debug {
  /**
   * Allows to change the presentation of an object in debuggers
   */
  @Target(ElementType.TYPE)
  public @interface Renderer {
    /**
     * Expression to be evaluated and used as the textual representation of the object.<br>
     * {@code this} refers to the class instance being presented
     */
    @Language(value = "JAVA", prefix = "class Renderer{String $text(){return ", suffix = ";}}")
    String text() default "";

    /**
     * Expression to be evaluated to obtain an array of object's children.<br>
     * Usually the result is an array of elements in a collection, or an array of entries in a map.<br>
     * {@code this} refers to the class instance being presented
     */
    @Language(value = "JAVA", prefix = "class Renderer{Object[] $childrenArray(){return ", suffix = ";}}")
    String childrenArray() default "";

    /**
     * Expression to be evaluated to check if the object has any children at all.<br>
     * This should work faster than {@link #childrenArray()} and return boolean.<br>
     * {@code this} refers to the class instance being presented
     */
    @Language(value = "JAVA", prefix = "class Renderer{boolean $hasChildren(){return ", suffix = ";}}")
    String hasChildren() default "";
  }
}
