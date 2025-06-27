// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common

import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.ijent.community.impl.nio.IjentNioFileSystemProvider
import java.nio.file.spi.FileSystemProvider

fun injectIjentNioFileSystemProvider() {
  executeUnderLock {
    modifyProviders { providers ->
      if (providers.filterIsInstance<IjentNioFileSystemProvider>().isNotEmpty()) {
        Logger.getInstance(EelTestEnvironmentHolder::class.java).warn("IjentNioFileSystemProvider is already registered")
      }
      else {
        providers.add(IjentNioFileSystemProvider())
      }

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
