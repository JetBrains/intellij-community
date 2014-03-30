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
public class AutoCloseableResourceInspectionTest extends LightInspectionTestCase {

  public void testCorrectClose() {
    doTest("import java.io.*;" +
           "class X {" +
           "    public void m() throws IOException {" +
           "        FileInputStream str;" +
           "        str = /*'FileInputStream' used without 'try'-with-resources statement*/new FileInputStream(\"bar\")/**/;" +
           "        try {" +
           "        } finally {" +
           "            str.close();" +
           "        }" +
           "    }" +
           "}");
  }

  public void testARM() {
    doTest("import java.sql.*;\n" +
           "class X {\n" +
           "  void m(Driver driver) throws SQLException {\n" +
           "    try (Connection connection = driver.connect(\"jdbc\", null);\n" +
           "      PreparedStatement statement = connection.prepareStatement(\"SELECT *\");\n" +
           "      ResultSet resultSet = statement.executeQuery()) {\n" +
           "      while (resultSet.next()) { resultSet.getMetaData(); }\n" +
           "    }\n" +
           "  }\n" +
           "}");
  }

  public void testSimple() {
    doTest("import java.sql.*;" +
           "class X {" +
           "  void m(Driver driver) throws SQLException {" +
           "    /*'Connection' used without 'try'-with-resources statement*/driver.connect(\"jdbc\", null)/**/;" +
           "  }" +
           "}");
  }

  @Override
  protected LocalInspectionTool getInspection() {
    return new AutoCloseableResourceInspection();
  }
}
