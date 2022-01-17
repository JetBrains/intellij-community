// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.roots

import com.intellij.conversion.*
import com.intellij.conversion.impl.ConversionContextImpl
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.ApplicationLibraryTable
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryKindRegistry
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.jdom.Element
import org.jetbrains.jps.model.JpsElement
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.jps.model.module.JpsTypedModuleSourceRoot
import org.jetbrains.jps.model.serialization.facet.JpsFacetSerializer
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer.*
import org.jetbrains.kotlin.config.getFacetPlatformByConfigurationElement
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import org.jetbrains.kotlin.idea.framework.JavaRuntimeDetectionUtil
import org.jetbrains.kotlin.idea.framework.JsLibraryStdDetectionUtil
import org.jetbrains.kotlin.idea.framework.getLibraryJar
import org.jetbrains.kotlin.platform.CommonPlatforms
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.js.JsPlatforms
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.utils.PathUtil
import java.util.*

private val rootTypesToMigrate: List<JpsModuleSourceRootType<*>> = listOf(
    JavaSourceRootType.SOURCE,
    JavaSourceRootType.TEST_SOURCE,
    JavaResourceRootType.RESOURCE,
    JavaResourceRootType.TEST_RESOURCE
)

// TODO(dsavvinov): review how it behaves in HMPP environment
private val PLATFORM_TO_STDLIB_DETECTORS: Map<TargetPlatform, (Array<VirtualFile>) -> Boolean> = mapOf(
    JvmPlatforms.unspecifiedJvmPlatform to { roots: Array<VirtualFile> ->
        JavaRuntimeDetectionUtil.getRuntimeJar(roots.toList()) != null
    },
    JsPlatforms.defaultJsPlatform to { roots: Array<VirtualFile> ->
        JsLibraryStdDetectionUtil.getJsStdLibJar(roots.toList()) != null
    },
    CommonPlatforms.defaultCommonPlatform to { roots: Array<VirtualFile> ->
        getLibraryJar(roots, PathUtil.KOTLIN_STDLIB_COMMON_JAR_PATTERN) != null
    }
)

internal class KotlinNonJvmSourceRootConverterProvider : ConverterProvider() {
    sealed class LibInfo {
        class ByXml(
            private val element: Element,
            private val conversionContext: ConversionContext,
            private val moduleSettings: ModuleSettings
        ) : LibInfo() {
            override val explicitKind: PersistentLibraryKind<*>?
                get() = LibraryKindRegistry.getInstance().findKindById(element.getAttributeValue("type")) as? PersistentLibraryKind<*>

            override fun getRoots(): Array<VirtualFile> {
                val contextImpl = conversionContext as? ConversionContextImpl ?: return VirtualFile.EMPTY_ARRAY
                val classRoots = contextImpl.getClassRootUrls(element, moduleSettings)
                val jarFileSystem = JarFileSystem.getInstance()
                return classRoots
                    .map {
                        if (it.startsWith(JarFileSystem.PROTOCOL_PREFIX)) {
                            jarFileSystem.findFileByPath(it.substring(JarFileSystem.PROTOCOL_PREFIX.length))
                        } else {
                            null
                        }
                    }
                    .filter(Objects::nonNull)
                    .toArray{ arrayOfNulls<VirtualFile>(it) }
            }
        }

        class ByLibrary(private val library: Library) : LibInfo() {
            override val explicitKind: PersistentLibraryKind<*>?
                get() = (library as? LibraryEx)?.kind

            override fun getRoots(): Array<VirtualFile> = library.getFiles(OrderRootType.CLASSES)
        }

        abstract val explicitKind: PersistentLibraryKind<*>?
        abstract fun getRoots(): Array<VirtualFile>

        val stdlibPlatform: TargetPlatform? by lazy {
            val roots = getRoots()
            for ((platform, detector) in PLATFORM_TO_STDLIB_DETECTORS) {
                if (detector.invoke(roots)) {
                    return@lazy platform
                }
            }

            return@lazy null
        }
    }

    class ConverterImpl(private val context: ConversionContext) : ProjectConverter() {
        private val projectLibrariesByName by lazy {
            context.projectLibrariesSettings.projectLibraries.groupBy { it.getAttributeValue(NAME_ATTRIBUTE) }
        }

        private fun findGlobalLibrary(name: String) = ApplicationLibraryTable.getApplicationTable().getLibraryByName(name)

        private fun findProjectLibrary(name: String) = projectLibrariesByName[name]?.firstOrNull()

        private fun createLibInfo(orderEntryElement: Element, moduleSettings: ModuleSettings): LibInfo? {
            return when (orderEntryElement.getAttributeValue("type")) {
                MODULE_LIBRARY_TYPE -> {
                    orderEntryElement.getChild(LIBRARY_TAG)?.let { LibInfo.ByXml(it, context, moduleSettings) }
                }

                LIBRARY_TYPE -> {
                    val libraryName = orderEntryElement.getAttributeValue(NAME_ATTRIBUTE) ?: return null
                    when (orderEntryElement.getAttributeValue(LEVEL_ATTRIBUTE)) {
                        LibraryTablesRegistrar.PROJECT_LEVEL ->
                            findProjectLibrary(libraryName)?.let { LibInfo.ByXml(it, context, moduleSettings) }
                        LibraryTablesRegistrar.APPLICATION_LEVEL ->
                            findGlobalLibrary(libraryName)?.let { LibInfo.ByLibrary(it) }
                        else ->
                            null
                    }
                }

                else -> null
            }
        }

        override fun createModuleFileConverter(): ConversionProcessor<ModuleSettings> {
            return object : ConversionProcessor<ModuleSettings>() {
                private fun ModuleSettings.detectPlatformByFacet(): TargetPlatform? {
                    return getFacetElement(KotlinFacetType.ID)
                        ?.getChild(JpsFacetSerializer.CONFIGURATION_TAG)
                        ?.getFacetPlatformByConfigurationElement()
                }


                private fun detectPlatformByDependencies(moduleSettings: ModuleSettings): TargetPlatform? {
                    var hasCommonStdlib = false

                    moduleSettings.orderEntries
                        .asSequence()
                        .mapNotNull { createLibInfo(it, moduleSettings) }
                        .forEach {
                            val stdlibPlatform = it.stdlibPlatform
                            if (stdlibPlatform != null) {
                                if (stdlibPlatform.isCommon()) {
                                    hasCommonStdlib = true
                                } else {
                                    return stdlibPlatform
                                }
                            }
                        }

                    return if (hasCommonStdlib) CommonPlatforms.defaultCommonPlatform else null
                }

                private fun ModuleSettings.detectPlatform(): TargetPlatform {
                    return detectPlatformByFacet()
                        ?: detectPlatformByDependencies(this)
                        ?: JvmPlatforms.unspecifiedJvmPlatform
                }

                private fun ModuleSettings.getSourceFolderElements(): List<Element> {
                    val rootManagerElement = getComponentElement(ModuleSettings.MODULE_ROOT_MANAGER_COMPONENT) ?: return emptyList()
                    return rootManagerElement
                        .getChildren(CONTENT_TAG)
                        .flatMap { it.getChildren(SOURCE_FOLDER_TAG) }
                }

                @Suppress("UnstableApiUsage")
                private fun ModuleSettings.isExternalModule(): Boolean {
                    return when {
                        rootElement.getAttributeValue(ExternalProjectSystemRegistry.EXTERNAL_SYSTEM_ID_KEY) != null -> true
                        rootElement.getAttributeValue(ExternalProjectSystemRegistry.IS_MAVEN_MODULE_KEY)?.toBoolean() ?: false -> true
                        else -> false
                    }
                }

                override fun isConversionNeeded(settings: ModuleSettings): Boolean {
                    if (settings.isExternalModule()) return false

                    val hasMigrationRoots = settings.getSourceFolderElements().any {
                        loadSourceRoot(it).rootType in rootTypesToMigrate
                    }
                    if (!hasMigrationRoots) {
                        return false
                    }

                    val targetPlatform = settings.detectPlatform()
                    return (!targetPlatform.isJvm())
                }

                override fun process(settings: ModuleSettings) {
                    for (sourceFolder in settings.getSourceFolderElements()) {
                        val contentRoot = sourceFolder.parent as? Element ?: continue
                        val oldSourceRoot = loadSourceRoot(sourceFolder)
                        val (newRootType, data) = oldSourceRoot.getMigratedSourceRootTypeWithProperties() ?: continue
                        val url = sourceFolder.getAttributeValue(URL_ATTRIBUTE)!!
                        @Suppress("UNCHECKED_CAST")
                        val newSourceRoot = JpsElementFactory.getInstance().createModuleSourceRoot(url, newRootType, data)
                                as? JpsTypedModuleSourceRoot<JpsElement> ?: continue

                        contentRoot.removeContent(sourceFolder)
                        saveSourceRoot(contentRoot, url, newSourceRoot)
                    }
                }
            }
        }
    }

    override fun getConversionDescription() =
        KotlinBundle.message("roots.description.text.update.source.roots.for.non.jvm.modules.in.kotlin.project")

    override fun createConverter(context: ConversionContext) = ConverterImpl(context)
}