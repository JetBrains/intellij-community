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
package org.jetbrains.android.dom.attrs;

import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class AttributeDefinition {
  private final String myName;
  private final Set<AttributeFormat> myFormats = EnumSet.noneOf(AttributeFormat.class);
  private final List<String> myValues = new ArrayList<String>();
  private String myDocValue;

  public AttributeDefinition(@NotNull String name) {
    myName = name;
  }

  public AttributeDefinition(@NotNull String name, @NotNull Collection<AttributeFormat> formats) {
    myName = name;
    myFormats.addAll(formats);
  }

  public void addValue(@NotNull String name) {
    myValues.add(name);
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public Set<AttributeFormat> getFormats() {
    return Collections.unmodifiableSet(myFormats);
  }

  public void addFormats(@NotNull Collection<AttributeFormat> format) {
    myFormats.addAll(format);
  }

  @NotNull
  public String[] getValues() {
    return ArrayUtil.toStringArray(myValues);
  }

  @Nullable
  public String getDocValue() {
    return myDocValue;
  }

  public void addDocValue(String docValue) {
    myDocValue = docValue;
  }

  @Override
  public String toString() {
    return myName + " [" + myFormats + ']';
  }
}
