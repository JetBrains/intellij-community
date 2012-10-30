/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.idea;

import java.io.*;

/**
 * @author max
 */
public class Launcher {
  private Launcher() {
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
    public static void main(String[] args) throws InterruptedException {
        String javaVmExecutablePath = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        String classpath = System.getProperty("java.class.path");
        try {
            final Process process = Runtime.getRuntime().exec("cmd " + javaVmExecutablePath + " -cp " + classpath + " com.intellij.idea.Main");
            new Thread(new Redirector(process.getErrorStream(), System.err)).start();
            new Thread(new Redirector(process.getInputStream(), System.out)).start();
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

        public Redirector(InputStream input, PrintStream output) {
            myInput = input;
            myOutput = output;
        }

        @Override
        public void run() {
            Reader reader = new InputStreamReader(myInput);
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
