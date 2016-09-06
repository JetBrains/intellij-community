/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.junit5;

import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.discovery.MethodSelector;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class JUnit5TestRunnerUtil {

  public static LauncherDiscoveryRequest buildRequest(String[] suiteClassNames, String[] packageNameRef) {
    if (suiteClassNames.length == 0) {
      return null;
    }

    final LauncherDiscoveryRequestBuilder builder = LauncherDiscoveryRequestBuilder.request();


    if (suiteClassNames.length == 1 && suiteClassNames[0].charAt(0) == '@') {
      // all tests in the package specified
      try {
        BufferedReader reader = new BufferedReader(new FileReader(suiteClassNames[0].substring(1)));
        try {
          final String packageName = reader.readLine();
          if (packageName == null) return null;

          //todo category?
          final String categoryName = reader.readLine();
          String line;

          List<DiscoverySelector> selectors = new ArrayList<>();
          while ((line = reader.readLine()) != null) {
            selectors.add(createSelector(line));
          }
          packageNameRef[0] = packageName.length() == 0 ? "<default package>" : packageName;
          return builder.selectors(selectors).build();
        }
        finally {
          reader.close();
        }
      }
      catch (IOException e) {
        e.printStackTrace();
        System.exit(1);
      }
    }
    else {
        return builder.selectors(createSelector(suiteClassNames[0])).build();
    }

    return null;
  }

  protected static DiscoverySelector createSelector(String line) {
    if (line.contains(",")) {
      return DiscoverySelectors.selectMethod(line.replaceFirst(",", "#"));
    }
    else {
      return DiscoverySelectors.selectClass(line);
    }
  }
}
