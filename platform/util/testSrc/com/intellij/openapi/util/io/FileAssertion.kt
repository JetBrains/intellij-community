// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io

import org.junit.jupiter.api.Assertions

abstract class FileAssertion<T, Self : FileAssertion<T, Self>> {

  private var myValue: T? = null
  private var myException: Exception? = null

  protected abstract val self: Self

  protected abstract fun createAssertion(): Self

  abstract fun isEmpty(file: T): Boolean

  abstract fun exists(file: T): Boolean

  abstract fun isFile(file: T): Boolean

  abstract fun isDirectory(file: T): Boolean

  fun getFile(): T? {
    myException?.let { throw it }
    return myValue
  }

  private inline fun withFile(action: (T?) -> Unit) = self.apply {
    action(getFile())
  }

  fun <Ex : Exception> isFailedWithException(aClass: Class<Ex>, pattern: Regex) = self.apply {
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

  suspend fun isEqualsTo(init: suspend () -> T?) = withFile { file ->
    val other = createAssertion().init { init() }
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

  suspend fun init(init: suspend () -> T?) = self.apply {
    try {
      myValue = init()
    }
    catch (ex: Exception) {
      myException = ex
    }
  }
}