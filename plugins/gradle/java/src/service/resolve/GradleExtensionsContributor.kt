// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.icons.AllIcons
import com.intellij.lang.properties.IProperty
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.*
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.castSafelyTo
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_PROJECT
import org.jetbrains.plugins.gradle.service.resolve.staticModel.impl.getStaticPluginModel
import org.jetbrains.plugins.gradle.settings.GradleExtensionsSettings
import org.jetbrains.plugins.gradle.settings.GradleExtensionsSettings.GradleExtensionsData
import org.jetbrains.plugins.gradle.settings.GradleLocalSettings
import org.jetbrains.plugins.gradle.util.PROPERTIES_FILE_NAME
import org.jetbrains.plugins.gradle.util.getGradleUserHomePropertiesPath
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightField
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor
import org.jetbrains.plugins.groovy.lang.resolve.getName
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type
import org.jetbrains.plugins.groovy.lang.resolve.shouldProcessProperties
import java.nio.file.Path

class GradleExtensionsContributor : NonCodeMembersContributor() {

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


    val properties = if (name == null) data.findAllProperties() else listOfNotNull(data.findProperty(name))
    val dynamicPropertiesNames = properties.map { it.name }

    val staticProperties = getStaticPluginModel(place.containingFile).extensions.filter { if (name == null) it.name !in dynamicPropertiesNames else it.name == name }


    for (property in properties) {
      if (property.name in resolvedProperties) {
        continue
      }
      if (!processor.execute(GradleGroovyProperty(property.name, property.typeFqn, property.value, file), state)) {
        return
      }
    }

    for (property in staticProperties) {
      if (property.name in resolvedProperties) {
        continue
      }
      if (!processor.execute(GradleGroovyProperty(property.name, property.type, null, file), state)) {
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
    fun gradlePropertiesStream(place: PsiElement): Sequence<PropertiesFile> = sequence {
      val externalRootProjectPath = place.getRootGradleProjectPath() ?: return@sequence
      val userHomePropertiesFile = getGradleUserHomePropertiesPath()?.parent?.toString()?.getGradlePropertiesFile(place.project)
      if (userHomePropertiesFile != null) {
        yield(userHomePropertiesFile)
      }
      val projectRootPropertiesFile = externalRootProjectPath.getGradlePropertiesFile(place.project)
      if (projectRootPropertiesFile != null) {
        yield(projectRootPropertiesFile)
      }
      val localSettings = GradleLocalSettings.getInstance(place.project)
      val installationDirectoryPropertiesFile = localSettings.getGradleHome(externalRootProjectPath)?.getGradlePropertiesFile(place.project)
      if (installationDirectoryPropertiesFile != null) {
        yield(installationDirectoryPropertiesFile)
      }
    }

    private fun String.getGradlePropertiesFile(project: Project): PropertiesFile? {
      val file = VfsUtil.findFile(Path.of(this), false)?.findChild(PROPERTIES_FILE_NAME)
      return file?.let { PsiUtilCore.getPsiFile(project, it) }.castSafelyTo<PropertiesFile>()
    }

    private fun createGroovyProperty(aClass: PsiClass,
                                     property: IProperty,
                                     stringType: PsiClassType): GrLightField {
      val newProperty = GrLightField(aClass, property.name, stringType, property.psiElement)
      newProperty.setIcon(AllIcons.FileTypes.Properties)
      newProperty.originInfo = propertiesFileOriginInfo
      return newProperty
    }


    fun getExtensionsFor(psiElement: PsiElement): GradleExtensionsData? {
      val project = psiElement.project
      val virtualFile = psiElement.containingFile?.originalFile?.virtualFile ?: return null
      val module = ProjectFileIndex.getInstance(project).getModuleForFile(virtualFile)
      return GradleExtensionsSettings.getInstance(project).getExtensionsFor(module)
    }

    internal const val propertiesFileOriginInfo : String = "by gradle.properties"
  }
}
