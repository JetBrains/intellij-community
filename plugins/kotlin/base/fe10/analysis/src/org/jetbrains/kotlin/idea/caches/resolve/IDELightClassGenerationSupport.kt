// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.java.analysis.OuterModelsModificationTrackerManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.containers.ConcurrentFactoryMap
import org.jetbrains.kotlin.asJava.LightClassGenerationSupport
import org.jetbrains.kotlin.asJava.classes.KtUltraLightSupport
import org.jetbrains.kotlin.asJava.classes.cleanFromAnonymousTypes
import org.jetbrains.kotlin.asJava.classes.lazyPub
import org.jetbrains.kotlin.asJava.classes.tryGetPredefinedName
import org.jetbrains.kotlin.codegen.JvmCodegenUtil
import org.jetbrains.kotlin.codegen.state.KotlinTypeMapper
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.idea.FrontendInternals
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.stubindex.KotlinTypeAliasShortNameIndex
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.deprecation.DeprecationResolver
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import java.util.concurrent.ConcurrentMap

class IDELightClassGenerationSupport : LightClassGenerationSupport() {
    private class KtUltraLightSupportImpl(private val element: KtElement) : KtUltraLightSupport {

        private val module = ModuleUtilCore.findModuleForPsiElement(element)

        override val languageVersionSettings: LanguageVersionSettings
            get() = module?.languageVersionSettings ?: LanguageVersionSettingsImpl.DEFAULT

        private val resolutionFacade get() = element.getResolutionFacade()

        override val moduleDescriptor get() = resolutionFacade.moduleDescriptor

        override val moduleName: String by lazyPub {
            JvmCodegenUtil.getModuleName(moduleDescriptor)
        }

        override fun possiblyHasAlias(file: KtFile, shortName: Name): Boolean =
            allAliases(file)[shortName.asString()] == true

        private fun allAliases(file: KtFile): ConcurrentMap<String, Boolean> {
            val project = file.project
            return CachedValuesManager.getCachedValue(file) {
                val importAliases = file.importDirectives.mapNotNull { it.aliasName }.toSet()
                val map = ConcurrentFactoryMap.createMap<String, Boolean> { s ->
                    s in importAliases || KotlinTypeAliasShortNameIndex.get(s, project, file.resolveScope).isNotEmpty()
                }
                Result.create<ConcurrentMap<String, Boolean>>(map, OuterModelsModificationTrackerManager.getTracker(project))
            }
        }

        @OptIn(FrontendInternals::class)
        override val deprecationResolver: DeprecationResolver
            get() = resolutionFacade.getFrontendService(DeprecationResolver::class.java)

        override val typeMapper: KotlinTypeMapper by lazyPub {
            KotlinTypeMapper(
                moduleName, languageVersionSettings,
                useOldInlineClassesManglingScheme = false,
                typePreprocessor = KotlinType::cleanFromAnonymousTypes,
                namePreprocessor = ::tryGetPredefinedName
            )
        }
    }

    override fun getUltraLightClassSupport(element: KtElement): KtUltraLightSupport = KtUltraLightSupportImpl(element)

    override fun resolveToDescriptor(declaration: KtDeclaration): DeclarationDescriptor? =
        declaration.resolveToDescriptorIfAny(BodyResolveMode.FULL)

    override fun analyze(element: KtElement) = element.safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL)

    override fun analyzeAnnotation(element: KtAnnotationEntry): AnnotationDescriptor? = element.resolveToDescriptorIfAny()

    override fun analyzeWithContent(element: KtClassOrObject) = element.safeAnalyzeWithContentNonSourceRootCode()
}
