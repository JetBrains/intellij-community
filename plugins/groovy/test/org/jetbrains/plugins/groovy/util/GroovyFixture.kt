// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GroovyFixture")

package org.jetbrains.plugins.groovy.util

import com.intellij.codeInspection.ex.EntryPointsManagerBase
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
import com.intellij.testFramework.junit5.fixture.pathInProjectFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly
import kotlin.io.path.Path

/**
 * Creates a [JavaCodeInsightTestFixture] in a new project for each test.
 * It replaces the JUnit 4 base `LightProjectTest` and its subclasses, such as `GroovyLatestTest`.
 *
 * The module gets the SDK, the language level, and the libraries of [descriptor], as a light module of [descriptor] does.
 * The test data path is [testDataPath] under [TestUtils.getAbsoluteTestDataPath].
 *
 * The JUnit 4 base ran each test on the EDT. Put the EDT calls of a JUnit 6 test in `EdtTestUtil.runInEdtAndWait`.
 */
@TestOnly
@JvmOverloads
fun groovyFixture(descriptor: LightProjectDescriptor, testDataPath: String = ""): TestFixture<JavaCodeInsightTestFixture> =
  testFixture("groovyFixture") {
    val projectFixture = codeInsightProjectFixture()
    val disposable = disposableFixture().init()
    val fixture = javaCodeInsightFixture(projectFixture, projectFixture.pathInProjectFixture(Path(""))).init()
    val module = fixture.module
    // configureModule is public only on DefaultLightProjectDescriptor.
    val defaultDescriptor = descriptor as DefaultLightProjectDescriptor
    val sdk = defaultDescriptor.sdk!!
    WriteAction.runAndWait<RuntimeException> { ProjectJdkTable.getInstance().addJdk(sdk, disposable) }
    ModuleRootModificationUtil.setModuleSdk(module, sdk)
    ModuleRootModificationUtil.updateModel(module) { defaultDescriptor.configureModule(module, it, it.contentEntries.single()) }
    IndexingTestUtil.waitUntilIndexesAreReady(fixture.project)
    // LightGroovyTestCase loads the extensions before the test, so that the highlighting does not load them.
    EntryPointsManagerBase.DEAD_CODE_EP_NAME.extensionList
    fixture.testDataPath = TestUtils.getAbsoluteTestDataPath() + testDataPath
    initialized(fixture) {}
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
