// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.text;

import org.jetbrains.annotations.ApiStatus.Internal;

/**
 * This interface implementations must have {@code hashCode} values equal to those for String.
 *
 * @see com.intellij.openapi.util.text.StringUtil#stringHashCode(CharSequence)
 */
@Internal
public interface CharSequenceWithStringHash extends CharSequence {
  @Override
  int hashCode();
}