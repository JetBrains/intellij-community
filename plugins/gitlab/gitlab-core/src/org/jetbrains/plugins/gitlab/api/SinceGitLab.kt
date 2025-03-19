// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import org.jetbrains.plugins.gitlab.api.GitLabEdition.Community
import org.jetbrains.plugins.gitlab.api.GitLabEdition.Enterprise
import kotlin.annotation.AnnotationTarget.*

/**
 * (Currently) aesthetic annotation meant to document that a DTO, its field, or a function
 * and all its callers are dependent on the GitLab server version they are running against.
 *
 * This annotation documents this dependency by showing the version from which the functionality
 * is available, the version in which it has been removed and the editions in which the function
 * is available.
 *
 * @param version The version of GitLab from which the functionality or data is made available,
 * expressed as a version string, so *major*.*minor*.*patch*. For example: 16.2.4
 * @param deprecatedIn The version of GitLab from which the functionality or data is no longer
 * available, expressed the same as [version].
 * @param editions The editions of GitLab for which the functionality or data is made available.
 */
@Repeatable
@Target(CLASS, PROPERTY, FIELD, VALUE_PARAMETER, FUNCTION)
annotation class SinceGitLab(
  val version: String,
  val deprecatedIn: String = "",
  val editions: Array<GitLabEdition> = [Community, Enterprise],
  val note: String = ""
)

enum class GitLabEdition {
  Community,
  Enterprise
}
