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
package com.intellij.util.io.zip;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.lang.JarMemoryLoader;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author anna
 * @since 23-Apr-2009
 */
@SuppressWarnings("CallToPrintStackTrace")
public class ReorderJarsMain {
  private ReorderJarsMain() { }

  public static void main(String[] args) {
    try {
      final String orderTxtPath = args[0];
      final String jarsPath = args[1];
      final String destinationPath = args[2];
      final String libPath = args.length > 3 ? args[3] : null;

      final Map<String, List<String>> toReorder = getOrder(new File(orderTxtPath));
      final Set<String> ignoredJars = libPath == null ? Collections.<String>emptySet() : loadIgnoredJars(libPath);

      for (String jarUrl : toReorder.keySet()) {
        if (ignoredJars.contains(StringUtil.trimStart(jarUrl, "/lib/"))) continue;
        if (jarUrl.startsWith("/lib/ant")) continue;

        final File jarFile = new File(jarsPath, jarUrl);
        if (!jarFile.isFile()) continue;

        final JBZipFile zipFile = new JBZipFile(jarFile);
        final List<JBZipEntry> entries = zipFile.getEntries();
        final List<String> orderedEntries = toReorder.get(jarUrl);
        assert orderedEntries.size() <= Short.MAX_VALUE : jarUrl;
        Collections.sort(entries, new Comparator<JBZipEntry>() {
          @Override
          public int compare(JBZipEntry o1, JBZipEntry o2) {
            if ("META-INF/plugin.xml".equals(o2.getName())) return Integer.MAX_VALUE;
            if ("META-INF/plugin.xml".equals(o1.getName())) return -Integer.MAX_VALUE;
            if (orderedEntries.contains(o1.getName())) {
              return orderedEntries.contains(o2.getName()) ? orderedEntries.indexOf(o1.getName()) - orderedEntries.indexOf(o2.getName()) : -1;
            }
            else {
              return orderedEntries.contains(o2.getName()) ? 1 : 0;
            }
          }
        });

        final File tempJarFile = FileUtil.createTempFile("__reorder__", "__reorder__", true);
        final JBZipFile file = new JBZipFile(tempJarFile);

        final JBZipEntry sizeEntry = file.getOrCreateEntry(JarMemoryLoader.SIZE_ENTRY);
        sizeEntry.setData(ZipShort.getBytes(orderedEntries.size()));

        for (JBZipEntry entry : entries) {
          final JBZipEntry zipEntry = file.getOrCreateEntry(entry.getName());
          zipEntry.setData(entry.getData());
        }
        file.close();

        final File resultJarFile = new File(destinationPath, jarUrl);
        final File resultDir = resultJarFile.getParentFile();
        if (!resultDir.isDirectory() && !resultDir.mkdirs()) {
          throw new IOException("Cannot create: " + resultDir);
        }
        try {
          FileUtil.rename(tempJarFile, resultJarFile);
        }
        catch (Exception e) {
          FileUtil.delete(resultJarFile);
          throw e;
        }
        FileUtil.delete(tempJarFile);
      }
    }
    catch (Throwable t) {
      t.printStackTrace();
      System.exit(1);
    }
  }

  private static Set<String> loadIgnoredJars(String libPath) throws IOException {
    final File ignoredJarsFile = new File(libPath, "required_for_dist.txt");
    final Set<String> ignoredJars = new HashSet<String>();
    ContainerUtil.addAll(ignoredJars, FileUtil.loadFile(ignoredJarsFile).split("\r\n"));
    return ignoredJars;
  }

  private static Map<String, List<String>> getOrder(final File loadingFile) throws IOException {
    final Map<String, List<String>> entriesOrder = new HashMap<String, List<String>>();
    final String[] lines = FileUtil.loadFile(loadingFile).split("\n");
    for (String line : lines) {
      line = line.trim();
      final int i = line.indexOf(":");
      if (i != -1) {
        final String entry = line.substring(0, i);
        final String jarUrl = line.substring(i + 1);
        List<String> entries = entriesOrder.get(jarUrl);
        if (entries == null) {
          entries = new ArrayList<String>();
          entriesOrder.put(jarUrl, entries);
        }
        entries.add(entry);
      }
    }
    return entriesOrder;
  }
}
