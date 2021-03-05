/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.core.script.dependencies

import com.intellij.ide.IdeBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.NonPhysicalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem
import com.intellij.psi.PsiManager
import com.intellij.psi.ResolveScopeProvider
import com.intellij.psi.search.DelegatingGlobalSearchScope
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.caches.project.ScriptModuleInfo
import org.jetbrains.kotlin.idea.caches.project.getModuleInfo
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.KotlinScriptDefinitionFromAnnotatedTemplate
import java.io.IOException
import java.io.OutputStream

class KotlinScriptSearchScope(project: Project, baseScope: GlobalSearchScope) : DelegatingGlobalSearchScope(project, baseScope) {
    override fun contains(file: VirtualFile): Boolean {
        return when (file) {
            KotlinScriptMarkerFileSystem.rootFile -> true
            else -> super.contains(file)
        }
    }
}

object KotlinScriptMarkerFileSystem : DummyFileSystem(), NonPhysicalFileSystem {
    override fun getProtocol() = "kotlin-script-dummy"

    val rootFile = object : VirtualFile() {
        override fun getFileSystem() = this@KotlinScriptMarkerFileSystem

        override fun getName() = "root"
        override fun getPath() = "/$name"

        override fun getLength(): Long = 0
        override fun isWritable() = false
        override fun isDirectory() = true
        override fun isValid() = true

        override fun getParent() = null
        override fun getChildren(): Array<VirtualFile> = emptyArray()

        override fun getTimeStamp(): Long = -1
        override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {}

        override fun contentsToByteArray() = throw IOException(IdeBundle.message("file.read.error", url))
        override fun getInputStream() = throw IOException(IdeBundle.message("file.read.error", url))

        override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long): OutputStream {
            throw IOException(IdeBundle.message("file.write.error", url))
        }
    }
}

class KotlinScriptResolveScopeProvider : ResolveScopeProvider() {
    companion object {
        // Used in LivePlugin (that's probably not true anymore)
        @Deprecated("Declaration is deprecated.", level = DeprecationLevel.ERROR)
        val USE_NULL_RESOLVE_SCOPE = "USE_NULL_RESOLVE_SCOPE"
    }

    override fun getResolveScope(file: VirtualFile, project: Project): GlobalSearchScope? {
        if (file.fileType != KotlinFileType.INSTANCE) return null

        val ktFile = PsiManager.getInstance(project).findFile(file) as? KtFile ?: return null
        val scriptDefinition = ktFile.findScriptDefinition() ?: return null

        // This is a workaround for completion in scripts inside module and REPL to provide module dependencies
        if (ktFile.getModuleInfo() !is ScriptModuleInfo) return null

        // This is a workaround for completion in REPL to provide module dependencies
        if (scriptDefinition.baseClassType.fromClass == Any::class) return null

        if (scriptDefinition is ScriptDefinition.FromConfigurationsBase ||
            scriptDefinition.asLegacyOrNull<KotlinScriptDefinitionFromAnnotatedTemplate>() != null
        ) {
            val dependenciesScope = ScriptConfigurationManager.getInstance(project).getScriptDependenciesClassFilesScope(file)
            return KotlinScriptSearchScope(project, GlobalSearchScope.fileScope(project, file).uniteWith(dependenciesScope))
        }

        return null
    }
}