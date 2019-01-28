/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.service.execution;

import org.gradle.internal.impldep.com.google.common.collect.ImmutableList;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.intellij.openapi.util.io.FileUtil.filesEqual;
import static com.intellij.openapi.util.io.FileUtil.loadFile;
import static org.junit.Assert.*;

public class GradleExecutionHelperTest {

  @Test
  public void testPasswordObfuscation() {
    List<String> originalArgs = ImmutableList.of(
      "--configure-on-demand",
      "-Pandroid.injected.invoked.from.ide=true",
      "-Pandroid.injected.signing.store.password=asdfghjkl;",
      "-Pandroid.injected.signing.key.alias=testKey",
      "-Pandroid.injected.signing.key.password=blabla",
      "/tmp/asLocalRepo1004.gradle"
    );

    List<String> obfuscatedArgs = GradleExecutionHelper.obfuscatePasswordParameters(originalArgs);

    List<String> expectedArgs = ImmutableList.of(
      "--configure-on-demand",
      "-Pandroid.injected.invoked.from.ide=true",
      "-Pandroid.injected.signing.store.password=*********",
      "-Pandroid.injected.signing.key.alias=testKey",
      "-Pandroid.injected.signing.key.password=*********",
      "/tmp/asLocalRepo1004.gradle"
    );

    assertEquals(obfuscatedArgs, expectedArgs);
  }

  @Test
  public void testWriteToFileGradleInitScript() throws IOException {
    String prefix = "init";

    File tempFile = GradleExecutionHelper.writeToFileGradleInitScript("foo", prefix);
    assertTrue(tempFile.exists());
    assertEquals("foo", loadFile(tempFile));

    assertTrue(filesEqual(tempFile, GradleExecutionHelper.writeToFileGradleInitScript("foo", prefix)));

    File anotherTempFile = GradleExecutionHelper.writeToFileGradleInitScript("bar", prefix);
    assertTrue(anotherTempFile.exists());
    assertEquals("bar", loadFile(anotherTempFile));

    assertFalse(filesEqual(tempFile, anotherTempFile));

    assertTrue(filesEqual(anotherTempFile, GradleExecutionHelper.writeToFileGradleInitScript("bar", prefix)));
  }
}
