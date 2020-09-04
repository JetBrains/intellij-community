/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.parser.semantics;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public abstract class ArityHelper {
  /**
   * A value of null encodes "this is not a method" (i.e. it is a property)
   */
  public static final Integer property = null;

  /**
   * We represent the arity of a method taking exactly some number of arguments as that number.
   *
   * @param n
   * @return n
   */
  @NotNull @Contract(pure = true, value = "_ -> param1")
  public static Integer exactly(@NotNull Integer n) {
    assert (n >= 0);
    return n;
  }

  /**
   * We represent the arity of a method taking at least some number of arguments (i.e. varargs) as the bitwise-complement of that number.
   *
   * This representation will allow matching of method calls in the parser to information in tables using the following lookup procedure:
   *
   * 1. if the (name,expressed arity) pair is present in the table, then we have found the relevant entry.
   * 2. otherwise, consider the pair of the name and c, where c is the bitwise-complement of the expressed arity:
   *    a. if that is present in the table, then we have found the relevant entry.
   *    b. otherwise, increment c: if c is 0, then there is no relevant entry, otherwise repeat step 2.
   *
   * @param n
   * @return the bitwise complement of n
   */
  @NotNull @Contract(pure = true)
  public static Integer atLeast(@NotNull Integer n) {
    assert (n >= 0);
    return ~n;
  }
}