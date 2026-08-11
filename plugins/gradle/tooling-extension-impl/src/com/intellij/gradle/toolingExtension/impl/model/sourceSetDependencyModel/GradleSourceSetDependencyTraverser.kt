// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.sourceSetDependencyModel

import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.internal.file.UnionFileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSetOutput
import org.jetbrains.plugins.gradle.tooling.Message
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import java.util.concurrent.Callable

internal class GradleSourceSetDependencyTraverser(
  private val context: ModelBuilderContext,
  private val project: Project,
) {

  fun traverse(source: Any?, visitor: GradleSourceSetDependencyVisitor) {
    if (unsafeVisit<Callable<*>>(source) { traverse(it.call(), visitor) }) return
    if (unsafeVisit<Provider<*>>(source) { traverse(it.get(), visitor) }) return

    if (unsafeVisit<ConfigurableFileCollection>(source) { traverse(it.from, visitor) }) return
    if (unsafeVisit<UnionFileCollection>(source) { traverse(it.sources, visitor) }) return
    if (unsafeVisit<Configuration>(source) { visitor.visitConfiguration(it) }) return
    if (unsafeVisit<SourceSetOutput>(source) { visitor.visitSourceSetOutput(it) }) return
    if (unsafeVisit<FileCollection>(source) { visitor.visitFileCollection(it) }) return
    if (unsafeVisit<FileSystemLocation>(source) { visitor.visitFile(it) }) return

    if (unsafeVisit<Iterable<*>>(source) { it.forEach { item -> traverse(item, visitor) } }) return

    context.messageReporter.createMessage()
      .withGroup(Messages.DEPENDENCY_RESOLUTION_GROUP)
      .withTitle("Dependency resolution error")
      .withText("Undefined dependency type: ${source?.javaClass}")
      .withKind(Message.Kind.WARNING)
      .reportMessage(project)
  }

  private inline fun <reified T> unsafeVisit(source: Any?, visit: (T) -> Unit): Boolean {
    if (source !is T) return false
    try {
      visit(source)
    }
    catch (e: Exception) {
      context.messageReporter.createMessage()
        .withGroup(Messages.DEPENDENCY_RESOLUTION_GROUP)
        .withTitle("Dependency resolution error")
        .withText("Unable to resolve dependency with type: ${T::class.java}")
        .withKind(Message.Kind.WARNING)
        .withException(e)
        .reportMessage(project)
    }
    return true
  }
}