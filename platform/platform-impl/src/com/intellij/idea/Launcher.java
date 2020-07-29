// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import java.io.*;
import java.nio.charset.StandardCharsets;

public final class Launcher {
  private Launcher() {
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
    public static void main(String[] args) throws InterruptedException {
        String javaVmExecutablePath = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        String classpath = System.getProperty("java.class.path");
        try {
            final Process process = Runtime.getRuntime().exec("cmd " + javaVmExecutablePath + " -cp " + classpath + " com.intellij.idea.Main");
            new Thread(new Redirector(process.getErrorStream(), System.err), "Redirector err").start();
            new Thread(new Redirector(process.getInputStream(), System.out), "Redirector out").start();
            process.waitFor();
        } catch (IOException e) {
            System.out.println("Can't launch java VM executable: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("Launcher exited");
    }

    private static class Redirector implements Runnable {
        private final InputStream myInput;
        private final PrintStream myOutput;

        Redirector(InputStream input, PrintStream output) {
            myInput = input;
            myOutput = output;
        }

        @Override
        public void run() {
            Reader reader = new InputStreamReader(myInput, StandardCharsets.UTF_8);
            do {
                int ch = 0;
                try {
                    ch = reader.read();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (ch == -1) break;
                myOutput.print((char) ch);
            } while (true);
        }
    }
}
