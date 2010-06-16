/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.application.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

/**
 * User: spLeaner
 */
public class MacRestarter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.application.impl.MacRestarter");

  private MacRestarter() {
  }

  @SuppressWarnings({"RedundantArrayCreation"})
  public static void restart() {
    final ID autoReleasePool = Foundation.invoke("NSAutoreleasePool", "new");
    final ID app = Foundation.invoke("NSRunningApplication", "currentApplication");
    final ID executableURL = Foundation.invoke(app, Foundation.createSelector("executableURL"));
    final ID stringURL = Foundation.invoke(executableURL, Foundation.createSelector("absoluteString"));

    try {
      final String executablePath = Foundation.toStringViaUTF8(stringURL);
      final URL url = new URL(executablePath);
      final String path = url.getPath();
      if (path.contains(".app")) {
        final int appIndex = path.indexOf(".app");
        final String appPath = path.substring(0, appIndex + 4);
        final String relaunchPath = path.substring(0, path.lastIndexOf('/')) + "/../../bin/relaunch";
        final long processId = Foundation.invoke(app, Foundation.createSelector("processIdentifier")).longValue();

        final ID args = Foundation.invoke(Foundation.getClass("NSArray"), Foundation.createSelector("arrayWithObjects:"),
                                          new Object[]{Foundation.cfString(appPath), Foundation.cfString(String.valueOf(processId))});

        Foundation.invoke(Foundation.getClass("NSTask"), Foundation.createSelector("launchedTaskWithLaunchPath:arguments:"),
                          Foundation.cfString(relaunchPath), args);
      }
    }
    catch (MalformedURLException e) {
      LOG.error(e);
    }
    finally {
      Foundation.invoke(autoReleasePool, Foundation.createSelector("release"));
      ((ApplicationEx)ApplicationManager.getApplication()).exit(true);
    }
  }
}
