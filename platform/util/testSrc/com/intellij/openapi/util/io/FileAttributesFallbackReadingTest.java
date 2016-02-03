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
package com.intellij.openapi.util.io;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import static org.junit.Assert.assertEquals;

public class FileAttributesFallbackReadingTest extends FileAttributesReadingTest {
  @BeforeClass
  public static void setUpClass() {
    System.setProperty(FileSystemUtil.FORCE_USE_FALLBACK_KEY, "true");
    FileSystemUtil.resetMediator();
    assertEquals("Fallback", FileSystemUtil.getMediatorName());
  }

  @AfterClass
  public static void tearDownClass() {
    System.clearProperty(FileSystemUtil.FORCE_USE_FALLBACK_KEY);
    FileSystemUtil.resetMediator();
  }

  @Override public void linkToFile() { }
  @Override public void doubleLink() { }
  @Override public void linkToDirectory() { }
  @Override public void missingLink() { }
  @Override public void selfLink() { }
  @Override public void junction() { }
}