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
package org.jetbrains.plugins.groovy.util;

import com.intellij.util.containers.ConcurrentHashMap;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

/**
 * @author Max Medvedev
 */
public class ResolveProfiler {
  private static final ThreadLocal<String> prefix = new ThreadLocal<String>();

  private static final Map<Thread, String> threadMap = new ConcurrentHashMap<Thread, String>();
  private static volatile int fileCount = 0;

  public static void start() {
    final String cur = prefix.get();
    if (cur == null) {
      prefix.set("  ");
    }
    else {
      prefix.set("  " + cur);
    }
  }

  public static void finish() {
    final String cur = prefix.get();
    if (cur == null) {
      assert false;
    }
    else {
      prefix.set(cur.substring(2));
    }
  }

  public static void write(String s) {
    String name = getName();
    try {
      final FileWriter writer = new FileWriter(name, true);
      try {
        writer.write(prefix.get());
        writer.write(s);
        writer.write('\n');
      }
      finally {
        writer.close();
      }
    }
    catch (IOException e) {
      e.printStackTrace();
    }

    if (prefix.get().length() == 0) {
      synchronized (ResolveProfiler.class) {
        threadMap.remove(Thread.currentThread());
      }
    }
  }

  private synchronized static String getName() {
    String name = threadMap.get(Thread.currentThread());
    if (name == null) {
      name = "out" + fileCount + ".txt";
      fileCount++;
      threadMap.put(Thread.currentThread(), name);
    }
    return name;
  }
}
