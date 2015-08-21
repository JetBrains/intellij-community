/*
 * Copyright 2015 JetBrains s.r.o.
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
package org.jetbrains.java.decompiler;

import static org.jetbrains.java.decompiler.TestFileUtilities.compareFolderContents;
import static org.jetbrains.java.decompiler.TestFileUtilities.unpack;

import java.io.File;
import java.io.IOException;

import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ShadowingClassesTest {

  private DecompilerTestFixture fixture;

  @Before
  public void setUp() throws IOException {
    fixture = new DecompilerTestFixture();
    fixture.setUp();
  }

  @After
  public void tearDown() {
    fixture.tearDown();
    fixture = null;
  }

  @Test
  public void testDecompileWithNameShadowing() throws IOException {
    final ConsoleDecompiler decompiler = fixture.getDecompiler();
    decompiler.addSpace(new File(fixture.getTestDataDir(), "shadowingTestData.jar"), true);
    decompiler.decompileContext();

    final File unpacked = new File(fixture.getTempDir(), "unpacked");
    unpack(new File(fixture.getTargetDir(), "shadowingTestData.jar"), unpacked);

    compareFolderContents(new File(fixture.getTestDataDir(), "expected/shadowingTestData"), unpacked);
  }

}
