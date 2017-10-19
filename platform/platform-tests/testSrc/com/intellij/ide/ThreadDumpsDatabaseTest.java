/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.intellij.ide;

import com.google.common.base.Charsets;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class ThreadDumpsDatabaseTest {
  @Rule
  public TemporaryFolder myTestFolder = new TemporaryFolder();

  @Test
  public void testParser() throws IOException {
    ThreadDumpsDatabase db = new ThreadDumpsDatabase(new File(myTestFolder.getRoot(), "threads.dmp"));

    Path t1 = createTempFileWithThreadDump("1");
    Path t2 = createTempFileWithThreadDump("1");

    db.appendThreadDump(t1);
    db.appendThreadDump(t2);

    List<Path> paths = db.reapThreadDumps();
    assertThat(paths, hasItems(t1, t2));

    paths = db.reapThreadDumps();
    assertTrue(paths.isEmpty());
  }

  @NotNull
  private Path createTempFileWithThreadDump(@NotNull String contents) throws IOException {
    File file = myTestFolder.newFile();
    Files.write(file.toPath(), contents.getBytes(Charsets.UTF_8), StandardOpenOption.CREATE);
    return file.toPath();
  }
}
