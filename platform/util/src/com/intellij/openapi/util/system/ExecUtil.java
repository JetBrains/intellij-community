/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.util.system;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ExecUtil {
  private ExecUtil() { }

  public static int execAndGetResult(final String... command) throws IOException, InterruptedException {
    assert command != null && command.length > 0;
    return execAndGetResult(Arrays.asList(command));
  }

  public static int execAndGetResult(@NotNull final List<String> command) throws IOException, InterruptedException {
    assert command.size() > 0;
    final ProcessBuilder processBuilder = new ProcessBuilder(command);
    final Process process = processBuilder.start();
    return process.waitFor();
  }

  public static String loadTemplate(@NotNull final ClassLoader loader,
                                    @NotNull final String templateName,
                                    @Nullable final Map<String, String> variables) throws IOException {
    final InputStream stream = loader.getResourceAsStream(templateName);
    if (stream == null) {
      throw new IOException("Template '" + templateName + "' not found by " + loader);
    }

    final StringBuilder template = new StringBuilder(FileUtil.loadTextAndClose(stream));
    if (variables != null) {
      for (Map.Entry<String, String> var : variables.entrySet()) {
        final String name = var.getKey();
        final int pos = template.indexOf(name);
        if (pos >= 0) {
          template.replace(pos, pos + name.length(), var.getValue());
        }
      }
    }
    return template.toString();
  }

  public static File createTempExecutableScript(@NotNull final String prefix,
                                                @NotNull final String suffix,
                                                @NotNull final String source) throws IOException {
    final File tempFile = FileUtil.createTempFile(prefix, suffix);
    FileUtil.writeToFile(tempFile, source);
    if (!tempFile.setExecutable(true, true)) {
      throw new IOException("Failed to make temp file executable: " + tempFile);
    }
    return tempFile;
  }

  public static int sudoAndGetResult(@NotNull final String scriptPath,
                                     @NotNull final String prompt) throws IOException, ScriptException, InterruptedException {
    if (SystemInfo.isMac) {
      final ScriptEngine engine = new ScriptEngineManager(null).getEngineByName("AppleScript");
      if (engine == null) {
        throw new IOException("Could not find AppleScript engine");
      }
      engine.eval("do shell script \"" + scriptPath + "\" with administrator privileges");
      return 0;
    }
    else if (SystemInfo.isKDE) {
      return execAndGetResult("kdesudo", "--comment", prompt, scriptPath);
    }
    else if (SystemInfo.isGnome) {
      return execAndGetResult("gksudo", "--message", prompt, scriptPath);
    }
    else if (SystemInfo.isUnix) {
      final File sudo = createTempExecutableScript("sudo", ".sh",
                                                   "#!/bin/sh\n" +
                                                   "echo \"" + prompt + "\"\n" +
                                                   "echo\n" +
                                                   "sudo \"" + scriptPath + "\"\n" +
                                                   "STATUS=$?" +
                                                   "echo\n" +
                                                   "read -p \"Press Enter to close this window...\" TEMP\n" +
                                                   "exit $STATUS\n");
      return execAndGetResult("xterm", "-T", "Install", "-e", sudo.getAbsolutePath());
    }
    else {
      throw new UnsupportedOperationException("Unsupported OS/desktop: " + System.getProperty("os.name") + '/' + SystemInfo.SUN_DESKTOP);
    }
  }
}
