// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch

import com.google.gson.*
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.vcs.editor.ComplexPathVirtualFileSystem
import com.intellij.vcs.editor.GsonComplexPathSerializer
import com.intellij.vcs.log.VcsLogRangeFilter
import java.lang.reflect.Type

internal class GitCompareBranchesVirtualFileSystem : ComplexPathVirtualFileSystem<GitCompareBranchesVirtualFileSystem.ComplexPath>(
  GsonComplexPathSerializer(
    pathClass = ComplexPath::class.java,
    gson = GsonBuilder().registerTypeAdapter(VirtualFile::class.java, VirtualFileSerializer()).create()
  )
) {
  override fun getProtocol(): String = PROTOCOL

  override fun findOrCreateFile(project: Project, path: ComplexPath): VirtualFile? {
    return GitCompareBranchesFilesManager.getInstance(project).findOrCreateFile(path)
  }

  data class ComplexPath(override val sessionId: String,
                         override val projectHash: String,
                         val ranges: List<VcsLogRangeFilter.RefRange>,
                         val roots: Collection<VirtualFile>?) : ComplexPathVirtualFileSystem.ComplexPath

  companion object {
    private const val PROTOCOL = "git-compare-branches"

    @JvmStatic
    fun getInstance() = service<VirtualFileManager>().getFileSystem(PROTOCOL) as GitCompareBranchesVirtualFileSystem
  }
}

private class VirtualFileSerializer : JsonSerializer<VirtualFile>, JsonDeserializer<VirtualFile> {
  override fun serialize(src: VirtualFile, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
    return JsonObject().also { it.addProperty(pathProperty, src.path) }
  }

  override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): VirtualFile {
    val path = json.asJsonObject.get(pathProperty).asString
    return LocalFileSystem.getInstance().findFileByPath(path) ?: throw JsonParseException("Could not find file by $path")
  }

  companion object {
    private const val pathProperty = "path"
  }
}
