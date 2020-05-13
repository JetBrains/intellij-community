package com.intellij.workspace.api

import java.io.File

/**
 * This file contains drafts of elements a new Java project model.
 */

/** Represents a set of source files written in JVM-based languages which have the same dependencies */
interface JvmSourceSet {
  val mode: Mode
  val sourceRoots: List<JvmSourceRoot>
  val outputRoots: List<FilePointer>
  val dependencies: List<JvmDependency>
  
  enum class Mode {
    /** The actual code is available as sources, they are compiled to output roots during build (corresponds to Module in old model) */
    SOURCE,
    /** The actual code is available in compiled form under [outputRoots], [sourceRoots] (corresponds to Library/SDK in the old model) */
    COMPILED
  }
}

interface JvmSourceRoot {
  val type: Type
  val file: FilePointer
  val generated: Boolean
  /** Corresponds to package prefix for source roots */
  val relativeOutputPath: String?

  enum class Type {
    SOURCE, RESOURCES
  }
}

interface FilePointer {
  val url: String
  val file: File
}

interface Reference<T> {
  /** Not whether we need to store unresolvable references in the model, maybe there is a better way to store incorrect elements */
  val resolved: T?
  /** This name is used to show error message if the reference cannot be resolved */
  val displayName: String
}

interface JvmDependency {
  val target: Reference<JvmSourceSet>

  /** Not sure about this part */
  val includedAtRuntime: Boolean
  val includedForCompilation: Boolean
  val exported: Boolean
}

/**
 * Represents a source set which contains tests. May be contain reference to a source set which contains production code which is tested here.
 */
interface JvmTestSourceSet : JvmSourceSet {
  val productionSourceSet: Reference<JvmSourceSet>?
}

/**
 * Qualified name (including the project name) of a source set imported from Gradle.
 */
val JvmSourceSet.gradleQualifiedName: String?
  get() = TODO()

/**
 * Name of a manually created module provided by user.
 */
val JvmSourceSet.moduleName: String?
  get() = TODO()

/**
 * Returns maven coordinates if the source set is actually a library imported from a Maven repository
 */
val JvmSourceSet.mavenCoordinates: String?
  get() = TODO()

val JvmSourceSet.annotationRoot: List<FilePointer>
  get() = TODO()

val JvmSourceSet.javadocRoot: List<FilePointer>
  get() = TODO()

