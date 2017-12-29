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
           "        str = new /*'FileInputStream' used without 'try'-with-resources statement*/FileInputStream/**/(\"bar\");" +
           "        try {" +
           "        } finally {" +
           "            str.close();" +
           "        }" +
           "    }" +
           "}");
  }

  public void testEscape() {
    doTest("import java.io.*;" +
           "class X {" +
           "  void m() throws IOException {" +
           "    n(new FileInputStream(\"file.name\"));" +
           "  }" +
           "  void n(Closeable c) {" +
           "    System.out.println(c);" +
           "  }" +
           "}");
  }

  public void testEscape2() {
    final AutoCloseableResourceInspection inspection = new AutoCloseableResourceInspection();
    inspection.anyMethodMayClose = false;
    myFixture.enableInspections(inspection);
    doTest("import java.io.*;" +
           "class X {" +
           "  void m() throws IOException {" +
           "    n(new /*'FileInputStream' used without 'try'-with-resources statement*/FileInputStream/**/(\"file.name\"));" +
           "  }" +
           "  void n(Closeable c) {" +
           "    System.out.println(c);" +
           "  }" +
           "}");
  }

  public void testEscape3() {
    doTest("import java.io.*;" +
           "class X {" +
           "  void m() throws IOException {" +
           "    System.out.println(new FileInputStream(\"file.name\"));" +
           "  }" +
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
           "    driver./*'Connection' used without 'try'-with-resources statement*/connect/**/(\"jdbc\", null);" +
           "  }" +
           "}");
  }

  public void testSystemOut() {
    doTest("class X {" +
           "  void m(String s) {" +
           "    System.out.printf(\"asdf %s\", s);" +
           "    System.err.format(\"asdf %s\", s);" +
           "  }" +
           "}");
  }

  public void testMethodReference() {
    doTest("import java.util.*;" +
           "class X {" +
           "  void m(List<String> list) {" +
           "    final Z<String, Y> f = /*'Y' used without 'try'-with-resources statement*/Y::new/**/;" +
           "  }" +
           "  class Y implements java.io.Closeable {" +
           "    Y(String s) {}" +
           "    public void close() throws java.io.IOException {}" +
           "  }" +
           "  interface Z<T, R> {\n" +
           "    R apply(T t);" +
           "  }" +
           "}");
  }

  public void testFormatter() {
    doTest("import java.util.*;" +
           "class TryWithResourcesFalsePositiveForFormatterFormat {" +
           "    public void useFormatter( Formatter output ) {" +
           "        output.format( \"Hello, world!%n\" );" +
           "    }" +
           "}");
  }

  public void testWriterAppend() {
    doTest("import java.io.*;" +
           "class A {" +
           "    private static void write(Writer writer) throws IOException {" +
           "        writer.append(\"command\");" +
           "    }" +
           "}");
  }

  public void testScanner() {
    doTest("import java.util.Scanner;" +
           "class A {" +
           "    void a() throws java.io.IOException {" +
           "        try (Scanner scanner = new Scanner(\"\").useDelimiter(\"\\\\A\")) {" +
           "            String sconf = scanner.next();" +
           "            System.out.println(sconf);" +
           "        }" +
           "    }" +
           "}" +
           "");
  }

  public void testTernary() {
    doTest("import java.io.*;\n" +
           "\n" +
           "class X {\n" +
           "  private static void example(int a) throws IOException {\n" +
           "    try (FileOutputStream byteArrayOutputStream = a > 0 ? new FileOutputStream(\"/etc/passwd\") : new\n" +
           "      FileOutputStream(\"/etc/shadow\")) {\n" +
           "    }\n" +
           "  }\n" +
           "}");
  }

  @Override
  protected LocalInspectionTool getInspection() {
    return new AutoCloseableResourceInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package java.util;" +
      "public final class Formatter implements java.io.Closeable {" +
      "    public Formatter format(String format, Object ... args) {" +
      "      return this;" +
      "    }" +
      "}",
      "package java.util;" +
      "public final class Scanner implements java.io.Closeable {" +
      "    public Scanner(String source) {}" +
      "    public Scanner useDelimiter(String pattern) {" +
      "         return this;" +
      "    }" +
      "    public String next() {" +
      "        return this;" +
      "    }" +
      "}"
    };
  }
}
