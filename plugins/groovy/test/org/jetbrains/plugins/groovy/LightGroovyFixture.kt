// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("LightGroovyFixtures")

package org.jetbrains.plugins.groovy

import com.intellij.codeInspection.ex.EntryPointsManagerBase
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly

/**
 * Creates the code insight fixture of a Groovy test. Declare it as an instance field.
 *
 * Give the project fixture the descriptor of the test, usually one of [GroovyProjectDescriptors].
 * When [testDataPath] is not null, the fixture uses it as the absolute test data path.
 * The fixture loads the dead code entry points before the test.
 * Without it, the first highlighting loads them and fails with "PSI/document/model changes are not allowed during highlighting".
 */
@TestOnly
@JvmOverloads
fun lightGroovyFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String? = null,
): TestFixture<LightJavaCodeInsightTestFixture> = testFixture("lightGroovyFixture") {
  val fixture = lightJavaCodeInsightFixture(projectFixture).init()
  if (testDataPath != null) {
    fixture.testDataPath = testDataPath
  }
  EntryPointsManagerBase.DEAD_CODE_EP_NAME.extensionList
  initialized(fixture) {}
}

/**
 * Returns the name of the test in camel case, with a lower case first letter and without the `test` prefix.
 * For the Kotlin test `` `test foo bar` ``, it returns `fooBar`.
 */
@TestOnly
fun LightJavaCodeInsightTestFixture.groovyTestName(): String {
  val joined = getTestName(true).split(" ").joinToString("") { StringUtil.capitalize(it) }
  return StringUtil.toLowerCase(joined[0]) + joined.substring(1)
}

/**
 * Adds the annotation `groovy.transform.Field`.
 */
@TestOnly
fun JavaCodeInsightTestFixture.addGroovyTransformField() {
  addClass("package groovy.transform; public @interface Field{}")
}

/**
 * Adds the interface `groovy.lang.GroovyObject`.
 */
@TestOnly
fun JavaCodeInsightTestFixture.addGroovyObject() {
  addClass("""
    package groovy.lang;
    public interface GroovyObject {
        java.lang.Object invokeMethod(java.lang.String s, java.lang.Object o);
        java.lang.Object getProperty(java.lang.String s);
        void setProperty(java.lang.String s, java.lang.Object o);
        groovy.lang.MetaClass getMetaClass();
        void setMetaClass(groovy.lang.MetaClass metaClass);
    }
  """.trimIndent())
}

/**
 * Adds the annotation `groovy.transform.CompileStatic`.
 */
@TestOnly
fun JavaCodeInsightTestFixture.addCompileStatic() {
  addClass("""
    package groovy.transform;
    public @interface CompileStatic{
    }
  """.trimIndent())
}

/**
 * Adds a stub of `java.math.BigDecimal`.
 */
@TestOnly
fun JavaCodeInsightTestFixture.addBigDecimal() {
  addClass("""
    package java.math;

    public class BigDecimal extends Number implements Comparable<BigDecimal> {
    }
  """.trimIndent())
}

/**
 * Adds a stub of `java.math.BigInteger`.
 */
@TestOnly
fun JavaCodeInsightTestFixture.addBigInteger() {
  addClass("""
    package java.math;

    public class BigInteger extends Number implements Comparable<BigInteger> {
    }
  """.trimIndent())
}

/**
 * Adds a stub of `java.util.HashSet`.
 */
@TestOnly
fun JavaCodeInsightTestFixture.addHashSet() {
  addClass("""
    package java.util;

    public class HashSet<E>
        extends AbstractSet<E>
        implements Set<E>, Cloneable, java.io.Serializable
    {}
  """.trimIndent())
}

/**
 * Adds the annotation `groovy.transform.AnnotationCollector`.
 */
@TestOnly
fun JavaCodeInsightTestFixture.addAnnotationCollector() {
  addClass("""
    package groovy.transform;

    @java.lang.annotation.Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
    public @interface AnnotationCollector {
        String processor() default "org.codehaus.groovy.transform.AnnotationCollectorTransform";
        Class[] value() default {};
    }
  """.trimIndent())
}

/**
 * Adds a stub of the JUnit 3 class `junit.framework.TestCase`.
 */
@TestOnly
fun JavaCodeInsightTestFixture.addTestCase() {
  addClass("""
    // IntelliJ API Decompiler stub source generated from a class file
    // Implementation of methods is not available

    package junit.framework;

    @SuppressWarnings({"Contract", "MethodOverridesStaticMethodOfSuperclass", "RedundantThrows"})
    public abstract class TestCase extends junit.framework.Assert implements junit.framework.Test {
      private java.lang.String fName;

      public TestCase() { /* compiled code */ }

      public TestCase(java.lang.String name) { /* compiled code */ }

      public int countTestCases() { /* compiled code */ }

      protected junit.framework.TestResult createResult() { /* compiled code */ }

      public junit.framework.TestResult run() { /* compiled code */ }

      public void run(junit.framework.TestResult result) { /* compiled code */ }

      public void runBare() throws java.lang.Throwable { /* compiled code */ }

      protected void runTest() throws java.lang.Throwable { /* compiled code */ }

      public static void assertTrue(java.lang.String message, boolean condition) { /* compiled code */ }

      public static void assertTrue(boolean condition) { /* compiled code */ }

      public static void assertFalse(java.lang.String message, boolean condition) { /* compiled code */ }

      public static void assertFalse(boolean condition) { /* compiled code */ }

      public static void fail(java.lang.String message) { /* compiled code */ }

      public static void fail() { /* compiled code */ }

      public static void assertEquals(java.lang.String message, java.lang.Object expected, java.lang.Object actual) { /* compiled code */ }

      public static void assertEquals(java.lang.Object expected, java.lang.Object actual) { /* compiled code */ }

      public static void assertEquals(java.lang.String message, java.lang.String expected, java.lang.String actual) { /* compiled code */ }

      public static void assertEquals(java.lang.String expected, java.lang.String actual) { /* compiled code */ }

      public static void assertEquals(java.lang.String message, double expected, double actual, double delta) { /* compiled code */ }

      public static void assertEquals(double expected, double actual, double delta) { /* compiled code */ }

      public static void assertEquals(java.lang.String message, float expected, float actual, float delta) { /* compiled code */ }

      public static void assertEquals(float expected, float actual, float delta) { /* compiled code */ }

      public static void assertEquals(java.lang.String message, long expected, long actual) { /* compiled code */ }

      public static void assertEquals(long expected, long actual) { /* compiled code */ }

      public static void assertEquals(java.lang.String message, boolean expected, boolean actual) { /* compiled code */ }

      public static void assertEquals(boolean expected, boolean actual) { /* compiled code */ }

      public static void assertEquals(java.lang.String message, byte expected, byte actual) { /* compiled code */ }

      public static void assertEquals(byte expected, byte actual) { /* compiled code */ }

      public static void assertEquals(java.lang.String message, char expected, char actual) { /* compiled code */ }

      public static void assertEquals(char expected, char actual) { /* compiled code */ }

      public static void assertEquals(java.lang.String message, short expected, short actual) { /* compiled code */ }

      public static void assertEquals(short expected, short actual) { /* compiled code */ }

      public static void assertEquals(java.lang.String message, int expected, int actual) { /* compiled code */ }

      public static void assertEquals(int expected, int actual) { /* compiled code */ }

      public static void assertNotNull(java.lang.Object object) { /* compiled code */ }

      public static void assertNotNull(java.lang.String message, java.lang.Object object) { /* compiled code */ }

      public static void assertNull(java.lang.Object object) { /* compiled code */ }

      public static void assertNull(java.lang.String message, java.lang.Object object) { /* compiled code */ }

      public static void assertSame(java.lang.String message, java.lang.Object expected, java.lang.Object actual) { /* compiled code */ }

      public static void assertSame(java.lang.Object expected, java.lang.Object actual) { /* compiled code */ }

      public static void assertNotSame(java.lang.String message, java.lang.Object expected, java.lang.Object actual) { /* compiled code */ }

      public static void assertNotSame(java.lang.Object expected, java.lang.Object actual) { /* compiled code */ }

      public static void failSame(java.lang.String message) { /* compiled code */ }

      public static void failNotSame(java.lang.String message, java.lang.Object expected, java.lang.Object actual) { /* compiled code */ }

      public static void failNotEquals(java.lang.String message, java.lang.Object expected, java.lang.Object actual) { /* compiled code */ }

      public static java.lang.String format(java.lang.String message, java.lang.Object expected, java.lang.Object actual) { /* compiled code */ }

      protected void setUp() throws java.lang.Exception { /* compiled code */ }

      protected void tearDown() throws java.lang.Exception { /* compiled code */ }

      public java.lang.String toString() { /* compiled code */ }

      public java.lang.String getName() { /* compiled code */ }

      public void setName(java.lang.String name) { /* compiled code */ }
    }
  """.trimIndent())
}
