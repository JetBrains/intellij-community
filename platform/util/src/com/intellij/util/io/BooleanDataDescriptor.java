/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
public class BooleanDataDescriptor extends InlineKeyDescriptor<Boolean> {
  public static final BooleanDataDescriptor INSTANCE = new BooleanDataDescriptor();

  private BooleanDataDescriptor() {
  }

  public Boolean fromInt(int n) {
    return n != 0 ? Boolean.TRUE : Boolean.FALSE;
  }

  public int toInt(Boolean aBoolean) {
    return aBoolean == Boolean.TRUE ? 1 : 0;
  }
}
