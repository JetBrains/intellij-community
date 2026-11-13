// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GroovyFixture")

package org.jetbrains.plugins.groovy.util

import com.intellij.codeInspection.ex.EntryPointsManagerBase
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightProjectFixture
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.javaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.pathInProjectFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.junit5.fixture.vfsDeletedTempPathFixture
import org.jetbrains.annotations.TestOnly
import kotlin.io.path.Path

/**
 * Creates a [JavaCodeInsightTestFixture] in a new project for each test.
 * The project directory is the module directory.
 * Use it only when the code under test needs the project directory. Otherwise, use the overload with a shared project.
 *
 * The module gets the SDK, the language level, and the libraries of [descriptor], as a light module of [descriptor] does.
 * The test data path is [testDataPath] under [TestUtils.getAbsoluteTestDataPath].
 */
@TestOnly
@JvmOverloads
fun groovyFixture(descriptor: LightProjectDescriptor, testDataPath: String = ""): TestFixture<JavaCodeInsightTestFixture> =
  testFixture("groovyFixture") {
    val projectFixture = codeInsightProjectFixture()
    val disposable = disposableFixture().init()
    val fixture = javaCodeInsightFixture(projectFixture, projectFixture.pathInProjectFixture(Path(""))).init()
    configureGroovyModule(fixture, descriptor, disposable, testDataPath)
    initialized(fixture) {}
  }

/**
 * Creates an opened project to share between the tests of a class. Declare it in a static field, and pass it to [groovyFixture].
 */
@TestOnly
fun groovyProjectFixture(): TestFixture<Project> = projectFixture(openAfterCreation = true)

/**
 * Creates a [JavaCodeInsightTestFixture] with a new module and a new temporary directory for each test in the shared [projectFixture].
 * It replaces the JUnit 3 and JUnit 4 light Groovy bases, such as `LightGroovyTestCase` and `GroovyLatestTest`.
 * The project directory is not the module directory.
 *
 * The module gets the SDK, the language level, and the libraries of [descriptor], as a light module of [descriptor] does.
 * The test data path is [testDataPath] under [TestUtils.getAbsoluteTestDataPath].
 */
@TestOnly
@JvmOverloads
fun groovyFixture(
  projectFixture: TestFixture<Project>,
  descriptor: LightProjectDescriptor,
  testDataPath: String = "",
): TestFixture<JavaCodeInsightTestFixture> =
  testFixture("groovyFixture") {
    val disposable = disposableFixture().init()
    val pathFixture = vfsDeletedTempPathFixture()
    val moduleFixture = projectFixture.moduleFixture(pathFixture, addPathToSourceRoot = true)
    // The editors of the test must close before the module is disposed.
    val fixture = javaCodeInsightFixture(projectFixture, pathFixture).dependsOn(moduleFixture).init()
    configureGroovyModule(fixture, descriptor, disposable, testDataPath)
    initialized(fixture) {}
  }

private fun configureGroovyModule(fixture: JavaCodeInsightTestFixture, descriptor: LightProjectDescriptor, disposable: Disposable, testDataPath: String) {
  val module = fixture.module
  // The empty descriptor has no SDK and no library, so the module stays as the module fixture made it.
  if (descriptor !== LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR) {
    // configureModule is public only on DefaultLightProjectDescriptor.
    val defaultDescriptor = descriptor as DefaultLightProjectDescriptor
    val sdk = defaultDescriptor.sdk!!
    WriteAction.runAndWait<RuntimeException> { ProjectJdkTable.getInstance().addJdk(sdk, disposable) }
    ModuleRootModificationUtil.setModuleSdk(module, sdk)
    ModuleRootModificationUtil.updateModel(module) { defaultDescriptor.configureModule(module, it, it.contentEntries.single()) }
  }
  IndexingTestUtil.waitUntilIndexesAreReady(fixture.project)
  // LightGroovyTestCase loads the extensions before the test, so that the highlighting does not load them.
  EntryPointsManagerBase.DEAD_CODE_EP_NAME.extensionList
  fixture.testDataPath = TestUtils.getAbsoluteTestDataPath() + testDataPath
}

/**
 * Adds a stub of the annotation `groovy.transform.CompileStatic` to the project of [fixture].
 */
@TestOnly
fun addCompileStatic(fixture: JavaCodeInsightTestFixture) {
  fixture.addClass("""
    package groovy.transform;
    public @interface CompileStatic{
    }
  """.trimIndent())
}

/**
 * Adds a stub of the annotation `groovy.transform.Field` to the project of [fixture].
 */
@TestOnly
fun addGroovyTransformField(fixture: JavaCodeInsightTestFixture) {
  fixture.addClass("package groovy.transform; public @interface Field{}")
}

/**
 * Adds a stub of the interface `groovy.lang.GroovyObject` to the project of [fixture].
 */
@TestOnly
fun addGroovyObject(fixture: JavaCodeInsightTestFixture) {
  fixture.addClass("""
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
 * Adds a stub of the class `java.math.BigDecimal` to the project of [fixture].
 */
@TestOnly
fun addBigDecimal(fixture: JavaCodeInsightTestFixture) {
  fixture.addClass("""
    package java.math;

    public class BigDecimal extends Number implements Comparable<BigDecimal> {
    }
  """.trimIndent())
}

/**
 * Adds a stub of the class `java.math.BigInteger` to the project of [fixture].
 */
@TestOnly
fun addBigInteger(fixture: JavaCodeInsightTestFixture) {
  fixture.addClass("""
    package java.math;

    public class BigInteger extends Number implements Comparable<BigInteger> {
    }
  """.trimIndent())
}

/**
 * Adds a stub of the class `java.util.HashSet` to the project of [fixture].
 */
@TestOnly
fun addHashSet(fixture: JavaCodeInsightTestFixture) {
  fixture.addClass("""
    package java.util;

    public class HashSet<E>
        extends AbstractSet<E>
        implements Set<E>, Cloneable, java.io.Serializable
    {}
  """.trimIndent())
}

/**
 * Adds a stub of the annotation `groovy.transform.AnnotationCollector` to the project of [fixture].
 */
@TestOnly
fun addAnnotationCollector(fixture: JavaCodeInsightTestFixture) {
  fixture.addClass("""
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
 * Adds a stub of the class `junit.framework.TestCase` to the project of [fixture].
 */
@TestOnly
fun addTestCase(fixture: JavaCodeInsightTestFixture) {
  fixture.addClass("""
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

/**
 * Configures [fixture] with [text] in the file [fileName] in the root of the project, as the JUnit 3 light fixtures do.
 * The file gets exactly this name, and the text replaces the text of an existing file.
 * `configureByText` of a JUnit 6 fixture makes the file name unique, which changes the name of a script class.
 */
@TestOnly
fun configureByExactName(fixture: JavaCodeInsightTestFixture, fileName: String, text: String): PsiFile {
  // The editor of an earlier configuration keeps an unsaved document, which blocks the reload of the new text.
  val document = fixture.tempDirFixture.getFile(fileName)?.let { FileDocumentManager.getInstance().getCachedDocument(it) }
  if (document != null) {
    WriteCommandAction.runWriteCommandAction(fixture.project) {
      PsiDocumentManager.getInstance(fixture.project).doPostponedOperationsAndUnblockDocument(document)
      FileDocumentManager.getInstance().saveDocument(document)
    }
  }
  val file = fixture.addFileToProject(fileName, text)
  fixture.configureFromExistingVirtualFile(file.virtualFile)
  return fixture.file
}
