// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.util

enum class GHSchemaPreview(val mimeType: String) {
  BRANCH_PROTECTION("application/vnd.github.luke-cage-preview+json"),
  CHECKS("application/vnd.github.antiope-preview+json"),
  PR_MERGE_INFO("application/vnd.github.merge-info-preview+json"),
  PR_DRAFT("application/vnd.github.shadow-cat-preview+json")
}