/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.reactivemodel.util;

import java.util.Collections;
import java.util.List;

import static java.util.Arrays.asList;

public class Primitives {
    public static final List<? extends Class<?>> TYPES = Collections.unmodifiableList(asList(
            boolean.class, Boolean.class,
            byte.class, Byte.class,
            char.class, Character.class,
            double.class, Double.class,
            float.class, Float.class,
            int.class, Integer.class,
            long.class, Long.class,
            short.class, Short.class,
            String.class));

  public static final List<? extends Class<?>> JAVA_PRIMITIVES = Collections.unmodifiableList(asList(
    boolean.class,
    byte.class,
    char.class,
    double.class,
    float.class,
    int.class,
    long.class,
    short.class));

  private Primitives() {
  }
}
