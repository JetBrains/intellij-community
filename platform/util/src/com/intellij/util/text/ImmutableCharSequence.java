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
package com.intellij.util.text;

import org.jetbrains.annotations.NotNull;

public abstract class ImmutableCharSequence implements CharSequence {

  public static CharSequence asImmutable(@NotNull final CharSequence cs) {
    return isImmutable(cs) ? cs : cs.toString();
  }

  public static boolean isImmutable(@NotNull final CharSequence cs) {
    if (cs instanceof ImmutableCharSequence) return true;
    if (cs instanceof CharSequenceSubSequence) return isImmutable(((CharSequenceSubSequence)cs).getBaseSequence());
    return false;
  }
  
}
