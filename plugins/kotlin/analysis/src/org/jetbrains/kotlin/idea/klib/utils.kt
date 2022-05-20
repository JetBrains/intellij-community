// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.klib

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.SingleRootFileViewProvider
import com.intellij.psi.impl.PsiFileFactoryImpl
import com.intellij.psi.stubs.PsiFileStub
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.backend.common.serialization.metadata.KlibMetadataModuleDescriptorFactory
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentProvider
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.compatibilityInfo
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementTypes
import org.jetbrains.kotlin.resolve.CompilerDeserializationConfiguration
import org.jetbrains.kotlin.storage.StorageManager
import java.io.IOException

fun createFileStub(project: Project, text: String): PsiFileStub<*> {
    val virtualFile = LightVirtualFile("dummy.kt", KotlinFileType.INSTANCE, text)
    virtualFile.language = KotlinLanguage.INSTANCE
    SingleRootFileViewProvider.doNotCheckFileSizeLimit(virtualFile)

    val psiFileFactory = PsiFileFactory.getInstance(project) as PsiFileFactoryImpl
    val file = psiFileFactory.trySetupPsiForFile(virtualFile, KotlinLanguage.INSTANCE, false, false)!!
    return KtStubElementTypes.FILE.builder.buildStubTree(file) as PsiFileStub<*>
}

fun KotlinLibrary.createKlibPackageFragmentProvider(
    storageManager: StorageManager,
    metadataModuleDescriptorFactory: KlibMetadataModuleDescriptorFactory,
    languageVersionSettings: LanguageVersionSettings,
    moduleDescriptor: ModuleDescriptor,
    lookupTracker: LookupTracker
): PackageFragmentProvider? {
    if (!compatibilityInfo.isCompatible) return null

    val packageFragmentNames = CachingIdeKlibMetadataLoader.loadModuleHeader(this).packageFragmentNameList

    return metadataModuleDescriptorFactory.createPackageFragmentProvider(
        library = this,
        packageAccessHandler = CachingIdeKlibMetadataLoader,
        packageFragmentNames = packageFragmentNames,
        storageManager = storageManager,
        moduleDescriptor = moduleDescriptor,
        configuration = CompilerDeserializationConfiguration(languageVersionSettings),
        compositePackageFragmentAddend = null,
        lookupTracker = lookupTracker
    )
}
