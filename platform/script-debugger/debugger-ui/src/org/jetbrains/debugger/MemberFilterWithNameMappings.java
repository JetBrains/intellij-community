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
package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

public class MemberFilterWithNameMappings extends MemberFilterBase {
  protected final Map<String, String> rawNameToSource;

  public MemberFilterWithNameMappings(@Nullable Map<String, String> rawNameToSource) {
    this.rawNameToSource = rawNameToSource == null ? Collections.<String, String>emptyMap() : rawNameToSource;
  }

  @Override
  public final boolean hasNameMappings() {
    return !rawNameToSource.isEmpty();
  }

  @NotNull
  @Override
  public String rawNameToSource(@NotNull Variable variable) {
    String name = variable.getName();
    String sourceName = rawNameToSource.get(name);
    return sourceName == null ? normalizeMemberName(name) : sourceName;
  }

  @NotNull
  protected String normalizeMemberName(@NotNull String name) {
    return name;
  }

  @Nullable
  @Override
  public String sourceNameToRaw(@NotNull String name) {
    if (!hasNameMappings()) {
      return null;
    }

    for (Map.Entry<String, String> entry : rawNameToSource.entrySet()) {
      if (entry.getValue().equals(name)) {
        return entry.getKey();
      }
    }
    return null;
  }
}
