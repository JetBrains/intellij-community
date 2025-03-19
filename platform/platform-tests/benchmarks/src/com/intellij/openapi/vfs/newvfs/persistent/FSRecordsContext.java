// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.io.File;

@State(Scope.Benchmark)
public class FSRecordsContext {

  private File vfsFolder;

  private FSRecordsImpl vfs;

  @Setup
  public void setup() throws Exception {
    vfsFolder = FileUtil.createTempDirectory("FastVFSAttributeAccessBenchmark", "tst", /*deleteOnExit: */ true);
    vfs = FSRecordsImpl.connect(vfsFolder.toPath(),
                                (records, error) -> {
                                  throw new RuntimeException(error);
                                }
    );
  }

  @TearDown
  public void tearDown() throws Exception {
    vfs.close();
    if (vfsFolder != null) {
      FileUtilRt.deleteRecursively(vfsFolder.toPath());
    }

    //IdeEventQueue.applicationClose();
    ShutDownTracker.getInstance().run();
    AppExecutorUtil.shutdownApplicationScheduledExecutorService();
  }

  public File vfsFolder() {
    return vfsFolder;
  }

  public FSRecordsImpl vfs() {
    return vfs;
  }
}