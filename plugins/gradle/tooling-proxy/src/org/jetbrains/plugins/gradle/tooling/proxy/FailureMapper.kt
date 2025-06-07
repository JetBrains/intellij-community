// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.proxy

import org.gradle.tooling.Failure
import org.gradle.tooling.events.problems.*
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.InternalFailure
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.problem.*

fun toInternalFailure(failure: Failure?): InternalFailure? {
  if (failure == null) {
    return null
  }
  return InternalFailure(
    failure.message,
    failure.description,
    failure.causes?.let { mapCauses(it) },
    failure.problems?.let { mapProblems(it) }
  )
}

private fun toInternalProblem(problem: Problem?): InternalProblem? {
  if (problem == null) {
    return null
  }
  return InternalProblem(
    map(problem.definition),
    map(problem.contextualLabel),
    map(problem.details),
    emptyList(),
    emptyList(),
    mapSolutions(problem.solutions),
    toInternalFailure(problem.failure),
    map(problem.additionalData)
  )
}

private fun mapCauses(causes: List<Failure>): List<InternalFailure?> {
  return causes.map<Failure?, InternalFailure?>(::toInternalFailure)
}

private fun mapProblems(problems: List<Problem?>): List<InternalProblem?> {
  return problems.map<Problem?, InternalProblem?>(::toInternalProblem)
}

private fun map(data: AdditionalData): InternalAdditionalData {
  return InternalAdditionalData(data.asMap)
}

private fun mapSolutions(solutions: List<Solution?>): List<Solution?> {
  return solutions.map<Solution?, Solution?>(::map)
}

private fun map(solution: Solution?): Solution? {
  if (solution == null) {
    return null
  }
  return InternalSolution(solution.solution)
}

private fun map(definition: ProblemDefinition): InternalProblemDefinition {
  return InternalProblemDefinition(
    map(definition.id),
    map(definition.severity),
    definition.documentationLink?.let { InternalDocumentationLink(it.url) }
  )
}

private fun map(label: ContextualLabel): InternalContextualLabel {
  return InternalContextualLabel(label.contextualLabel)
}

private fun map(details: Details): InternalDetails {
  return InternalDetails(details.details)
}

private fun map(id: ProblemId): InternalProblemId {
  return InternalProblemId(
    id.name,
    id.displayName,
    map(id.group)
  )
}

private fun map(group: ProblemGroup): InternalProblemGroup {
  return InternalProblemGroup(
    group.name,
    group.displayName,
    group.parent?.let { map(it) }
  )
}

private fun map(severity: Severity): InternalSeverity {
  return InternalSeverity(
    severity.severity,
    severity.isKnown
  )
}
