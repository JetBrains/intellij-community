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
package com.intellij.rt.execution.junit;

import java.io.*;

/**
* User: anna
* Date: 4/6/11
*/
class ForkedVMWrapper extends DataOutputStream {

  private FileOutputStream myOutputStream;
  private boolean myError;

  public ForkedVMWrapper(FileOutputStream outputStream, boolean error) throws FileNotFoundException {
    super(outputStream);
    myOutputStream = outputStream;
    myError = error;
  }

  public synchronized void write(int b) throws IOException {
    printPrefix();
    myOutputStream.write(b);
  }

  private void printPrefix() throws IOException {
    myOutputStream.write("/K".getBytes());
    if (myError) {
      myOutputStream.write("e".getBytes());
    }
    else {
      myOutputStream.write("o".getBytes());
    }
  }

  public void write(byte[] b) throws IOException {
    printPrefix();
    myOutputStream.write(b);
  }

  public synchronized void write(byte[] b, int off, int len) throws IOException {
    printPrefix();
    myOutputStream.write(b, off, len);
  }

  public void close() throws IOException {
    myOutputStream.close();
  }

  public void flush() throws IOException {
    myOutputStream.flush();
  }

  public static void readWrapped(String path, PrintStream out, PrintStream err) throws IOException {
    FileInputStream stream = new FileInputStream(path);
    try {
      boolean error = false;
      boolean afterSymbol = false;
      boolean afterM = false;
      while (stream.available() > 0) {
        char read = (char)stream.read();
        if (read == '/') {
          afterSymbol = true;
          continue;
        }
        if (afterSymbol) {
          if (afterM) {
            error = read == 'e';
            afterSymbol = false;
            afterM = false;
            continue;
          }
          if (read != 'K') {
            if (error) {
              err.write("/".getBytes());
              err.write(read);
            }
            else {
              out.write("/".getBytes());
              out.write(read);
            }
            afterSymbol = false;
            afterM = false;
            continue;
          }
          else {
            afterM = true;
            continue;
          }
        }
        if (error) {
          err.write(read);
        }
        else {
          out.write(read);
        }
      }
    }
    finally {
      if (stream != null) stream.close();
    }
  }
}
