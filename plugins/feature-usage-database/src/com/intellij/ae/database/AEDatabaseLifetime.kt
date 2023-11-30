// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ae.database

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

@Service
class AEDatabaseLifetime(private val coroutineScope: CoroutineScope) : Disposable {
  companion object {
    fun getScope() = ApplicationManager.getApplication().service<AEDatabaseLifetime>().coroutineScope
    fun getDisposable(): Disposable = ApplicationManager.getApplication().service<AEDatabaseLifetime>()
    fun getDisposable(project: Project): Disposable = project.service<AEDatabaseProjectLifetime>()
  }

  override fun dispose() {}
}

@Service(Service.Level.PROJECT)
internal class AEDatabaseProjectLifetime : Disposable {
  override fun dispose() {}
}