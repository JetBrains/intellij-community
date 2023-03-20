// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.projectView

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.AbstractPsiBasedNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.parentOfType
import com.intellij.util.SmartList
import org.jetbrains.kotlin.analysis.decompiler.psi.file.KtClsFile
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.fileClasses.isJvmMultifileClassFile
import org.jetbrains.kotlin.idea.decompiler.navigation.SourceNavigationHelper
import org.jetbrains.kotlin.idea.stubindex.KotlinFileFacadeFqNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinJvmNameAnnotationIndex
import org.jetbrains.kotlin.load.kotlin.PackagePartClassUtils
import org.jetbrains.kotlin.name.JvmNames
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFileAnnotationList

class KtInternalFileTreeNode(project: Project?, lightClass: KtLightClass, viewSettings: ViewSettings) :
    AbstractPsiBasedNode<KtLightClass>(project, lightClass, viewSettings) {

    private val navigatablePsiElement: SmartPsiElementPointer<KtElement>? by lazy {
        val ktClsFile = value?.navigationElement as? KtClsFile
        val virtualFile = ktClsFile?.containingFile?.virtualFile ?: return@lazy null
        val prj = getProject()
        val baseName = virtualFile.nameWithoutExtension
        val smartPointerManager = SmartPointerManager.getInstance(prj)
        val scopes = SourceNavigationHelper.targetClassFilesToSourcesScopes(virtualFile, prj)
        if (scopes.isEmpty()) return@lazy null
        val scope = GlobalSearchScope.union(scopes)

        val originalPackageName = ktClsFile.packageFqName
        val filesFromFacade = SmartList<KtFile>()
        KotlinJvmNameAnnotationIndex.processElements(baseName.substringBefore(JvmNames.MULTIFILE_PART_NAME_DELIMITER), prj, scope) {
            ProgressManager.checkCanceled()
            if (it.parentOfType<KtFileAnnotationList>() != null) {
                it.containingKtFile.takeIf { ktFile -> ktFile.packageFqName == originalPackageName }?.let(filesFromFacade::add)
            }

            true
        }

        val partShortName = baseName.substringAfter(JvmNames.MULTIFILE_PART_NAME_DELIMITER)
        if (baseName.contains(JvmNames.MULTIFILE_PART_NAME_DELIMITER)) {
            for (ktFile in filesFromFacade) {
                if (ktFile.isJvmMultifileClassFile && PackagePartClassUtils.getFilePartShortName(ktFile.name) == partShortName) {
                    return@lazy smartPointerManager.createSmartPsiElementPointer(ktFile)
                }
            }
        }

        // do not navigate to source if it is a facade file
        val file = filesFromFacade.singleOrNull() ?: run {
            // top level functions and properties are located in files like `SomeClassKt.class`
            val fqName = originalPackageName.child(Name.identifier(baseName))
            KotlinFileFacadeFqNameIndex[fqName.asString(), prj, scope].singleOrNull()
        }

        file?.let(smartPointerManager::createSmartPsiElementPointer)
    }

    override fun extractPsiFromValue(): PsiElement? = navigatablePsiElement?.element ?: value

    override fun getChildrenImpl(): Collection<AbstractTreeNode<*>> {
        if (!settings.isShowMembers) return emptyList()

        return (extractPsiFromValue() as? KtFile)?.toDeclarationsNodes(settings) ?: emptyList()
    }

    override fun canRepresent(element: Any?): Boolean {
        if (super.canRepresent(element)) return true
        val value: PsiElement? = extractPsiFromValue()
        val elementVirtualFile = (element as? PsiElement)?.navigationElement?.containingFile?.virtualFile
        val virtualFile = value?.containingFile?.virtualFile
        return elementVirtualFile == virtualFile
    }

    override fun updateImpl(data: PresentationData) {
        value?.let {
            data.presentableText = it.name
        }
    }
}