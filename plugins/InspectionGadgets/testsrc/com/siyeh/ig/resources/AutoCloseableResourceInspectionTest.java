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
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 * @noinspection resource
 */
public class AutoCloseableResourceInspectionTest extends LightJavaInspectionTestCase {

  public void testCorrectClose() {
    // No highlighting because str was closed and we have ignoreResourcesWithClose option set
    //noinspection EmptyTryBlock
    doTest("import java.io.*;" +
           "class X {" +
           "    public static void m() throws IOException {" +
           "        FileInputStream str;" +
           "        str = new FileInputStream(\"bar\");" +
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
           "  static void m() throws IOException {" +
           "    n(new FileInputStream(\"file.name\"));" +
           "  }" +
           "  static void n(Closeable c) {" +
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
           "  static void m() throws IOException {" +
           "    n(new /*'FileInputStream' used without 'try'-with-resources statement*/FileInputStream/**/(\"file.name\"));" +
           "  }" +
           "  static void n(Closeable c) {" +
           "    System.out.println(c);" +
           "  }" +
           "}");
  }

  public void testEscape3() {
    doTest("import java.io.*;" +
           "class X {" +
           "  static void m() throws IOException {" +
           "    System.out.println(new FileInputStream(\"file.name\"));" +
           "  }" +
           "}");
  }

  public void testARM() {
    mockSql();
    doTest("import java.sql.*;\n" +
           "class X {\n" +
           "  static void m(Driver driver) throws SQLException {\n" +
           "    try (Connection connection = driver.connect(\"jdbc\", null);\n" +
           "      PreparedStatement statement = connection.prepareStatement(\"SELECT *\");\n" +
           "      ResultSet resultSet = statement.executeQuery()) {\n" +
           "      while (resultSet.next()) { resultSet.getMetaData(); }\n" +
           "    } catch(Exception e) {}\n" +
           "  }\n" +
           "}");
  }

  private void mockSql() {
    addEnvironmentClass("package java.sql;\n" +
                        "public interface Driver { Connection connect(String s, Object o) throws SQLException;}");
    addEnvironmentClass("package java.sql;\n" +
                        "public interface Connection extends AutoCloseable { PreparedStatement prepareStatement(String s);}");
    addEnvironmentClass("package java.sql;\n" +
                        "public interface PreparedStatement extends AutoCloseable { ResultSet executeQuery();}");
    addEnvironmentClass("package java.sql;\n" +
                        "public class SQLException extends Exception {}");
    addEnvironmentClass("package java.sql;\n" +
                        "public interface ResultSet extends AutoCloseable {\n" +
                        "  boolean next();\n" +
                        "  void getMetaData();\n" +
                        "}");
  }

  public void testSimple() {
    mockSql();
    doTest("import java.sql.*;" +
           "class X {" +
           "  static void m(Driver driver) throws SQLException {" +
           "    driver./*'Connection' used without 'try'-with-resources statement*/connect/**/(\"jdbc\", null);" +
           "  }" +
           "}");
  }

  public void testSystemOut() {
    doTest("class X {" +
           "  static void m(String s) {" +
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
           "    @Override public void close() throws java.io.IOException {}" +
           "  }" +
           "  interface Z<T, R> {\n" +
           "    R apply(T t);" +
           "  }" +
           "}");
  }

  public void testFormatter() {
    doTest("import java.util.*;" +
           "class TryWithResourcesFalsePositiveForFormatterFormat {" +
           "    public static void useFormatter( Formatter output ) {" +
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
           "    static void a() throws java.io.IOException {" +
           "        try (Scanner scanner = new Scanner(\"\").useDelimiter(\"\\\\A\")) {" +
           "            String sconf = scanner.next();" +
           "            System.out.println(sconf);" +
           "        }" +
           "    }" +
           "}" +
           "");
  }

  public void testTernary() {
    //noinspection EmptyTryBlock
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

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  public void testFilesMethod() {
    addEnvironmentClass("package java.nio.file;\n" +
                        "import java.util.stream.Stream;\n" +
                        "public final class Files {\n" +
                        "  public static Stream<String> lines(Path path) {return Stream.empty();}\n" +
                        "}");
    doTest("import java.io.*;\n" +
           "import java.nio.file.Files;\n" +
           "import java.util.stream.Stream;\n" +
           "class X {\n" +
           "  private static void example(int a)  {\n" +
           "    Stream<String> s = Files.<warning descr=\"'Stream<String>' used without 'try'-with-resources statement\">lines</warning>(null);\n" +
           "  }\n" +
           "}");
  }

  public void testClosedResource() {
    doTest("import java.io.*;\n" +
           "\n" +
           "class X {\n" +
           "  private static void example(int a) throws IOException {\n" +
           "    new FileOutputStream(\"\").close();\n" +
           "  }\n" +
           "}");
  }

  public void testContractParameter() {
    doTest("import java.io.*;\n" +
           "\n" +
           "class X {\n" +
           "  private static void example(Object o) throws IOException {\n" +
           "    InputStream is = o.getClass().<warning descr=\"'InputStream' used without 'try'-with-resources statement\">getResourceAsStream</warning>(\"\");\n" +
           "        InputStream is2 = test(is);\n" +
           "  }\n" +
           "\n" +
           "  static <T> T test(T t) {\n" +
           "    return t;\n" +
           "  }\n" +
           "}");
  }

  public void testContractThis() {
    doTest("import java.io.*;\n" +
           "\n" +
           "class X implements Closeable {\n" +
           "  @Override\n" +
           "  public void close() throws IOException {}\n" +
           "\n" +
           "  private static void example(Object o) throws IOException {\n" +
           "        new <warning descr=\"'X' used without 'try'-with-resources statement\">X</warning>().doX();\n" +
           "  }\n" +
           "\n" +
           "  private X doX() {\n" +
           "    return this;\n" +
           "  }\n" +
           "}");
  }

  public void testCallThenClose() {
    // No highlighting because str was closed and we have ignoreResourcesWithClose option set
    doTest("import java.io.*;\n" +
           "\n" +
           "class X implements AutoCloseable {\n" +
           "  @Override\n" +
           "  public void close() {}\n" +
           "  static native X makeX();\n" +
           "}\n" +
           "class Other {\n" +
           "  private static void example() {\n" +
           "    final X x = X.makeX();\n" +
           "    x.close();\n" +
           "  }\n" +
           "}");
  }


  public void testLambdaReturnsResource() {
    doTest("import java.io.*;\n" +
           "\n" +
           "class X implements AutoCloseable {\n" +
           "  @Override\n" +
           "  public void close() {}\n" +
           "\n" +
           "  private static void example() {\n" +
           "    consume(() -> new X());\n" +
           "  " +
           "}\n" +
           "  \n" +
           "  interface Consumer {" +
           " X use();" +
           "}\n" +
           "  private static native X getX();\n" +
           "  private static native void consume(Consumer x);\n" +
           "}");
  }

  public void testLambdaNotReturnsResource() {
    doTest("import java.io.*;\n" +
           "\n" +
           "class X implements AutoCloseable {\n" +
           "  @Override\n" +
           "  public void close() {}\n" +
           "\n" +
           "  private static void example() {\n" +
           "    consume(() -> new <warning descr=\"'X' used without 'try'-with-resources statement\">X</warning>());\n" +
           "  " +
           "}\n" +
           "  \n" +
           "  interface Runnable {" +
           " void run();" +
           "}\n" +
           "  private static native X getX();\n" +
           "  private static native void consume(Runnable x);\n" +
           "}");
  }


  public void testResourcePassedToConstructorOfResource() {
    doTest("import java.io.*;\n" +
           "\n" +
           "class X implements AutoCloseable {\n" +
           "  X(X other) {}\n" +
           "  X() {}\n" +
           "  @Override public void close() {}\n" +
           "  private static void example(X other) {\n" +
           "    new X(other);\n" +
           "  " +
           "}\n" +
           "}");
  }

  public void testCreatedResourcePassedToConstructor() {
    doTest("import java.io.*;\n" +
           "\n" +
           "class X implements AutoCloseable {\n" +
           "  X(X other) {}\n" +
           "  X() {}\n" +
           "  @Override public void close() {}\n" +
           "  private static void example(X other) {\n" +
           "    new X(new X());\n" +
           "  " +
           "}\n" +
           "}");
  }

  public void testCreatedResourcePassedToConstructorAsVar() {
    doTest("import java.io.*;\n" +
           "\n" +
           "class X implements AutoCloseable {\n" +
           "  X(X other) {}\n" +
           "  X() {}\n" +
           "  @Override public void close() {}\n" +
           "  private static void example(X other) {\n" +
           "    X resource = new X();\n" +
           "    new X(resource);\n" +
           "  " +
           "}\n" +
           "}");
  }

  public void testResourceAssigned() {
    doTest(
      "class X implements AutoCloseable {\n" +
      "  @Override public void close() {}\n" +
      "  private static X example(boolean cond, X other) {\n" +
           "    X x;\n" +
      "    if (cond) {\n" +
      "      x = new X();\n" +
      "    " +
      "} else {\n" +
      "      x = other;\n" +
      "    " +
      "}\n" +
      "    return x;\n" +
      "  " +
      "}\n" +
           "}");
  }

  public void testResourceObjectMethodReturnsAnotherResource() {
    // Expect error in case when it happens inside the resource itself
    doTest(
      "class X implements AutoCloseable {\n" +
      "  @Override public void close() {}\n" +
      "  private static void example() {\n" +
      "    <warning descr=\"'X' used without 'try'-with-resources statement\">createPossiblyDependantResource</warning>();\n" +
      "  " +
      "}\n" +
      "  private static X createPossiblyDependantResource() { return null; }\n" +
      "}");
  }

  public void testResourceEscapesToConstructor() {
    doTest(
      "class X implements AutoCloseable {\n" +
      "  @Override public void close() {}\n" +
      "  private static void example() {\n" +
      "    X x = createX();\n" +
      "    if (x != null) {\n" +
      "      new EscapeTo(10, x);\n" +
      "    " +
      "}\n" +
      "  }\n" +
      "  native static X createX();\n" +
      "}\n" +
      "class EscapeTo {\n" +
      "  X x;\n" +
      " " +
      " EscapeTo(int y, X x) {" +
      "this.x = x;" +
      "}\n" +
      "  native void doStuff();\n" +
      "}");
  }

  public void testIgnoredStreamPassedAsArgumentToNonIgnored() {
    doTest(
      "import java.io.ByteArrayOutputStream;\n" +
      "import java.io.IOException;\n" +
      "import java.io.ObjectOutputStream;\n" +
      "\n" +
      "class Test{\n" +
      "  void test() throws IOException {\n" +
      "    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();\n" +
      "    ObjectOutputStream oos;\n" +
      "    oos = new ObjectOutputStream(outputStream);\n" +
      "  }\n" +
      "}");
  }

  public void testConstructorClosesResource() {
    doTest(
      "import java.io.ByteArrayOutputStream;\n" +
      "import java.io.IOException;\n" +
      "import java.io.ObjectOutputStream;\n" +
      "\n" +
      "class Test{\n" +
      "  Test(AutoCloseable ac){}\n" +
      "  " +
      "void test(){\n" +
      "    AC ac = new AC();\n" +
      "    new Test(ac);\n" +
      "  }\n" +
      "}\n" +
      "class AC implements AutoCloseable {\n" +
      "  " +
      "@Override" +
      " public void close(){" +
      "}\n" +
      "}");
  }

  public void testResourceClosed() {
    doTest(
      "class Test{\n" +
      "  " +
      "void test(){\n" +
      "    AC ac = ACHolder.makeAc();\n" +
      "    useAc(new ACHolder(ac));\n" +
      "  }\n" +
      "  void useAc(ACHolder holder) {}\n" +
      "}\n" +
      "class ACHolder {\n" +
      "  private final AC ac;\n" +
      "  public static AC makeAc() { return null;}\n" +
      "  ACHolder(AC ac) {this.ac = ac;" +
      "}\n" +
      "}\n" +
      "class AC implements AutoCloseable {\n" +
      "  " +
      "@Override" +
      " public void close(){" +
      "}\n" +
      "}");
  }

  public void testGetMethodNotConsideredAsResource() {
    doTest(
      "class Test{\n" +
      "  " +
      "void test(){\n" +
      "    getAc();\n" +
      "  }\n" +
      "  AC getAc() {return null;}\n" +
      "}\n" +
      "class AC implements AutoCloseable {\n" +
      "  " +
      "@Override" +
      " public void close(){" +
      "}\n" +
      "}");
  }

  public void testBuilderMustNotBeTriggered() {
    doTest(
      "class Test{\n" +
      "  " +
      "void test(){\n" +
      "    AC ac = makeAc();\n" +
      "    ac" +
      ".use();\n" +
      "    ac.close();\n" +
      "  }\n" +
      "  AC makeAc() {return null;}\n" +
      "}\n" +
      "class AC implements AutoCloseable {\n" +
      "  AC use() {return this; }\n" +
      "  " +
      "@Override" +
      " public void close(){" +
      "}\n" +
      "}");
  }

  public void testFieldInitializationAsEscape() {
    doTest(
      "class AC implements AutoCloseable {\n" +
      "  AC ac = new AC();\n" +
      "  @Override" +
      " public void close(){" +
      "}\n" +
      "}");
  }

  public void testTryAsReference() {
    doTest(
      "class AC implements AutoCloseable {\n" +
      "  void test(boolean condition) {\n" +
      "    AC ac = condition ? new AC() : null;\n" +
      "    if (ac == null) return;\n" +
      "    try (AC ac1 = ac) {\n" +
      "      ac1.test(false);\n" +
      "    " +
      "}\n" +
      "  }\n" +
      "  @Override" +
      " public void close(){" +
      "}\n" +
      "}");
  }

  public void testClose() {
    doTest(
      "class AC implements AutoCloseable {\n" +
      "  void test() {\n" +
      "    final AC ac = new AC();\n" +
      "    try {\n" +
      "      work();\n" +
      "    " +
      "} finally {\n" +
      "      ac.close();\n" +
      "    " +
      "}\n" +
      "  " +
      "}\n" +
      "  native void work();\n" +
      "  @Override" +
      " public void close(){" +
      "}\n" +
      "}");
  }

  public void test232779() {
    doTest(
      "class AutoCloseableSample {\n" +
      "  public OAuth2AccessToken authenticateOAuth(String code) {\n" +
      "    OAuth20Service service = <warning descr=\"'OAuth20Service' used without 'try'-with-resources statement\">makeOAuth2Service</warning>();\n" +
      "    try {\n" +
      "      return service.getAccessToken(code);\n" +
      "    }\n" +
      "    catch (RuntimeException e) {\n" +
      "      return null;\n" +
      "    }\n" +
      "  }\n" +
      "\n" +
      "  native OAuth20Service makeOAuth2Service();\n" +
      "\n" +
      "  private static class OAuth2AccessToken {}\n" +
      "\n" +
      "  private static class OAuth20Service implements AutoCloseable {\n" +
      "    native public OAuth2AccessToken getAccessToken(String code);\n" +
      "    @Override public void close() {}\n" +
      "  }\n" +
      "}");
  }

  public void testOnlyLastBuilderCallInConsidered() {
    doTest(
      "class AutoCloseableSample {\n" +
      "  void test() {\n" +
      "    final AC res = new AC().withX(10).withX(12);\n" +
      "    res.close();\n" +
      "  }\n" +
      "}\n" +
      "class AC implements AutoCloseable {\n" +
      "  native AC withX(int x);\n" +
      "  @Override public void close(){" +
      "}\n" +
      "}");
  }

  @Override
  protected LocalInspectionTool getInspection() {
    AutoCloseableResourceInspection inspection = new AutoCloseableResourceInspection();
    inspection.ignoreConstructorMethodReferences = false;
    return inspection;
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
