// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.java.properties.service

import com.intellij.gradle.java.groovy.service.resolve.GradleExtensionsContributorUtil
import com.intellij.gradle.java.groovy.service.resolve.GradleExtensionsContributorUtil.Companion.PROPERTIES_FILE_ORIGINAL_INFO
import com.intellij.gradle.java.groovy.service.resolve.GradleExtensionsContributorUtil.Companion.getExtensionsFor
import com.intellij.gradle.java.groovy.service.resolve.GradleGroovyProperty
import com.intellij.gradle.java.properties.util.gradlePropertiesStream
import com.intellij.icons.AllIcons
import com.intellij.lang.properties.IProperty
import com.intellij.psi.CommonClassNames
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames
import org.jetbrains.plugins.gradle.service.resolve.GradleProjectAwareType
import org.jetbrains.plugins.gradle.service.resolve.getAccessorsForAllCatalogs
import org.jetbrains.plugins.gradle.service.resolve.getVersionCatalogAccessor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.impl.utils.createGrField
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor
import org.jetbrains.plugins.groovy.lang.resolve.getName
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type
import org.jetbrains.plugins.groovy.lang.resolve.shouldProcessProperties

@ApiStatus.Internal
class GradleExtensionsContributor : NonCodeMembersContributor() {

  override fun getClassNames(): Collection<String> {
    return listOf(GradleCommonClassNames.GRADLE_API_EXTRA_PROPERTIES_EXTENSION, GradleCommonClassNames.GRADLE_API_PROJECT)
  }

  override fun processDynamicElements(qualifierType: PsiType,
                                      aClass: PsiClass?,
                                      processor: PsiScopeProcessor,
                                      place: PsiElement,
                                      state: ResolveState
  ) {
    if (qualifierType !is GradleProjectAwareType && !InheritanceUtil.isInheritor(qualifierType, GradleCommonClassNames.GRADLE_API_EXTRA_PROPERTIES_EXTENSION)) return
    if (!processor.shouldProcessProperties()) return
    val file = place.containingFile
    val data = getExtensionsFor(file) ?: return
    val name = processor.getName(state)

    val resolvedProperties = processPropertiesFromFile(aClass, processor, place, state)
    val versionCatalogProperties = processPropertiesFromCatalog(name, place, processor, state) ?: return

    val properties = if (name == null) data.findAllProperties() else listOfNotNull(data.findProperty(name))

    for (property in properties) {
      if (property.name in resolvedProperties || property.name in versionCatalogProperties) {
        continue
      }
      if (!processor.execute(GradleGroovyProperty(property.name, property.typeFqn, null, file), state)) {
        return
      }
    }
  }

  private fun processPropertiesFromFile(aClass: PsiClass?,
                                        processor: PsiScopeProcessor,
                                        place: PsiElement,
                                        state: ResolveState
  ) : Set<String> {
    if (aClass == null) {
      return emptySet()
    }
    val factory = JavaPsiFacade.getInstance(place.project)
    val stringType = factory.findClass(CommonClassNames.JAVA_LANG_STRING, place.resolveScope)?.type() ?: return emptySet()
    val properties = gradlePropertiesStream(place)
    val name = processor.getName(state)
    val processedNames = mutableSetOf<String>()
    for (propertyFile in properties) {
      if (name == null) {
        for (property in propertyFile.properties) {
          if (property.name.contains(".")) {
            continue
          }
          processedNames.add(property.name)
          val newProperty = createGroovyProperty(aClass, property, stringType)
          processor.execute(newProperty, state)
        }
      }
      else {
        val property = propertyFile.findPropertyByKey(name) ?: continue
        val newProperty = createGroovyProperty(aClass, property, stringType)
        processor.execute(newProperty, state)
        return setOf(newProperty.name)
      }
    }
    return processedNames
  }

  companion object {
    private fun createGroovyProperty(aClass: PsiClass,
                                     property: IProperty,
                                     stringType: PsiClassType): GrField {
      val newProperty = createGrField(aClass, property.name, stringType, property.psiElement, PROPERTIES_FILE_ORIGINAL_INFO, AllIcons.FileTypes.Properties)
      return newProperty
    }

    fun processPropertiesFromCatalog(name: String?, place: PsiElement, processor: PsiScopeProcessor, state: ResolveState) : Set<String>? {
      if (name == null) {
        // this case is possible when only a part of a catalog name is written and autocomplete is triggered
        return processAllCatalogsOfBuild(place, processor, state)
      }
      val accessor = getVersionCatalogAccessor(place, name) ?: return emptySet()
      val element = GradleExtensionsContributorUtil.Companion.StaticVersionCatalogProperty(place, name, accessor)
      if (!processor.execute(element, state)) {
        return null // to stop processing
      }
      return setOf(name)
    }

    private fun processAllCatalogsOfBuild(place: PsiElement, processor: PsiScopeProcessor, state: ResolveState): Set<String>? {
      val catalogNameToAccessor: Map<String, PsiClass> = getAccessorsForAllCatalogs(place)
      catalogNameToAccessor.forEach { (catalogName, accessor) ->
        if (!processor.execute(GradleExtensionsContributorUtil.Companion.StaticVersionCatalogProperty(place, catalogName, accessor), state)) {
          return null // to stop processing
        }
      }
      return catalogNameToAccessor.keys
    }
  }
}