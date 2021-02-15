// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse.util;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.eclipse.ConversionException;

import java.io.IOException;

public final class ErrorLog {
  public static boolean failFast = true;

  public enum Level {
    Warning, Error, Fatal
  }

  public interface Impl {
    void report(Level level, @NonNls String module, @NonNls String context, @NonNls String message);
  }

  public static Impl defaultImpl;

  public static void release() {
    defaultImpl = null;
  }

  public static void report(Level level, @NonNls String module, @NonNls String context, @NonNls String message) {
    if (defaultImpl != null) {
      defaultImpl.report(level, module, context, message);
    }
  }

  public static void rethrow(Level level, @NonNls String module, @NonNls String context, Exception e) throws IOException, ConversionException {
    report(level, module, context, e);
    if ( failFast ) {
      if ( e instanceof IOException) {
        throw (IOException) e;
      } else
      if ( e instanceof ConversionException) {
        throw (ConversionException) e;
      } else
        throw new ConversionException ( e.getMessage() );
    }
  }

  public static void report(Level level, @NonNls String module, @NonNls String context, Exception e) {
    String message = e.getMessage();
    report(level, module, context, message == null ? e.getClass().toString() : FileUtil.toSystemIndependentName(message));
  }
}
