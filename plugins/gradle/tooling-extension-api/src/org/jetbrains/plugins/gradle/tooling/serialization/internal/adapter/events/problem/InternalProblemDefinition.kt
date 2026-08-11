// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.problem

import org.gradle.tooling.events.problems.DocumentationLink
import org.gradle.tooling.events.problems.ProblemDefinition
import org.gradle.tooling.events.problems.ProblemId
import org.gradle.tooling.events.problems.Severity
import org.jetbrains.annotations.ApiStatus
import java.io.Serializable

@ApiStatus.Internal
class InternalProblemDefinition(
  private val id: ProblemId,
  private val severity: Severity,
  private val documentationLink: DocumentationLink?,
) : Serializable, ProblemDefinition {

  constructor(definition: ProblemDefinition) : this(
    InternalProblemId(definition.id),
    InternalSeverity(definition.severity),
    if (definition.documentationLink == null) null else InternalDocumentationLink(definition.documentationLink!!)
  )

  override fun getId(): ProblemId = id

  override fun getSeverity(): Severity = severity

  override fun getDocumentationLink(): DocumentationLink? = documentationLink
}
