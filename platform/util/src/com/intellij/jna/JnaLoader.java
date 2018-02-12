// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.jna;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.sun.jna.Native;

public class JnaLoader {
  private static Boolean ourJnaLoaded = null;

  public static synchronized void load(Logger logger) {
    if (ourJnaLoaded == null) {
      ourJnaLoaded = Boolean.FALSE;
      try {
        long t = System.currentTimeMillis();
        int ptrSize = Native.POINTER_SIZE;
        t = System.currentTimeMillis() - t;
        logger.info("JNA library (" + (ptrSize << 3) + "-bit) loaded in " + t + " ms");
        ourJnaLoaded = Boolean.TRUE;
      }
      catch (Throwable t) {
        logger.error("Unable to load JNA library (OS: " + SystemInfo.OS_NAME + " " + SystemInfo.OS_VERSION + ")", t);
      }
    }
  }

  public static synchronized boolean isLoaded() {
    if (ourJnaLoaded == null) {
      load(Logger.getInstance(JnaLoader.class));
    }
    return ourJnaLoaded;
  }
}