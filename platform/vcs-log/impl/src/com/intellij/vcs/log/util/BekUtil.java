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
package com.intellij.vcs.log.util;

import com.intellij.openapi.util.registry.Registry;

public class BekUtil {
  public static boolean isBekEnabled() { // todo drop later
    if (Registry.is("vcs.log.bek.sort.disabled")) {
      return false;
    }
    boolean isInternal = Boolean.valueOf(System.getProperty("idea.is.internal"));
    boolean isBekEnabled = Registry.is("vcs.log.bek.sort");
    return isBekEnabled || isInternal;
  }

  public static boolean isLinearBekEnabled() {
    return isBekEnabled() && Registry.is("vcs.log.linear.bek.sort");
  }
}
