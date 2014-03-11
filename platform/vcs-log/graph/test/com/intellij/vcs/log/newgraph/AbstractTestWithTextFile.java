/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.vcs.log.newgraph;

import com.intellij.openapi.util.io.FileUtil;

import java.io.File;
import java.io.IOException;

public abstract class AbstractTestWithTextFile {
  public static final String BASE_DIRECTORY = "platform/vcs-log/graph/testData/";
  public static final String IN_POSTFIX = "_in.txt";
  public static final String OUT_POSTFIX = "_out.txt";

  protected final String myDirectory;

  protected AbstractTestWithTextFile(String directory) {
    this.myDirectory = BASE_DIRECTORY + directory;
  }

  protected void doTest(String testName) throws IOException {
    String in = FileUtil.loadFile(new File(myDirectory + testName + IN_POSTFIX), true);
    String out = FileUtil.loadFile(new File(myDirectory + testName + OUT_POSTFIX), true);
    runTest(in, out);
  }

  protected abstract void runTest(String in, String out);
}
