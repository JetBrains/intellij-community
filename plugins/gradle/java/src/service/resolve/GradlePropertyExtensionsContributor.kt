// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

//import org.jetbrains.plugins.gradle.service.resolve.static.getStaticallyHandledExtensions
import com.intellij.icons.AllIcons
import com.intellij.lang.properties.IProperty
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.*
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_PROJECT
import org.jetbrains.plugins.gradle.settings.GradleExtensionsSettings
import org.jetbrains.plugins.gradle.settings.GradleExtensionsSettings.GradleExtensionsData
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightField
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyPropertyBase
import org.jetbrains.plugins.groovy.lang.resolve.getName
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type
import org.jetbrains.plugins.groovy.lang.resolve.shouldProcessProperties

class GradlePropertyExtensionsContributor : NonCodeMembersContributor() {

  override fun getClassNames(): Collection<String> {
    return listOf(GradleCommonClassNames.GRADLE_API_EXTRA_PROPERTIES_EXTENSION, GRADLE_API_PROJECT)
  }

  override fun processDynamicElements(qualifierType: PsiType,
                                      aClass: PsiClass?,
                                      processor: PsiScopeProcessor,
                                      place: PsiElement,
                                      state: ResolveState) {
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
                                        state: ResolveState) : Set<String> {
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
                                     stringType: PsiClassType): GrLightField {
      val newProperty = GrLightField(aClass, property.name, stringType, property.psiElement)
      newProperty.setIcon(AllIcons.FileTypes.Properties)
      newProperty.originInfo = PROPERTIES_FILE_ORIGINAL_INFO
      return newProperty
    }


    class StaticVersionCatalogProperty(place: PsiElement, name: String, val clazz: PsiClass) : GroovyPropertyBase(name, place) {
      override fun getPropertyType(): PsiType {
        return PsiElementFactory.getInstance(project).createType(clazz, PsiSubstitutor.EMPTY)
      }
    }

    fun processPropertiesFromCatalog(name: String?, place: PsiElement, processor: PsiScopeProcessor, state: ResolveState) : Set<String>? {
      val staticExtensions = getGradleStaticallyHandledExtensions(place.project)
      val names = if (name == null) staticExtensions else listOf(name).filter { it in staticExtensions }
      val properties = mutableSetOf<String>()
      for (extName in names) {
        val accessor = getVersionCatalogAccessor(place, extName) ?: continue
        if (!processor.execute(StaticVersionCatalogProperty(place, extName, accessor), state)) {
          return null
        }
      }
      return properties
    }

    fun getExtensionsFor(psiElement: PsiElement): GradleExtensionsData? {
      val project = psiElement.project
      val virtualFile = psiElement.containingFile?.originalFile?.virtualFile ?: return null
      val module = ProjectFileIndex.getInstance(project).getModuleForFile(virtualFile)
      return GradleExtensionsSettings.getInstance(project).getExtensionsFor(module)
    }

    internal const val PROPERTIES_FILE_ORIGINAL_INFO : String = "by gradle.properties"
  }
}
