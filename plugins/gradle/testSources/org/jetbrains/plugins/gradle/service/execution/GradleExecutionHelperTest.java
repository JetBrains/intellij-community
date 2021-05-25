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

import com.intellij.util.containers.MultiMap;
import org.gradle.internal.impldep.com.google.common.collect.ImmutableList;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.util.io.FileUtil.filesEqual;
import static com.intellij.openapi.util.io.FileUtil.loadFile;
import static com.intellij.testFramework.UsefulTestCase.*;
import static com.intellij.util.containers.ContainerUtil.emptyList;
import static org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    List<String> obfuscatedArgs = obfuscatePasswordParameters(originalArgs);

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

    File tempFile = writeToFileGradleInitScript("foo", prefix);
    assertTrue(tempFile.exists());
    assertEquals("foo", loadFile(tempFile));

    assertTrue(filesEqual(tempFile, writeToFileGradleInitScript("foo", prefix)));

    File anotherTempFile = writeToFileGradleInitScript("bar", prefix);
    assertTrue(anotherTempFile.exists());
    assertEquals("bar", loadFile(anotherTempFile));

    assertFalse(filesEqual(tempFile, anotherTempFile));

    assertTrue(filesEqual(anotherTempFile, writeToFileGradleInitScript("bar", prefix)));
  }

  @Test
  public void testMergeJvmArgs() {
    assertOrderedEquals(mergeJvmArgs(Arrays.asList("-X:foo"), emptyList()), Arrays.asList("-X:foo"));
    assertOrderedEquals(mergeJvmArgs(emptyList(), Arrays.asList("-X:foo")), Arrays.asList("-X:foo"));
    assertOrderedEquals(mergeJvmArgs(Arrays.asList("-Dp=val"), Arrays.asList("-Dp=newVal")), Arrays.asList("-Dp=newVal"));

    assertOrderedEquals(
      mergeJvmArgs(Arrays.asList("-X:foo"), Arrays.asList("-Dp=v")),
      Arrays.asList("-X:foo", "-Dp=v"));

    assertOrderedEquals(
      mergeJvmArgs(Arrays.asList("-X:foo", "-Foo", "bar=001", "-Foo", "baz=002"),
                   Arrays.asList("-Dp=v", "-Foo", "bar=003", "-Foo", "baz=002")),
      Arrays.asList("-X:foo", "-Foo", "bar=003", "-Foo", "baz=002", "-Dp=v"));


    List<String> jvmArgs = mergeJvmArgs(null,
                                        Arrays.asList("-Xmx256", "--add-opens", "java.base/java.util=ALL-UNNAMED"),
                                        Arrays.asList("-Xmx512", "--add-opens", "java.base/java.lang=ALL-UNNAMED"));
    assertDoesntContain(jvmArgs, "-Xmx256", "--add-opens", "java.base/java.util=ALL-UNNAMED", "java.base/java.lang=ALL-UNNAMED");
    assertContainsElements(jvmArgs, "-Xmx512");
  }

  @Test
  public void testCommandLineTestArgsMerge() {
    MultiMap<String, String> r = extractTestCommandOptions(Collections.emptyList());

    assertTrue("Expecting empty result for empty arg", r.isEmpty());

    r = extractTestCommandOptions(asList("task1"));
    assertContainsElements(r.get("task1"), "*");

    r = extractTestCommandOptions(asList("task1", "task2"));
    assertContainsElements(r.get("task1"), "*");
    assertContainsElements(r.get("task2"), "*");

    r = extractTestCommandOptions(asList("t1", "t2", "t3", "--tests", "my.test.name"));
    assertContainsElements(r.get("t1"), "*");
    assertContainsElements(r.get("t2"), "*");
    assertContainsElements(r.get("t3"), "my.test.name");

    List<String> args = asList("t1", "--tests", "my.test1", "--tests", "my.test2", "--tests", "my.test3",
                               "t2", "--tests", "my2.test1", "--tests", "my2.test2", "--init-script", "a.init.gradle",
                               "--init-script", "b.init.gradle",
                               "t3",
                               "--info",
                               "t4", "--tests", "my4.test1",
                               "-s");
    r = extractTestCommandOptions(args);

    assertContainsElements(r.get("t1"), "my.test1", "my.test2", "my.test3");
    assertContainsElements(r.get("t2"), "my2.test1", "my2.test2");
    assertContainsElements(r.get("t3"), "*");
    assertContainsElements(r.get("t4"), "my4.test1");

    assertContainsElements(args, "--init-script", "a.init.gradle", "--init-script", "b.init.gradle", "--info", "-s");
  }

  private static List<String> asList(String... strings) {
    ArrayList<String> list = new ArrayList<>();
    Collections.addAll(list, strings);
    return list;
  }
}
