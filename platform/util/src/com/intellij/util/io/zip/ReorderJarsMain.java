// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.zip;

import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.lang.JarMemoryLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * @author anna
 */
@SuppressWarnings("CallToPrintStackTrace")
public final class ReorderJarsMain {
  private ReorderJarsMain() { }

  public static void main(String[] args) {
    try {
      final String orderTxtPath = args[0];
      Path jarDir = Paths.get(args[1]);
      Path destinationPath = Paths.get(args[2]);
      final String libPath = args.length > 3 ? args[3] : null;

      Map<String, List<String>> toReorder = getOrder(Paths.get(orderTxtPath));
      Set<String> ignoredJars = libPath == null ? Collections.emptySet() : new LinkedHashSet<>(Files.readAllLines(Paths.get(libPath, "required_for_dist.txt")));

      ExecutorService executor = Executors.newWorkStealingPool(Runtime.getRuntime().availableProcessors() > 2 ? 4 : 2);
      AtomicReference<Throwable> errorReference = new AtomicReference<>();
      for (String jarUrl : toReorder.keySet()) {
        if (errorReference.get() != null) {
          break;
        }

        if (ignoredJars.contains(StringUtil.trimStart(jarUrl, "/lib/")) || jarUrl.startsWith("/lib/ant")) {
          System.out.println("Ignored jar: " + jarUrl);
          return;
        }

        Path jarFile = jarDir.resolve(StringUtil.trimStart(jarUrl, "/"));
        if (!Files.exists(jarFile)) {
          System.out.println("Cannot find jar: " + jarFile);
          return;
        }

        executor.execute(() -> {
          if (errorReference.get() != null) {
            return;
          }

          System.out.println("Reorder jar: " + jarFile);
          try {
            reorderJar(jarFile, destinationPath, toReorder, jarUrl);
          }
          catch (Throwable e) {
            errorReference.compareAndSet(null, e);
          }
        });
      }
      executor.shutdown();

      Throwable error = errorReference.get();
      if (error != null) {
        error.printStackTrace();
        System.exit(1);
      }

      executor.awaitTermination(8, TimeUnit.MINUTES);
    }
    catch (Throwable t) {
      t.printStackTrace();
      System.exit(1);
    }
  }

  private static final class Item {
    final String name;
    final FileTime lastModifiedTime;
    final byte[] data;

    Item(String name, FileTime lastModifiedTime, byte[] data) {
      this.name = name;
      this.lastModifiedTime = lastModifiedTime;
      this.data = data;
    }
  }

  private static void reorderJar(@NotNull Path jarFile,
                                 @NotNull Path destinationPath,
                                 @NotNull Map<String, List<String>> toReorder,
                                 @NotNull String jarUrl) throws IOException {
    List<String> orderedEntries = toReorder.get(jarUrl);
    assert orderedEntries.size() <= Short.MAX_VALUE : jarUrl;

    List<Item> entries = new ArrayList<>();
    try (ZipInputStream in = new ZipInputStream(new BufferedInputStream(Files.newInputStream(jarFile), 32_000))) {
      ZipEntry entry;
      while ((entry = in.getNextEntry()) != null) {
        if (entry.isDirectory()) {
          continue;
        }

        entries.add(new Item(entry.getName(), entry.getLastModifiedTime(), FileUtilRt.loadBytes(in, (int)entry.getSize())));
      }
    }

    entries.sort((o1, o2) -> {
      String o2p = o2.name;
      if ("META-INF/plugin.xml".equals(o2p)) {
        return Integer.MAX_VALUE;
      }

      String o1p = o1.name;
      if ("META-INF/plugin.xml".equals(o1p)) {
        return -Integer.MAX_VALUE;
      }

      if (orderedEntries.contains(o1p)) {
        return orderedEntries.contains(o2p)
               ? orderedEntries.indexOf(o1p) - orderedEntries.indexOf(o2p)
               : -1;
      }
      else {
        return orderedEntries.contains(o2p) ? 1 : 0;
      }
    });

    Path resultJarFile = destinationPath.resolve(StringUtil.trimStart(jarUrl, "/"));
    Path resultDir = resultJarFile.getParent();
    Files.createDirectories(resultDir);

    Path tempJarFile = resultJarFile.resolveSibling(resultJarFile.getFileName() + "_reorder.jar");
    try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(tempJarFile,
                                                                                                  StandardOpenOption.CREATE_NEW,
                                                                                                  StandardOpenOption.WRITE), 32_000))) {
      addFile(out, JarMemoryLoader.SIZE_ENTRY, ZipShort.getBytes(orderedEntries.size()), null);
      for (Item entry : entries) {
        addFile(out, entry.name, entry.data, entry.lastModifiedTime);
      }
    }

    try {
      Files.move(tempJarFile, resultJarFile);
    }
    catch (Exception e) {
      Files.deleteIfExists(resultJarFile);
      throw e;
    }
    finally {
      Files.deleteIfExists(tempJarFile);
    }
  }

  private static void addFile(@NotNull ZipOutputStream out, @NotNull String entryName, byte @NotNull [] content, @Nullable FileTime lastModifiedTime) throws IOException {
    ZipEntry e = new ZipEntry(entryName);
    if (lastModifiedTime != null) {
      e.setLastModifiedTime(lastModifiedTime);
    }
    out.putNextEntry(e);
    out.write(content);
    out.closeEntry();
  }

  private static @NotNull Map<String, List<String>> getOrder(@NotNull Path loadingFile) throws IOException {
    Map<String, List<String>> entriesOrder = new LinkedHashMap<>();
    for (String line : Files.readAllLines(loadingFile)) {
      line = line.trim();
      final int i = line.indexOf(":");
      if (i != -1) {
        final String entry = line.substring(0, i);
        final String jarUrl = line.substring(i + 1);
        List<String> entries = entriesOrder.get(jarUrl);
        if (entries == null) {
          entries = new ArrayList<>();
          entriesOrder.put(jarUrl, entries);
        }
        entries.add(entry);
      }
    }
    return entriesOrder;
  }
}
