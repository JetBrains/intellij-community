// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal
@file:JvmName("InternalLocations")

package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.problem.location

import org.gradle.tooling.events.problems.FileLocation
import org.gradle.tooling.events.problems.LineInFileLocation
import org.gradle.tooling.events.problems.Location
import org.gradle.tooling.events.problems.OffsetInFileLocation
import org.gradle.tooling.events.problems.PluginIdLocation
import org.gradle.tooling.events.problems.TaskPathLocation
import org.jetbrains.annotations.ApiStatus
import java.io.Serializable

fun locationOf(location: Location): Location? = when (location) {
  is LineInFileLocation -> InternalLineInFileLocation(location)
  is OffsetInFileLocation -> InternalOffsetInFileLocation(location)
  is FileLocation -> InternalFileLocation(location)
  is PluginIdLocation -> InternalPluginIdLocation(location)
  is TaskPathLocation -> InternalTaskPathLocation(location)
  else -> null
}

class InternalPluginIdLocation(location: PluginIdLocation) : PluginIdLocation, Serializable {
  private val pluginId: String = location.pluginId

  override fun getPluginId(): String = pluginId
}

class InternalTaskPathLocation(location: TaskPathLocation) : TaskPathLocation, Serializable {
  private val buildTreePath: String = location.buildTreePath

  override fun getBuildTreePath(): String = buildTreePath
}

open class InternalFileLocation(location: FileLocation) : Serializable, FileLocation {
  private val path: String = location.path

  override fun getPath(): String = path
}

class InternalLineInFileLocation(location: LineInFileLocation) : InternalFileLocation(location), LineInFileLocation {
  private val line: Int = location.line
  private val column: Int = location.column
  private val length: Int = location.length

  override fun getLine(): Int = line

  override fun getColumn(): Int = column

  override fun getLength(): Int = length
}

class InternalOffsetInFileLocation(location: OffsetInFileLocation) : InternalFileLocation(location), OffsetInFileLocation {
  private val offset: Int = location.offset
  private val length: Int = location.length

  override fun getOffset(): Int = offset

  override fun getLength(): Int = length
}
