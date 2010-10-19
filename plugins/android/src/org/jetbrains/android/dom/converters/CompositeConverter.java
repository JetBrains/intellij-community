/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.android.dom.converters;

import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author coyote
 */
public class CompositeConverter extends ResolvingConverter<String> {
  private final List<ResolvingConverter<String>> converters = new ArrayList<ResolvingConverter<String>>();

  public void addConverter(@NotNull ResolvingConverter<String> converter) {
    converters.add(converter);
  }

  @NotNull
  public List<ResolvingConverter<String>> getConverters() {
    return converters;
  }

  public int size() {
    return converters.size();
  }

  @NotNull
  public Collection<String> getVariants(ConvertContext context) {
    List<String> variants = new ArrayList<String>();
    for (ResolvingConverter<String> converter : converters) {
      variants.addAll(converter.getVariants(context));
    }
    return variants;
  }

  public String fromString(@Nullable String s, ConvertContext context) {
    return s;
  }

  public String toString(@Nullable String s, ConvertContext context) {
    return s;
  }
}
