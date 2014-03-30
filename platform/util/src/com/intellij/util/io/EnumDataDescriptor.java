/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.util.io;

/**
 * @author peter
 */
public class EnumDataDescriptor<T extends Enum> extends InlineKeyDescriptor<T> {
  private final Class<T> myEnumClass;

  public EnumDataDescriptor(Class<T> enumClass) {
    myEnumClass = enumClass;
  }

  @Override
  public T fromInt(int n) {
    return myEnumClass.getEnumConstants()[n];
  }

  @Override
  public int toInt(T t) {
    return t.ordinal();
  }
}
