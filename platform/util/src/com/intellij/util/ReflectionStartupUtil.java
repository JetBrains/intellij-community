// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.JBTreeTraverser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Do not use in ReflectionUtil and any such low-level and early (start-up) code.
 */
public final class ReflectionStartupUtil {
  @NotNull
  public static JBTreeTraverser<Class<?>> classTraverser(@Nullable Class<?> root) {
    return CLASS_TRAVERSER.unique().withRoot(root);
  }

  private static final JBTreeTraverser<Class<?>> CLASS_TRAVERSER = JBTreeTraverser.from(
    (Function<? super Class<?>, ? extends Iterable<? extends Class<?>>>)aClass -> JBIterable.<Class<?>>of(aClass.getSuperclass())
      .append(aClass.getInterfaces()));
}
