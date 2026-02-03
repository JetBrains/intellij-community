// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileTypes.FileTypeEvent
import com.intellij.openapi.fileTypes.FileTypeListener
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@Service(Service.Level.PROJECT)
internal class StickyLinesLanguageSupport(project: Project) : Disposable {

  private val supportedLanguages: MutableSet<Language> = mutableSetOf()
  private val rwLock = ReentrantReadWriteLock()

  init {
    updateLanguages()
    subscribeProviderChanges()
    subscribeFileTypeChanges(project)
    subscribePluginChanges(project)
  }

  fun supportedLang(lang: Language): Language {
    // example: ECMAScript6 -> JavaScript
    rwLock.read {
      var supported: Language? = lang
      while (supported != null) {
        if (supported in supportedLanguages) {
          return supported
        }
        supported = supported.baseLanguage
      }
      return lang
    }
  }

  private fun updateLanguages() {
    rwLock.write {
      supportedLanguages.clear()
      for (provider in BreadcrumbsProvider.EP_NAME.extensionList) {
        supportedLanguages.addAll(provider.languages)
      }
    }
  }

  private fun subscribeProviderChanges() {
    BreadcrumbsProvider.EP_NAME.addChangeListener(
      Runnable { updateLanguages() },
      this,
    )
  }

  private fun subscribeFileTypeChanges(project: Project) {
    project.messageBus.connect(this).subscribe(
      FileTypeManager.TOPIC,
      object : FileTypeListener {
        override fun fileTypesChanged(event: FileTypeEvent) = updateLanguages()
      },
    )
  }

  private fun subscribePluginChanges(project: Project) {
    project.messageBus.connect(this).subscribe(
      DynamicPluginListener.TOPIC,
      object : DynamicPluginListener {
        override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) = updateLanguages()
        override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) = updateLanguages()
      },
    )
  }

  override fun dispose() {
  }
}
