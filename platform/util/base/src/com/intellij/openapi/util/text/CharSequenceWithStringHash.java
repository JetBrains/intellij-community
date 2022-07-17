// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.text;

/**
 * This interface implementations must have {@code hashCode} values equal to those for String.
 *
 * @see com.intellij.openapi.util.text.StringUtil#stringHashCode(CharSequence)
 */
public interface CharSequenceWithStringHash extends CharSequence {
  @Override
  int hashCode();
}