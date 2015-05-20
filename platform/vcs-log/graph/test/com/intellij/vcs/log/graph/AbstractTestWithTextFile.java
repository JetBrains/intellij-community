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
package com.intellij.vcs.log.graph;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtil;

import java.io.File;
import java.io.IOException;

abstract class AbstractTestWithTextFile {
  public static final String BASE_DIRECTORY = "platform/vcs-log/graph/testData/";

  protected final String myDirectory;

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  protected AbstractTestWithTextFile(String directory) {
    this.myDirectory = PathManagerEx.findFileUnderCommunityHome(BASE_DIRECTORY + directory).getPath();
  }

  protected String loadText(String filename) throws IOException {
    return FileUtil.loadFile(new File(myDirectory, filename), true);
  }

}
