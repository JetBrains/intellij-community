// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleDaemonParametersFactory")

package org.jetbrains.plugins.gradle.internal.daemon

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import org.gradle.api.internal.file.DefaultFileCollectionFactory
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.IdentityFileResolver
import org.gradle.api.internal.file.collections.DefaultDirectoryFileTreeFactory
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.gradle.api.internal.provider.PropertyHost
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.tasks.util.PatternSet
import org.gradle.api.tasks.util.internal.DefaultPatternSetFactory
import org.gradle.api.tasks.util.internal.PatternSets
import org.gradle.api.tasks.util.internal.PatternSpecFactory
import org.gradle.initialization.BuildLayoutParameters
import org.gradle.internal.Factory
import org.gradle.internal.file.PathToFileResolver
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.launcher.configuration.BuildLayoutResult
import org.gradle.launcher.daemon.configuration.DaemonParameters
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import java.io.File

// Constructors have changed for different versions of Gradle, need to use the correct version by reflection
@ApiStatus.Internal
fun getDaemonParameters(layout: BuildLayoutParameters): DaemonParameters {
  try {
    return when {
      GradleVersionUtil.isCurrentGradleAtLeast("8.14") -> daemonParameters8Dot14(layout)
      GradleVersionUtil.isCurrentGradleAtLeast("8.12") -> daemonParameters8Dot12(layout)
      GradleVersionUtil.isCurrentGradleAtLeast("6.6") -> daemonParameters6Dot6(layout)
      GradleVersionUtil.isCurrentGradleAtLeast("6.4") -> daemonParameters6Dot4(layout)
      GradleVersionUtil.isCurrentGradleAtLeast("6.3") -> daemonParameters6Dot3(layout)
      GradleVersionUtil.isCurrentGradleAtLeast("6.0") -> daemonParameters6Dot0(layout)
      GradleVersionUtil.isCurrentGradleAtLeast("5.3") -> daemonParameters5Dot3(layout)
      else -> daemonParametersPre5Dot3(layout)
    }
  }
  catch (e: ReflectiveOperationException) {
    throw RuntimeException("Cannot create DaemonParameters by reflection, gradle version " + GradleVersion.current(), e)
  }
}

private fun daemonParameters8Dot14(layout: BuildLayoutParameters): DaemonParameters {
  val daemonParametersConstructor = DaemonParameters::class.java.getConstructor(
    File::class.java,
    FileCollectionFactory::class.java
  )
  val collectionFactory = DefaultFileCollectionFactory(
    IdentityFileResolver(),
    DefaultTaskDependencyFactory.withNoAssociatedProject(),
    DefaultDirectoryFileTreeFactory(),
    DefaultPatternSetFactory(PatternSpecFactory.INSTANCE),
    PropertyHost.NO_OP,
    null
  )
  return daemonParametersConstructor.newInstance(layout.gradleUserHomeDir, collectionFactory)
}


/**
 * DaemonParameters(File (gradleUserHomeDir), FileCollectionFactory) with DefaultFileCollectionFactory using
 * DefaultFileCollectionFactory(
 *  PathToFileResolver,
 *  TaskDependencyFactory,
 *  DirectoryFileTreeFactory,
 *  Factory<PatternSet>,
 *  PropertyHost,
 *  FileSystem)
 * using IdentityFileResolver()
 */
private fun daemonParameters8Dot12(layout: BuildLayoutParameters): DaemonParameters {
  val patternSetFactory = getPatternSetFactoryPre8dot14()
  val identityFileResolver = IdentityFileResolver::class.java.getConstructor().newInstance()
  val collectionFactory = createCollectionFactory6Dot3(identityFileResolver, patternSetFactory)

  val daemonParametersConstructor = DaemonParameters::class.java.getConstructor(
    File::class.java,
    FileCollectionFactory::class.java
  )
  return daemonParametersConstructor.newInstance(layout.gradleUserHomeDir, collectionFactory)
}

/**
 * DaemonParameters(BuildLayoutResult, FileCollectionFactory) with DefaultFileCollectionFactory using
 * DefaultFileCollectionFactory(
 *  PathToFileResolver,
 *  TaskDependencyFactory,
 *  DirectoryFileTreeFactory,
 *  Factory<PatternSet>,
 *  PropertyHost,
 *  FileSystem)
 * using IdentityFileResolver()
 */
private fun daemonParameters6Dot6(layout: BuildLayoutParameters): DaemonParameters {
  val classLoader = DaemonAction::class.java.classLoader
  // Using reflection for code: "new BuildLayoutConverter$Result(BuildLayoutParameters);"
  val resultClass = classLoader.loadClass("org.gradle.launcher.cli.converter.BuildLayoutConverter\$Result")
  val resultConstructor = resultClass.getConstructor(layout.javaClass)
  resultConstructor.isAccessible = true
  val buildLayoutResult = resultConstructor.newInstance(layout) as BuildLayoutResult
  resultConstructor.isAccessible = false

  val patternSetFactory = getPatternSetFactoryPre8dot14()
  val identityFileResolver = IdentityFileResolver::class.java.getConstructor().newInstance()
  val collectionFactory = createCollectionFactory6Dot3(identityFileResolver, patternSetFactory)

  val daemonParametersConstructor = DaemonParameters::class.java.getConstructor(
    BuildLayoutResult::class.java,
    FileCollectionFactory::class.java
  )
  return daemonParametersConstructor.newInstance(buildLayoutResult, collectionFactory)
}

/**
 * DaemonParameters(BuildLayoutParameters, FileCollectionFactory) with DefaultFileCollectionFactory using
 * DefaultFileCollectionFactory(
 *  PathToFileResolver,
 *  TaskDependencyFactory,
 *  DirectoryFileTreeFactory,
 *  Factory<PatternSet>,
 *  PropertyHost,
 *  FileSystem)
 * using IdentityFileResolver()
 */
private fun daemonParameters6Dot4(layout: BuildLayoutParameters): DaemonParameters {
  val patternSetFactory = getPatternSetFactoryPre8dot14()
  val identityFileResolver = IdentityFileResolver::class.java.getConstructor().newInstance()
  val collectionFactory = createCollectionFactory6Dot3(identityFileResolver, patternSetFactory)

  val daemonParametersConstructor = DaemonParameters::class.java.getConstructor(
    BuildLayoutParameters::class.java,
    FileCollectionFactory::class.java
  )
  return daemonParametersConstructor.newInstance(layout, collectionFactory)
}

/**
 * DaemonParameters(BuildLayoutParameters, FileCollectionFactory) with DefaultFileCollectionFactory using
 * DefaultFileCollectionFactory(
 *  PathToFileResolver,
 *  TaskDependencyFactory,
 *  DirectoryFileTreeFactory,
 *  Factory<PatternSet>,
 *  PropertyHost,
 *  FileSystem)
 */
private fun daemonParameters6Dot3(layout: BuildLayoutParameters): DaemonParameters {
  val patternSetFactory = getPatternSetFactoryPre8dot14()
  val identityFileResolver = IdentityFileResolver::class.java.getConstructor(Factory::class.java).newInstance(patternSetFactory)
  val collectionFactory = createCollectionFactory6Dot3(identityFileResolver, patternSetFactory)

  val daemonParametersConstructor = DaemonParameters::class.java.getConstructor(
    BuildLayoutParameters::class.java,
    FileCollectionFactory::class.java
  )
  return daemonParametersConstructor.newInstance(layout, collectionFactory)
}

@Throws(ReflectiveOperationException::class)
private fun createCollectionFactory6Dot3(
  fileResolver: IdentityFileResolver,
  patternFactory: Factory<PatternSet>,
): DefaultFileCollectionFactory {
  val classLoader = DaemonAction::class.java.classLoader
  val propertyHostClass = classLoader.loadClass("org.gradle.api.internal.provider.PropertyHost")
  val propertyHostNoOp = propertyHostClass.getField("NO_OP")[null]
  val collectionFactoryConstructor = DefaultFileCollectionFactory::class.java.getConstructor(
    PathToFileResolver::class.java,
    TaskDependencyFactory::class.java,
    DirectoryFileTreeFactory::class.java,
    Factory::class.java,
    propertyHostClass,
    FileSystem::class.java
  )
  return collectionFactoryConstructor.newInstance(
    fileResolver,
    DefaultTaskDependencyFactory.withNoAssociatedProject(),
    DefaultDirectoryFileTreeFactory(),
    patternFactory,
    propertyHostNoOp,
    null
  )
}

/**
 * DaemonParameters(BuildLayoutParameters, FileCollectionFactory) with DefaultFileCollectionFactory using
 * DefaultFileCollectionFactory(
 *  PathToFileResolver,
 *  TaskDependencyFactory,
 *  DirectoryFileTreeFactory,
 *  Factory<PatternSet>)
 */
private fun daemonParameters6Dot0(layout: BuildLayoutParameters): DaemonParameters {
  val patternSetFactory = getPatternSetFactoryPre8dot14()
  val identityFileResolver = IdentityFileResolver::class.java.getConstructor(Factory::class.java).newInstance(patternSetFactory)
  val collectionFactoryConstructor = DefaultFileCollectionFactory::class.java.getConstructor(
    PathToFileResolver::class.java,
    TaskDependencyFactory::class.java,
    DirectoryFileTreeFactory::class.java,
    Factory::class.java
  )
  val factory = collectionFactoryConstructor.newInstance(
    identityFileResolver,
    DefaultTaskDependencyFactory.withNoAssociatedProject(),
    DefaultDirectoryFileTreeFactory(),
    patternSetFactory
  )

  val daemonParametersConstructor = DaemonParameters::class.java.getConstructor(
    BuildLayoutParameters::class.java,
    FileCollectionFactory::class.java
  )
  return daemonParametersConstructor.newInstance(layout, factory)
}

/**
 * DaemonParameters(BuildLayoutParameters, FileCollectionFactory) with DefaultFileCollectionFactory constructor with no parameters
 */
private fun daemonParameters5Dot3(layout: BuildLayoutParameters): DaemonParameters {
  val daemonParametersConstructor = DaemonParameters::class.java.getConstructor(
    BuildLayoutParameters::class.java,
    FileCollectionFactory::class.java
  )
  return daemonParametersConstructor.newInstance(
    layout,
    DefaultFileCollectionFactory::class.java.getConstructor().newInstance()
  )
}

/**
 * DaemonParameters(BuildLayoutParameters)
 */
private fun daemonParametersPre5Dot3(layout: BuildLayoutParameters): DaemonParameters {
  return DaemonParameters::class.java
    .getConstructor(BuildLayoutParameters::class.java)
    .newInstance(layout)
}

private fun getPatternSetFactoryPre8dot14(): Factory<PatternSet> {
  val handle = PatternSets::class.java.getMethod("getPatternSetFactory", PatternSpecFactory::class.java)
  return handle.invoke(PatternSets::class, PatternSpecFactory.INSTANCE) as Factory<PatternSet>
}
