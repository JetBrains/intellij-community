// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.common

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.ml.ContextFeatures
import com.intellij.codeInsight.completion.ml.ElementFeatureProvider
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.LanguageNamesValidation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.FixedHashMap
import java.util.*
import java.util.function.BooleanSupplier

class RecentPlacesFeatures : ElementFeatureProvider {
  override fun getName(): String = "recent_places"

  override fun calculateFeatures(
    element: LookupElement,
    location: CompletionLocation,
    contextFeatures: ContextFeatures,
  ): Map<String, MLFeatureValue> {
    val storage = location.project.service<RecentPlacesStorage>()
    val inRecentPlaces = storage.contains(element.lookupString)
    val inChildrenRecentPlaces = storage.childrenContains(element.lookupString)
    return when {
      inRecentPlaces && inChildrenRecentPlaces -> mapOf(
        "contains" to MLFeatureValue.binary(true),
        "children_contains" to MLFeatureValue.binary(true)
      )
      inRecentPlaces -> mapOf("contains" to MLFeatureValue.binary(true))
      inChildrenRecentPlaces -> mapOf("children_contains" to MLFeatureValue.binary(true))
      else -> emptyMap()
    }
  }

  internal class StoreRecentPlacesListener(private val project: Project) : IdeDocumentHistoryImpl.RecentPlacesListener {
    private val recentPlacesStorage = project.service<RecentPlacesStorage>()

    override fun recentPlaceAdded(changePlace: IdeDocumentHistoryImpl.PlaceInfo, isChanged: Boolean) {
      if (ApplicationManager.getApplication().isUnitTestMode || !changePlace.file.isValid || changePlace.file.isDirectory) {
        return
      }

      val offset = changePlace.caretPosition?.startOffset ?: return

      @Suppress("IncorrectParentDisposable")
      ReadAction
        .nonBlocking<Pair<String, List<String>>?> {
          val provider = PsiManager.getInstance(project).findViewProvider(changePlace.file) ?: return@nonBlocking null
          val namesValidator = LanguageNamesValidation.INSTANCE.forLanguage(provider.baseLanguage)

          val recentPlace = provider.tryFindElementAt(offset)
          if (recentPlace == null || !namesValidator.isIdentifier(recentPlace.text, project)) {
            return@nonBlocking null
          }
          val declaration = findDeclaration(recentPlace)
          val childrenPlaces = declaration?.getChildrenNames()?.take(MAX_CHILDREN_PER_PLACE) ?: emptyList()
          return@nonBlocking recentPlace.text to childrenPlaces
        }
        .finishOnUiThread(ModalityState.nonModal()) { place2children ->
          if (place2children != null) {
            recentPlacesStorage.put(place2children.first)
            recentPlacesStorage.putChildren(place2children.second)
          }
        }
        .coalesceBy(this, changePlace.file)
        .expireWith(project)
        .expireWhen(BooleanSupplier {
          val window = changePlace.getWindow()
          window == null || window.isDisposed
        })
        .submit(AppExecutorUtil.getAppExecutorService())
    }

    override fun recentPlaceRemoved(changePlace: IdeDocumentHistoryImpl.PlaceInfo, isChanged: Boolean) = Unit

    private fun PsiElement.getChildrenNames(): List<String> {
      return this.children.filterIsInstance<PsiNamedElement>().mapNotNull { it.name }
    }

    private fun FileViewProvider.tryFindElementAt(offset: Int): PsiElement? {
      return try {
        if (virtualFile.isValid && getPsi(baseLanguage)?.isValid == true) findElementAt(offset) else null
      }
      catch (_: Throwable) {
        null
      }
    }

    private fun findDeclaration(element: PsiElement): PsiElement? {
      var curElement = element
      while (curElement !is PsiFile) {
        if (curElement is PsiNameIdentifierOwner) return curElement
        curElement = curElement.parent ?: return null
      }
      return null
    }
  }

  @Service(Service.Level.PROJECT)
  private class RecentPlacesStorage {
    private val recentPlaces = createFixedSizeSet(20)
    private val childrenRecentPlaces = createFixedSizeSet(100)

    @Synchronized
    fun contains(value: String): Boolean = recentPlaces.contains(value)

    @Synchronized
    fun childrenContains(value: String): Boolean = childrenRecentPlaces.contains(value)

    @Synchronized
    fun put(value: String) = recentPlaces.addToTop(value)

    @Synchronized
    fun putChildren(values: List<String>) = values.forEach { childrenRecentPlaces.addToTop(it) }

    private fun createFixedSizeSet(maxSize: Int): MutableSet<String> =
      Collections.newSetFromMap(FixedHashMap(maxSize))

    private fun <T> MutableSet<T>.addToTop(value: T) {
      this.remove(value)
      this.add(value)
    }
  }
}

private const val MAX_CHILDREN_PER_PLACE = 10