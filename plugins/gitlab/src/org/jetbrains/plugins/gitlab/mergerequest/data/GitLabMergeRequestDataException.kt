// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

internal sealed class GitLabMergeRequestDataException(message: String) : Exception(message) {
  class EmptySourceProject(message: String, val url: String) : GitLabMergeRequestDataException(message)
}