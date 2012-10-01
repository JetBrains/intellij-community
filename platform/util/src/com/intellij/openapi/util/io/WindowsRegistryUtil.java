/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.util.io;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * User: Vassiliy.Kudryashov
 */
public class WindowsRegistryUtil {
  private WindowsRegistryUtil() {
  }

  @Nullable
  private static String trimToValue(@Nullable StringBuilder output) {
    if (output == null) {
      return null;
    }
    int pos = output.lastIndexOf("  ");
    int pos2 = output.lastIndexOf("\t");
    pos = Math.max(pos, pos2);
    if (pos == -1) {
      return null;
    }

    output.delete(0, pos + 1);
    String blackList = "\r\n \"";
    int startPos = 0;
    int endPos = output.length() - 1;
    while (true) {
      if (startPos >= endPos) {
        return null;
      }
      if (blackList.indexOf(output.charAt(startPos)) != -1) {
        startPos++;
      }
      else {
        break;
      }
    }
    while (true) {
      if (blackList.indexOf(output.charAt(endPos)) != -1) {
        endPos--;
      }
      else {
        break;
      }
    }
    return output.subSequence(startPos, endPos + 1).toString();
  }

  @NotNull
  public static List<String> readRegistryBranch(@NotNull String location) {
    List<String> result = new ArrayList<String>();
    StringBuilder output = readRegistry("reg query \"" + location + "\" /s");
    if (output != null) {
      for (int pos = output.indexOf(location); pos != -1; pos = output.indexOf(location, pos + location.length())) {
        int pos2 = output.indexOf("\r\n", pos + location.length());
        if (pos2 <= pos + location.length()) {
          continue;
        }
        String section = output.substring(pos + location.length() + 1, pos2);
        if (!section.contains("\\")) {
          result.add(section);
        }
      }
    }
    return result;
  }

  @Nullable
  public static String readRegistryDefault(@NotNull String location) {
    return trimToValue(readRegistry("reg query \"" + location + "\" /ve"));
  }

  @Nullable
  public static String readRegistryValue(@NotNull String location, @NotNull String key) {
    return trimToValue(readRegistry("reg query \"" + location + "\" /v " + key));
  }

  @Nullable
  private static StringBuilder readRegistry(String command) {
    try {
      Process process = Runtime.getRuntime().exec(command);
      StringBuilder output = null;
      InputStream is = null;
      ByteArrayOutputStream os = null;
      try {
        byte[] buffer = new byte[128];
        is = process.getInputStream();
        os = new ByteArrayOutputStream();
        for (int length = is.read(buffer); length > 0; length = is.read(buffer)) {
          os.write(buffer, 0, length);
        }
        output = new StringBuilder(new String(os.toByteArray()));
      }
      catch (IOException ignored) {

      }
      finally {
        if (is != null) {
          is.close();
        }
        if (os != null) {
          os.close();
        }
        process.waitFor();
      }

      return output;
    }
    catch (Exception e) {
      return null;
    }
  }
}
