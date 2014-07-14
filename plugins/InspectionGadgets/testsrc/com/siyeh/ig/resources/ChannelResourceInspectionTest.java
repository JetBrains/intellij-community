/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ig.resources;

import com.intellij.codeInspection.LocalInspectionTool;
import com.siyeh.ig.LightInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class ChannelResourceInspectionTest extends LightInspectionTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    addEnvironmentClass("package java.nio.channels;" +
                        "public interface Channel extends java.io.Closeable {}");
    addEnvironmentClass("package java.nio.channels;" +
                        "public interface FileChannel extends Channel {}");
    addEnvironmentClass("package java.net;" +
                        "import java.nio.channels.FileChannel;" +
                        "public class SocketInputStream implements java.io.Closeable {" +
                        "  public FileChannel getChannel() {" +
                        "    return null;" +
                        "  }" +
                        "  public void close() throws IOException {}" +
                        "}");
  }

  public void testFactoryClosed() {
    doTest("import java.net.*;" +
           "import java.io.*;" +
           "class X {" +
           "  void m() throws IOException {" +
           "    SocketInputStream ss = new SocketInputStream();" +
           "    try {" +
           "      ss.getChannel();" +
           "    } finally {" +
           "      ss.close();" +
           "    }" +
           "  }" +
           "}");
  }

  public void testSimple() {
    doTest("import java.net.*;" +
           "import java.io.*;" +
           "class X {" +
           "  void m(SocketInputStream ss) throws IOException {" +
           "    ss./*'FileChannel' should be opened in front of a 'try' block and closed in the corresponding 'finally' block*/getChannel/**/();" +
           "  }" +
           "}");
  }

  public void testChannelClosed() {
    doTest("import java.net.*;" +
           "import java.io.*;" +
           "class X {" +
           "  void m(SocketInputStream ss) throws IOException {" +
           "    final Closeable channel = ss.getChannel();" +
           "    try {" +
           "      System.out.println();" +
           "    } finally {" +
           "      channel.close();" +
           "    }" +
           "  }" +
           "}");
  }

  @Override
  protected LocalInspectionTool getInspection() {
    return new ChannelResourceInspection();
  }
}