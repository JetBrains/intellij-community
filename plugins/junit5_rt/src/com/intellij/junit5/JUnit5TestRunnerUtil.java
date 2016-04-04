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

import com.intellij.rt.execution.junit.JUnitStarter;
import org.junit.gen5.engine.discovery.NameBasedSelector;
import org.junit.gen5.engine.discovery.PackageSelector;
import org.junit.gen5.launcher.TestDiscoveryRequest;
import org.junit.gen5.launcher.main.TestDiscoveryRequestBuilder;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class JUnit5TestRunnerUtil {

  public static TestDiscoveryRequest buildRequest(String[] suiteClassNames, String[] packageNameRef) {
    if (suiteClassNames.length == 0) {
      return null;
    }

    final TestDiscoveryRequestBuilder builder = TestDiscoveryRequestBuilder.request();
    final List<String> lines = new ArrayList<>();


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

          while ((line = reader.readLine()) != null) {
            lines.add(line);
          }
          packageNameRef[0] = packageName.length() == 0 ? "<default package>" : packageName;
          if (JUnitStarter.isJUnit5Preferred()) {
            final PackageSelector selector = PackageSelector.forPackageName(packageName);
            return builder.select(selector).build();
          }
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
      Collections.addAll(lines, suiteClassNames);
    }

    final List<String> mappedLines = lines.stream().map(line -> line.replaceFirst(",", "#")).collect(Collectors.toList());
    return builder.select(NameBasedSelector.forNames(mappedLines)).build();
  }

}
