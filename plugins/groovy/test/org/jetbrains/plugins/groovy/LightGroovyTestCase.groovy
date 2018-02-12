// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy

import com.intellij.psi.PsiIntersectionType
import com.intellij.psi.PsiType
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

/**
 * @author peter
 */
abstract class LightGroovyTestCase extends LightCodeInsightFixtureTestCase {

  JavaCodeInsightTestFixture getFixture() {
    myFixture
  }

  @Override
  void setUp() throws Exception {
    super.setUp()
  }

  @Override
  void tearDown() throws Exception {
    super.tearDown()
  }

  @Override
  @NotNull
  protected LightProjectDescriptor getProjectDescriptor() {
    return GroovyLightProjectDescriptor.GROOVY_2_1
  }

  /**
   * Return relative path to the test data. Path is relative to the
   * {@link com.intellij.openapi.application.PathManager#getHomePath()}
   *
   * @return relative path to the test data.
   */
  @Override
  @NonNls
  protected String getBasePath() { null }


  protected void addGroovyTransformField() {
    myFixture.addClass('''package groovy.transform; public @interface Field{}''')
  }

  protected void addGroovyObject() throws IOException {
    myFixture.addClass('''\
package groovy.lang;
public interface GroovyObject {
    java.lang.Object invokeMethod(java.lang.String s, java.lang.Object o);
    java.lang.Object getProperty(java.lang.String s);
    void setProperty(java.lang.String s, java.lang.Object o);
    groovy.lang.MetaClass getMetaClass();
    void setMetaClass(groovy.lang.MetaClass metaClass);
}
''')
  }


  public static final String IMPORT_COMPILE_STATIC = 'import groovy.transform.CompileStatic'
  void addCompileStatic() {
   myFixture.addClass('''\
package groovy.transform;
public @interface CompileStatic{
}
''')
  }

  protected void addBigDecimal() {
    myFixture.addClass('''\
package java.math;

public class BigDecimal extends Number implements Comparable<BigDecimal> {
}
''')
  }

  protected void addBigInteger() {
    myFixture.addClass('''\
package java.math;

public class BigInteger extends Number implements Comparable<BigInteger> {
}
''')
  }

  protected void addHashSet() {
    myFixture.addClass('''\
package java.util;

public class HashSet<E>
    extends AbstractSet<E>
    implements Set<E>, Cloneable, java.io.Serializable
{}
''')
  }

  protected final void addAnnotationCollector() {
    myFixture.addClass '''\
package groovy.transform;

@java.lang.annotation.Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
public @interface AnnotationCollector {
    String processor() default "org.codehaus.groovy.transform.AnnotationCollectorTransform";
    Class[] value() default {};
}
'''
  }

  /*void addHashMap() {
    myFixture.addClass('''\
package java.util;

public class HashMap<K,V> extends AbstractMap<K,V> implements Map<K,V>, Cloneable, Serializable {
    public HashMap(int initialCapacity, float loadFactor) {}
    public HashMap(int initialCapacity) {}
    public HashMap() {}
    public HashMap(Map<? extends K, ? extends V> m) {}
}
''')
  }*/

  protected final void addTestCase() {
    myFixture.addClass('''\

  // IntelliJ API Decompiler stub source generated from a class file
  // Implementation of methods is not available

package junit.framework;

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
''')
  }

  static void assertType(@Nullable String expected, @Nullable PsiType actual) {
    if (expected == null) {
      assertNull(actual)
      return
    }

    assertNotNull(actual)
    if (actual instanceof PsiIntersectionType) {
      assertEquals(expected, genIntersectionTypeText(actual))
    }
    else {
      assertEquals(expected, actual.canonicalText)
    }
  }

  private static String genIntersectionTypeText(PsiIntersectionType t) {
    StringBuilder b = new StringBuilder('[')
    for (PsiType c : t.conjuncts) {
      b << c.canonicalText << ','
    }
    if (t.conjuncts) {
      b.replace(b.length() - 1, b.length(), ']')
    }
    return b
  }


  @CompileStatic
  protected String getTestName() {
    return (getTestName(true) - 'test').split(' ')*.capitalize().join('').uncapitalize()
  }
}
