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
package com.intellij.util.io;

import com.intellij.openapi.util.text.StringUtil;

/**
 * @author nik
 */
public class CaseInsensitiveEnumeratorStringDescriptor extends EnumeratorStringDescriptor {
  @Override
  public int getHashCode(String value) {
    return StringUtil.stringHashCodeInsensitive(value);
  }

  @Override
  public boolean isEqual(String val1, String val2) {
    return val1.equalsIgnoreCase(val2);
  }
}
