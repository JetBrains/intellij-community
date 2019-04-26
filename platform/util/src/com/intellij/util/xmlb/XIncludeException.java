// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb;

class XIncludeException extends RuntimeException {
  XIncludeException(final String message) {
    super(message);
  }

  XIncludeException(final Throwable cause) {
    super(cause);
  }
}