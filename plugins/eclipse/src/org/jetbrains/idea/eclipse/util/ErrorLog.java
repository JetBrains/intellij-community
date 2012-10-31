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
package org.jetbrains.idea.eclipse.util;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.eclipse.ConversionException;

import java.io.IOException;

public class ErrorLog {
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
