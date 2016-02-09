/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
public class MemoryDumpHelper {
  private static final String HOT_SPOT_BEAN_NAME = "com.sun.management:type=HotSpotDiagnostic";

  private static final Object ourMXBean;
  private static final Method ourDumpHeap;

  static {
    Object mxBean;
    Method dumpHeap;

    try {
      final Class hotSpotMxBeanClass = Class.forName("com.sun.management.HotSpotDiagnosticMXBean");

      mxBean = AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
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
      Logger.getInstance("#com.intellij.util.MemoryDumpHelper").info(t.getMessage());
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

  public static synchronized void captureMemoryDumpZipped(@NotNull String zipPath) throws Exception {
    File tempFile = FileUtil.createTempFile("heapDump.", ".hprof");
    FileUtil.delete(tempFile);

    captureMemoryDump(tempFile.getPath());

    ZipUtil.compressFile(tempFile, new File(zipPath));
    FileUtil.delete(tempFile);
  }
}
