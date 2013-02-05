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
package hg4idea.test;

import com.intellij.dvcs.test.Executor;
import com.intellij.openapi.util.text.StringUtil;

import java.util.Arrays;
import java.util.List;

/**
 * @author Nadya Zabrodina
 */
public class HgExecutor extends Executor {

  private static final String HG_EXECUTABLE_ENV = "IDEA_TEST_HG_EXECUTABLE";
  //private static final String TEAMCITY_HG_EXECUTABLE_ENV = "TEAMCITY_HG_PATH";   //todo var for server testing

  private static boolean myVersionPrinted;
  private static final String HG_EXECUTABLE = findHgExecutable();

  private static String findHgExecutable() {
    return findExecutable("hg", "hg", "hg.exe", Arrays.asList(HG_EXECUTABLE_ENV));
  }

  public static String hg(String command) {
    printVersionTheFirstTime();
    List<String> split = StringUtil.split(command, " ");
    split.add(0, HG_EXECUTABLE);
    log("hg " + command);
    for(int attempt = 0; attempt < 3; attempt++) {
      return run(split);
    }
    throw new RuntimeException("fatal error during execution of Hg command: " + command);
  }

  private static void printVersionTheFirstTime() {
    if (!myVersionPrinted) {
      myVersionPrinted = true;
      hg("version");
    }
  }
}

