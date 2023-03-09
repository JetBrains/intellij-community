// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mvstore

import com.intellij.openapi.diagnostic.Logger
import org.h2.mvstore.MVStore
import org.h2.mvstore.MVStoreException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

fun openOrRecreateStore(dbFile: Path, logSupplier: () -> Logger): MVStore {
  val storeErrorHandler = StoreErrorHandler(dbFile, logSupplier)
  return try {
    openStore(dbFile, storeErrorHandler)
  }
  catch (e: MVStoreException) {
    logSupplier().warn("Store will be recreated (db=$dbFile)", e)
    Files.deleteIfExists(dbFile)
    openStore(dbFile, storeErrorHandler)
  }
}

@Throws(MVStoreException::class)
private fun openStore(dbFile: Path, storeErrorHandler: StoreErrorHandler): MVStore {
  val store = MVStore.Builder()
    .fileName(dbFile.toString())
    .backgroundExceptionHandler(storeErrorHandler)
    // avoid extra thread - db maintainer should use coroutines
    .autoCommitDisabled()
    .cacheSize(64)
    .open()
  storeErrorHandler.isStoreOpened = true
  return store
}

private class StoreErrorHandler(private val dbFile: Path, private val logSupplier: () -> Logger) : Thread.UncaughtExceptionHandler {
  @JvmField
  var isStoreOpened = false

  override fun uncaughtException(t: Thread, e: Throwable) {
    val log = logSupplier()
    if (isStoreOpened) {
      log.error("Store error (db=$dbFile)", e)
    }
    else {
      log.warn("Store will be recreated (db=$dbFile)", e)
    }
  }
}