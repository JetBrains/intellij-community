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
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.mac.MacDockImpl;

/**
 * @author Denis Fokin
 */
public class SystemDock {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.wm.impl.SystemDock");

  synchronized public static void initialize() {
    if (SystemInfo.isMac) {
      try {
        MacDockImpl.initialize();
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }
}
