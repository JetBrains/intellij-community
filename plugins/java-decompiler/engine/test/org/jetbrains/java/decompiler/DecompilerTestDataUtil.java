// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class DecompilerTestDataUtil {
  private DecompilerTestDataUtil() {
  }

  public static Path findTestDataDir() {
    List<String> roots = new ArrayList<>();
    String ideaHome = System.getProperty("idea.home.path");
    if (ideaHome != null) {
      roots.add(ideaHome + "/community/plugins/java-decompiler/engine/testData");
      roots.add(ideaHome + "/plugins/java-decompiler/engine/testData");
    }
    roots.addAll(List.of(
      "testData",
      "community/plugins/java-decompiler/engine/testData",
      "plugins/java-decompiler/engine/testData",
      "../community/plugins/java-decompiler/engine/testData",
      "../plugins/java-decompiler/engine/testData"
    ));
    for (String rel : roots) {
      Path dir = Path.of(rel);
      if (Files.isDirectory(dir) && Files.isDirectory(dir.resolve("manual")) && Files.isDirectory(dir.resolve("roundTrip"))) {
        return dir.toAbsolutePath();
      }
    }
    throw new AssertionError("Cannot find 'testData' directory relative to " + Path.of("").toAbsolutePath());
  }
}
