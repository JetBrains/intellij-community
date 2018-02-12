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
@SuppressWarnings("ALL")
public class JDBCResourceInspectionTest extends LightInspectionTestCase {

  public void testCorrectClose() {
    doTest("import java.sql.*;" +
           "class X {" +
           "    public void m(Driver driver) throws SQLException {" +
           "        Connection connection = connection = driver.connect(\"foo\", null);\n" +
           "        try {" +
           "            System.out.println(connection);" +
           "        } finally {" +
           "            connection.close();" +
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
           "    driver./*'Connection' should be opened in front of a 'try' block and closed in the corresponding 'finally' block*/connect/**/(\"jdbc\", null);" +
           "  }" +
           "}");
  }

  public void testPreparedStatement() {
    doTest("import java.sql.*;" +
           "class X {" +
           "  void m(PreparedStatement s) throws SQLException {" +
           "    ResultSet r = s./*'ResultSet' should be opened in front of a 'try' block and closed in the corresponding 'finally' block*/executeQuery/**/();" +
           "  }" +
           "}");
  }

  @Override
  protected LocalInspectionTool getInspection() {
    return new JDBCResourceInspection();
  }
}
