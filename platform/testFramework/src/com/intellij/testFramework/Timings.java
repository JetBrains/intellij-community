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
package com.intellij.testFramework;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;

/**
 * @author peter
 */
public class Timings {

  static {
    long start = System.currentTimeMillis();
    BigInteger k = new BigInteger("1");
    for (int i = 0; i < 1000000; i++) {
      k = k.add(new BigInteger("1"));
    }
    for (int i = 0; i < 42; i++) {
      try {
        final File tempFile = File.createTempFile("test", "test" + i);
        final FileWriter writer = new FileWriter(tempFile);
        for (int j = 0; j < 15; j++) {
          writer.write("test" + j);
          writer.flush();
        }
        writer.close();
        final FileReader reader = new FileReader(tempFile);
        while (reader.read() >= 0) {}
        reader.close();
        tempFile.delete();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    MACHINE_TIMING = System.currentTimeMillis() - start;
  }

  public static final long MACHINE_TIMING;


}
