// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io

import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.utils.io.getChildren
import com.intellij.util.io.isFile
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

@TestApplication
abstract class NioPathUtilTestCase {

  private lateinit var fileFixture: TempDirTestFixture
  private lateinit var testRoot: Path

  val root: Path get() = testRoot

  @BeforeEach
  fun setUp() {
    fileFixture = IdeaTestFixtureFactory.getFixtureFactory()
      .createTempDirTestFixture()
    fileFixture.setUp()
    testRoot = fileFixture.tempDirPath.toNioPath()
  }

  @AfterEach
  fun tearDown() {
    fileFixture.tearDown()
  }

  suspend fun assertNioPath(init: suspend Path.() -> Path?): FileAssertion<Path> {
    return NioPathAssertion().init(init)
  }

  suspend fun <T> FileAssertion<T>.init(init: suspend Path.() -> T?) = apply {
    try {
      myValue = root.init()
    }
    catch (ex: Exception) {
      myException = ex
    }
  }

  inner class NioPathAssertion : FileAssertion<Path>() {

    override suspend fun createAssertion(init: suspend Path.() -> Path?) =
      assertNioPath(init)

    override fun isEmpty(file: Path) = file.getChildren().isEmpty()
    override fun exists(file: Path) = file.exists()
    override fun isFile(file: Path) = file.isFile()
    override fun isDirectory(file: Path) = file.isDirectory()
  }

  abstract class FileAssertion<T> {

    var myValue: T? = null
    var myException: Exception? = null

    abstract suspend fun createAssertion(init: suspend T.() -> T?): FileAssertion<T>

    abstract fun isEmpty(file: T): Boolean

    abstract fun exists(file: T): Boolean

    abstract fun isFile(file: T): Boolean

    abstract fun isDirectory(file: T): Boolean

    fun getFile(): T? {
      myException?.let { throw it }
      return myValue
    }

    private inline fun withFile(action: (T?) -> Unit) = apply {
      action(getFile())
    }

    fun <Ex : Exception> isFailedWithException(aClass: Class<Ex>, pattern: Regex) = apply {
      val exception = requireNotNull(myException)
      if (!aClass.isInstance(exception)) {
        throw AssertionError("Exception type isn't matches '$aClass'", exception)
      }
      if (!pattern.matches(exception.message!!)) {
        throw AssertionError("Exception message isn't matches '$pattern'", exception)
      }
    }

    inline fun <reified Ex : Exception> isFailedWithException(pattern: String = ".*") =
      isFailedWithException(Ex::class.java, Regex(pattern))

    fun doesNotExist() = withFile { path ->
      Assertions.assertTrue(path == null || !exists(path)) {
        "Expected file doesn't exist: $path"
      }
    }

    fun isExistedFile() = withFile { path ->
      Assertions.assertTrue(path != null && exists(path) && isFile(path)) {
        "Expected file instead directory: $path"
      }
    }

    fun isExistedDirectory() = withFile { file ->
      Assertions.assertTrue(file != null && exists(file) && isDirectory(file)) {
        "Expected directory instead file: $file"
      }
    }

    fun isEmptyDirectory() = withFile { file ->
      Assertions.assertTrue(file != null && isEmpty(file)) {
        "Expected empty directory: $file"
      }
    }

    suspend fun isEqualsTo(init: suspend T.() -> T?) = withFile { file ->
      val other = createAssertion(init)
      other.withFile { otherPath ->
        Assertions.assertEquals(file, otherPath)
      }
      when {
        file == null -> other.doesNotExist()
        !exists(file) -> other.doesNotExist()
        isFile(file) -> other.isExistedFile()
        isDirectory(file) -> {
          other.isExistedDirectory()
          if (isEmpty(file)) {
            other.isEmptyDirectory()
          }
        }
      }
    }
  }
}