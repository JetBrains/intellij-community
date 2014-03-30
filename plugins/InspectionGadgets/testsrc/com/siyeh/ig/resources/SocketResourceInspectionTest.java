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
public class SocketResourceInspectionTest extends LightInspectionTestCase {

  public void testNoCloseNoVar() {
    doTest("import java.io.*;" +
           "import java.net.*;" +
           "class X {" +
           "    public void m() throws IOException, UnknownHostException {" +
           "       new /*'Socket' should be opened in front of a 'try' block and closed in the corresponding 'finally' block*/Socket/**/( InetAddress.getLocalHost(), 1);" +
           "    }" +
           "}");
  }

  public void testNoClose() {
    doTest("import java.io.*;" +
           "import java.net.*;" +
           "class X {" +
           "    public void m() throws IOException, UnknownHostException {" +
           "        final Socket socket = new /*'Socket' should be opened in front of a 'try' block and closed in the corresponding 'finally' block*/Socket/**/(InetAddress.getLocalHost(), 1);" +
           "    }" +
           "}");
  }

  public void testTryNoClose() {
    doTest("import java.io.*;" +
           "import java.net.*;" +
           "class X {" +
           "    public void m() throws IOException, UnknownHostException {" +
           "        try {" +
           "            final Socket socket = new /*'Socket' should be opened in front of a 'try' block and closed in the corresponding 'finally' block*/Socket/**/(InetAddress.getLocalHost(), 1);" +
           "        } finally {" +
           "        }" +
           "    }" +
           "}");
  }

  public void testImmediateClose() {
    doTest("import java.io.*;" +
           "import java.net.*;" +
           "class X {" +
           "    public void m() throws IOException {" +
           "        final Socket socket = new Socket(InetAddress.getLocalHost(), 1);" +
           "        socket.close();" +
           "    }" +
           "}");
  }

  public void testCorrectClose() {
    doTest("import java.io.*;" +
           "import java.net.*;" +
           "class X {" +
           "    public void m() throws IOException {" +
           "        Socket socket = new Socket(InetAddress.getLocalHost(), 1);" +
           "        try {" +
           "        } finally {" +
           "            socket.close();" +
           "        }" +
           "    }" +
           "}");
  }

  @Override
  protected LocalInspectionTool getInspection() {
    return new SocketResourceInspection();
  }
}
