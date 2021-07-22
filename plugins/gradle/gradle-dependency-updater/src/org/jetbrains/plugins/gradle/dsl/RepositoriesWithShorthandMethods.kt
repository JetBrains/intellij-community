// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.dsl

import com.android.tools.idea.gradle.dsl.api.repositories.RepositoryModel.RepositoryType
import java.net.URI

enum class RepositoriesWithShorthandMethods(
  val methodName: String,
  val dslRepositoryType: RepositoryType,
  val repositoryId: String? = null,
  val urls: Set<URI>
) {

  JCENTER(methodName = "jcenter", dslRepositoryType = RepositoryType.MAVEN,
          urls = setOf(URI.create("https://jcenter.bintray.com"))),
  MAVEN_CENTRAL(methodName = "mavenCentral", dslRepositoryType = RepositoryType.MAVEN_CENTRAL,
                repositoryId = "maven_central",
                urls = setOf(URI.create("https://repo.maven.apache.org/maven2/"),
                             URI.create("https://repo1.maven.org/maven2"),
                             URI.create("https://maven-central.storage-download.googleapis.com/maven2"))),
  GOOGLE_MAVEN(methodName = "gmaven", dslRepositoryType = RepositoryType.GOOGLE_DEFAULT, repositoryId = "google",
               urls = setOf(URI.create("https://maven.google.com")));

  companion object {

    fun findByUrlLenient(url: String): RepositoriesWithShorthandMethods? =
      values().find { repo -> repo.urls.any { it.isEquivalentLenientTo(url) } }

    fun findByRepoType(repoType: RepositoryType): RepositoriesWithShorthandMethods? =
      values().find { repo -> repo.dslRepositoryType == repoType }

    private fun URI.isEquivalentLenientTo(url: String?): Boolean {
      if (url == null) return false
      val otherUriNormalized = URI(url.trim().trimEnd('/', '?', '#')).normalize()
      val thisUriNormalized = URI(toASCIIString().trim().trimEnd('/', '?', '#')).normalize()
      return thisUriNormalized == otherUriNormalized
    }
  }
}
