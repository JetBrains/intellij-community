// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.build;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.ApiStatus;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.intellij.openapi.util.text.StringUtil.splitByLines;

@ApiStatus.Internal
class GradleImprovedHotswapOutput {
  private static final Logger LOG = Logger.getInstance(GradleImprovedHotswapOutput.class);

  private static final String PATH_PREFIX = "path:";
  private static final String ROOT_PREFIX = "root:";

  private final String root;
  private final String path;

  private GradleImprovedHotswapOutput(String root, String path) {
    this.root = root;
    this.path = path;
  }

  public String getRoot() {
    return root;
  }

  public String getPath() {
    return path;
  }

  public static Collection<GradleImprovedHotswapOutput> parseOutputFile(File file) {
    try {
      String content = FileUtil.loadFile(file);

      Iterator<String> lines = Arrays.stream(splitByLines(content, true))
        .filter(line -> !line.isBlank())
        .iterator();

      List<GradleImprovedHotswapOutput> outputs = new ArrayList<>();
      while (lines.hasNext()) {
        String root = lines.next();
        if (!lines.hasNext()) {
          LOG.error("Expected Gradle Hotswap output to contain even number of lines");
          LOG.debug("Gradle Hotswap output file:\n{}", content);
          break;
        }
        String path = lines.next();

        if (root.startsWith(ROOT_PREFIX) && path.startsWith(PATH_PREFIX)) {
          outputs.add(new GradleImprovedHotswapOutput(
            root.substring(ROOT_PREFIX.length()),
            path.substring(PATH_PREFIX.length())
          ));
        }
        else {
          LOG.error(String.format("Unexpected Gradle Hotswap output format. " +
                                  "Expected '%s' to start with '%s' and '%s' to start with '%s'",
                                  root, ROOT_PREFIX, path, PATH_PREFIX));
        }
      }
      return outputs;
    }
    catch (IOException e) {
      LOG.warn("Can not load temp file with collected Gradle tasks output paths", e);
      return Collections.emptyList();
    }
  }
}
