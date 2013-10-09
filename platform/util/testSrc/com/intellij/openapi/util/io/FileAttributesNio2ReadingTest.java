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
package com.intellij.openapi.util.io;

import com.intellij.openapi.util.SystemInfo;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

public class FileAttributesNio2ReadingTest extends FileAttributesReadingTest {
  private static final String FORCE_USE_NIO_2_KEY;
  static {
    try {
      Field field = FileSystemUtil.class.getDeclaredField("FORCE_USE_NIO2_KEY");
      field.setAccessible(true);
      FORCE_USE_NIO_2_KEY = (String)field.get(null);
    }
    catch (Exception e) {
      throw new AssertionError("Please keep constants in sync: " + e.getMessage());
    }
  }

  @BeforeClass
  public static void setUpClass() throws Exception {
    assumeTrue(SystemInfo.isJavaVersionAtLeast("1.7"));

    System.setProperty(FORCE_USE_NIO_2_KEY, "true");
    FileSystemUtil.resetMediator();
    assertEquals("Nio2", FileSystemUtil.getMediatorName());
  }

  @AfterClass
  public static void tearDownClass() throws Exception {
    System.setProperty(FORCE_USE_NIO_2_KEY, "");
    FileSystemUtil.resetMediator();
  }
}
