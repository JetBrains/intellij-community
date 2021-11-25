// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.struct;

public class MalformedClassFormatException extends RuntimeException {

  public MalformedClassFormatException(String message) {
    super(message);
  }

}
