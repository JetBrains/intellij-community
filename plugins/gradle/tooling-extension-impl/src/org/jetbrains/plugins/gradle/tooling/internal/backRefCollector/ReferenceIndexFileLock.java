/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.tooling.internal.backRefCollector;

import com.intellij.openapi.util.io.FileUtilRt;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

public class ReferenceIndexFileLock {
  private final static String FILE_NAME = "index.lock";

  private final FileChannel myChannel;
  private final RandomAccessFile myRaf;

  public ReferenceIndexFileLock(File dir) throws FileNotFoundException {
    File lockFile = new File(dir, FILE_NAME);
    myRaf = new RandomAccessFile(lockFile, "rw");
    myChannel = myRaf.getChannel();
  }

  public FileLock lock() throws IOException {
    FileLock lock = myChannel.lock(0, Long.MAX_VALUE, true);
    return lock;
  }

  public static void createLockFile(String dir) throws IOException {
    File lockFile = new File(dir, FILE_NAME);
    FileUtilRt.ensureCanCreateFile(lockFile);
    if (!lockFile.createNewFile()) {
      throw new IOException("can't create file " + lockFile.getPath());
    }
  }

  public static void deleteLockFile(String dir) {
    File lockFile = new File(dir, FILE_NAME);
    FileUtilRt.delete(lockFile);
  }
}
