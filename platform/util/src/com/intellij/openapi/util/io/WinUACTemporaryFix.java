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
package com.intellij.openapi.util.io;

import com.intellij.openapi.application.PathManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
* @author gregsh
*/
class WinUACTemporaryFix {
  public static void main(String[] args) throws Exception {
    String command = args[0];
    if ("copy".equals(command)) {
      File fromFile = new File(args[1]);
      File toFile = new File(args[2]);
      boolean syncTimestamp = Boolean.parseBoolean(args[3]);
      boolean result = execExternalProcess(new String[]{"cmd.exe", "/C", "copy", fromFile.getPath(), toFile.getPath()});
      if (result && syncTimestamp && toFile.exists()) {
        long lastModified = fromFile.lastModified();
        if (lastModified >= 0) {
          toFile.setLastModified(lastModified);
        }
      }
      System.exit(result ? 0 : 1);
    }
  }

  private static boolean execExternalProcess(String[] args) throws IOException, InterruptedException {
    Process process = Runtime.getRuntime().exec(args);

    Thread outThread = new Thread(new StreamRedirector(process.getInputStream(), System.out),"winua redirector out");
    Thread errThread = new Thread(new StreamRedirector(process.getErrorStream(), System.err),"winua redirector err");
    outThread.start();
    errThread.start();

    try {
      process.waitFor();
    }
    finally {
      outThread.join();
      errThread.join();
    }
    return process.exitValue() == 0;
  }

  static boolean nativeCopy(File fromFile, File toFile, boolean syncTimestamp) {
    File launcherFile = new File(PathManager.getBinPath(), "vistalauncher.exe");
    try {
      // todo vistalauncher should be replaced with generic "elevate" process
      // todo   so the second java process will be unnecessary: plain 'elevate cmd /C copy' will work
      return execExternalProcess(new String[]{launcherFile.getPath(),
        //"cmd",  "/C", "move", fromFile.getPath(),
        //toFile.getPath()

        System.getProperty("java.home") + "/bin/java",
        "-classpath",
        PathManager.getLibPath() + "/util.jar",
        WinUACTemporaryFix.class.getName(),
        "copy",
        fromFile.getPath(),
        toFile.getPath(),
        String.valueOf(syncTimestamp),
        // vistalauncher hack
        "install",
        toFile.getParent()
      });
    }
    catch (Exception ex) {
      return false;
    }
  }

  static class StreamRedirector implements Runnable {
    private final InputStream myIn;
    private final OutputStream myOut;

    private StreamRedirector(InputStream in, OutputStream out) {
      myIn = in;
      myOut = out;
    }

    @Override
    public void run() {
      try {
        StreamUtil.copyStreamContent(myIn, myOut);
      }
      catch (IOException e) {
        e.printStackTrace();
      }
    }
  }
}
