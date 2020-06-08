// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.ZipUtil;
import org.jetbrains.annotations.NotNull;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.Iterator;
import java.util.Set;

/**
 * An utility class to capture heap dumps of the current process
 *
 * @author Pavel.Sher
 */
public final class MemoryDumpHelper {
  private static final String HOT_SPOT_BEAN_NAME = "com.sun.management:type=HotSpotDiagnostic";

  private static final Object ourMXBean;
  private static final Method ourDumpHeap;

  static {
    Object mxBean;
    Method dumpHeap;

    try {
      final Class hotSpotMxBeanClass = Class.forName("com.sun.management.HotSpotDiagnosticMXBean");

      mxBean = AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
        @Override
        public Object run() throws Exception {
          MBeanServer server = ManagementFactory.getPlatformMBeanServer();
          Set<ObjectName> s = server.queryNames(new ObjectName(HOT_SPOT_BEAN_NAME), null);
          Iterator<ObjectName> itr = s.iterator();
          if (itr.hasNext()) {
            ObjectName name = itr.next();
            return ManagementFactory.newPlatformMXBeanProxy(server, name.toString(), hotSpotMxBeanClass);
          }
          else {
            return null;
          }
        }
      });

      dumpHeap = mxBean.getClass().getMethod("dumpHeap", String.class, boolean.class);
    }
    catch (Throwable t) {
      Logger.getInstance(MemoryDumpHelper.class).info(t.getMessage());
      mxBean = null;
      dumpHeap = null;
    }

    ourMXBean = mxBean;
    ourDumpHeap = dumpHeap;
  }

  /**
   * @return whether there's an ability to capture heap dumps in this process
   */
  public static boolean memoryDumpAvailable() {
    try {
      return ourMXBean != null;
    }
    catch (UnsupportedOperationException e) {
      return false;
    }
  }

  /**
   * Save a memory dump in a binary format to a file.
   * @param dumpPath the name of the snapshot file
   * @throws Exception
   */
  public static synchronized void captureMemoryDump(@NotNull String dumpPath) throws Exception {
    ourDumpHeap.invoke(ourMXBean, dumpPath, true);
  }

  public static void captureMemoryDumpZipped(@NotNull String zipPath) throws Exception {
    captureMemoryDumpZipped(new File(zipPath));
  }

  public static synchronized void captureMemoryDumpZipped(@NotNull File zipFile) throws Exception {
    File tempFile = FileUtil.createTempFile("heapDump.", ".hprof");
    FileUtil.delete(tempFile);

    captureMemoryDump(tempFile.getPath());

    ZipUtil.compressFile(tempFile, zipFile);
    FileUtil.delete(tempFile);
  }
}
