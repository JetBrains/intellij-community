// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common

import com.intellij.openapi.diagnostic.logger
import java.lang.reflect.InaccessibleObjectException
import java.nio.file.spi.FileSystemProvider
import java.util.*

private object FileSystemProviderInjector

/**
 * Workaround for the case that happens at least in `com.intellij.tests.JUnit5TeamCityRunnerForTestAllSuite`.
 *
 * The test runner starts _without_ modules related to tests in the classpath.
 * Later an appropriate classpath is built during test discovery.
 * The new classpath contains the files necessary for [ServiceLoader], especially for [FileSystemProvider].
 * However, [FileSystemProvider.installedProviders] uses the system classloader,
 * i.e., the classloader that doesn't contain files from the new classpath.
 * Therefore, without this hack tests that require custom FileSystemProviders can fail.
 */
internal fun injectFileSystemProviders() {
  val desiredProvidersByScheme = ServiceLoader.load(FileSystemProvider::class.java).associateBy { it.scheme }

  val providersToAdd = desiredProvidersByScheme
    .filter { (scheme, _) ->
      FileSystemProvider.installedProviders().none { it.scheme == scheme }
    }
    .values

  if (providersToAdd.isNotEmpty()) {
    try {
      executeUnderLock {
        modifyProviders { providers ->
          providers.addAll(providersToAdd)
        }
      }
    }
    catch (err: InaccessibleObjectException) {
      logger<FileSystemProviderInjector>().error(
        "The test process runs with an invalid classloader and attempt to autofix it failed." +
        " It can lead to errors in tests that use IJent NIO file system." +
        " To make the test work, add `--add-opens java.base/java.nio.file.spi=ALL-UNNAMED`" +
        " to VM options of the test process.",
        err
      )
    }
  }
}

private fun executeUnderLock(runnable: () -> Unit) {
  val lockField = FileSystemProvider::class.java.getDeclaredField("lock")
  lockField.setAccessible(true)
  synchronized(lockField.get(FileSystemProvider::class.java)) {
    runnable()
  }
}

private fun modifyProviders(mutator: (providers: MutableList<FileSystemProvider>) -> Unit) {
  // this step is critical to ensure that the list of FileSystemProviders is already initialized
  FileSystemProvider.installedProviders()

  val klass = FileSystemProvider::class.java
  val installedProvidersField = klass.getDeclaredField("installedProviders")
  installedProvidersField.setAccessible(true)
  val providers = installedProvidersField.get(klass) as List<FileSystemProvider>

  val newProviders = mutableListOf<FileSystemProvider>()
  newProviders.addAll(providers)
  mutator(newProviders)

  installedProvidersField.set(klass, newProviders)
}
