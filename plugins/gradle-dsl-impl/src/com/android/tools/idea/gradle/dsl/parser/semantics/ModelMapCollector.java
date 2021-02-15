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

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableMap;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collector;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;

public final class ModelMapCollector {
  @NotNull
  public static Collector<Object[], ?, ImmutableMap<Pair<String, Integer>, ModelEffectDescription>> toModelMap() {
    Function<Object[], Pair<String,Integer>> k = data -> {
      Integer arity = (Integer) data[1];
      SemanticsDescription description = (SemanticsDescription) data[3];
      if (Objects.equals(arity, ArityHelper.property)) {
        if (!(description instanceof PropertySemanticsDescription)) {
          throw new RuntimeException((String) data[0]);
        }
      }
      else {
        if (!(description instanceof MethodSemanticsDescription)) {
          throw new RuntimeException((String) data[0]);
        }
      }
      return new Pair<>((String)data[0], (Integer)data[1]);
    };
    Function<Object[], ModelEffectDescription> v = data -> {
      if (data[2] instanceof String) {
        return new ModelEffectDescription(new ModelPropertyDescription((String)data[2]), (SemanticsDescription)data[3]);
      }
      else if (data[2] instanceof ModelPropertyDescription) {
        return new ModelEffectDescription((ModelPropertyDescription) data[2], (SemanticsDescription) data[3]);
      }
      else {
        throw new RuntimeException(data[2].toString());
      }
    };
    return toImmutableMap(k, v);
  }
}
