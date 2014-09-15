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
package com.intellij.testFramework;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;

public abstract class FlyIdeaTestCase extends TestCase {
  private final Disposable myRootDisposable = Disposer.newDisposable();
  private File myTempDir;

  @Override
  protected void setUp() throws Exception {
    LightPlatformTestCase.initApplication();
  }

  public File getTempDir() throws IOException {
    if (myTempDir == null) {
      myTempDir = FileUtil.createTempDirectory(getName(), getClass().getName(),false);
    }

    return myTempDir;
  }

  public Disposable getRootDisposable() {
    return myRootDisposable;
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    if (myTempDir != null) {
      FileUtil.asyncDelete(myTempDir);
    }
    Disposer.dispose(myRootDisposable);
  }
}
