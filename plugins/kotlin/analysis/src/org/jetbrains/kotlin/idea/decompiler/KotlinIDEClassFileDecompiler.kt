// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.decompiler

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.compiled.ClassFileDecompilers
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinDecompiledFileViewProvider
import org.jetbrains.kotlin.analysis.decompiler.psi.file.KtClsFile
import org.jetbrains.kotlin.analysis.decompiler.stub.file.ClsClassFinder
import org.jetbrains.kotlin.analysis.decompiler.stub.file.ClsKotlinBinaryClassCache
import org.jetbrains.kotlin.analysis.decompiler.stub.file.KotlinClsStubBuilder

private val LOG = Logger.getInstance(KotlinIDEClassFileDecompiler::class.java)
/**
 * Copy-paste from [org.jetbrains.kotlin.analysis.decompiler.psi.KotlinClassFileDecompiler] to workaround https://youtrack.jetbrains.com/issue/KTIJ-21472
 */
class KotlinIDEClassFileDecompiler : ClassFileDecompilers.Full() {

    private val stubBuilder = KotlinClsStubBuilder()

    override fun accepts(file: VirtualFile) = try {
      ClsKotlinBinaryClassCache.getInstance().isKotlinJvmCompiledFile(file)
    } catch (e: Exception) {
        LOG.warn("unable to handle $file: ${e.message}")
        false
    }

    override fun getStubBuilder() = stubBuilder

    override fun createFileViewProvider(file: VirtualFile, manager: PsiManager, physical: Boolean): KotlinDecompiledFileViewProvider {
        return KotlinDecompiledFileViewProvider(manager, file, physical) factory@{ provider ->
            val virtualFile = provider.virtualFile

            if (ClsClassFinder.isKotlinInternalCompiledFile(virtualFile))
                null
            else
                KtClsFile(provider)
        }
    }
}