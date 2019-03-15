package org.jetbrains.java.knownAnnotations

import com.google.gson.Gson
import com.intellij.codeInsight.externalAnnotation.location.AnnotationsLocation
import com.intellij.codeInsight.externalAnnotation.location.AnnotationsLocationProvider
import com.intellij.ide.extensionResources.ExtensionsRootType
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.text.VersionComparatorUtil

class JBBundledAnnotationsProvider : AnnotationsLocationProvider {

  private val myPluginId = PluginId.getId("org.jetbrains.java.knownAnnotations")
  private val packagePrefix = "org.jetbrains.externalAnnotations."
  private val knownAnnotations: Collection<AnnotationsLocation> by lazy { buildAnnotations() }

  override fun getLocations(library: Library,
                            artifactId: String?,
                            groupId: String?,
                            version: String?): Collection<AnnotationsLocation> {

    if (artifactId == null) return emptyList()
    if (groupId == null) return emptyList()
    if (version == null) return emptyList()

    return knownAnnotations.asSequence()
      .filter {
        it.groupId == groupId &&
        it.artifactId == artifactId &&
        versionMatches(it.version, version)
      }
      .map {
        AnnotationsLocation(packagePrefix + it.groupId, it.artifactId, it.version, *it.repositoryUrls.toTypedArray())
      }
      .toList()
  }

  private fun versionMatches(available: String, requested: String): Boolean {
    val majorRequested = extractMajor(requested)
    val availableWithoutAnSuffix = dropAnSuffix(available)

    return VersionComparatorUtil.compare(majorRequested, available) <= 0
           && VersionComparatorUtil.compare(availableWithoutAnSuffix, requested) <= 0
  }

  private fun extractMajor(versionStr: String): String = versionStr.split('(', ')', '.', '_', '-', ';', ':', '/', ',', ' ', '+', '~')[0]
  private fun dropAnSuffix(versionStr: String): String = versionStr.split(Regex("-an[\\d]+$"))[0]

  private fun buildAnnotations(): Collection<AnnotationsLocation> {
    val extensionsRootType = ExtensionsRootType.getInstance()
    val annotationsFile = extensionsRootType.findResource(myPluginId, "predefinedExternalAnnotations.json")
                          ?: extensionsRootType.run {
                            extractBundledResources(myPluginId, "")
                            findResource(myPluginId, "predefinedExternalAnnotations.json")
                          }
                          ?: return emptyList()

    val raw: Array<RepositoryDescriptor> =
      Gson().fromJson(FileUtil.loadFile(annotationsFile, Charsets.UTF_8), Array<RepositoryDescriptor>::class.java)

    return raw.asSequence()
      .flatMap { rd ->
        rd.artifacts.asSequence().map { ad ->
          AnnotationsLocation(ad.groupId, ad.artifactId, ad.version, rd.repositoryUrl)
        }
      }.toList()
  }

  private data class RepositoryDescriptor(val repositoryUrl: String, val artifacts: Array<ArtifactDescriptor>) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is RepositoryDescriptor) return false

      if (repositoryUrl != other.repositoryUrl) return false
      if (!artifacts.contentEquals(other.artifacts)) return false

      return true
    }

    override fun hashCode(): Int {
      var result = repositoryUrl.hashCode()
      result = 31 * result + artifacts.contentHashCode()
      return result
    }
  }

  private data class ArtifactDescriptor(val groupId: String, val artifactId: String, val version: String)
}