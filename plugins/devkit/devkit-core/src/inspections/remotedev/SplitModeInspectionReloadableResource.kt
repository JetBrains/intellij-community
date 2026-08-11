// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.remotedev

internal abstract class SplitModeInspectionReloadableResource<T : Any>(
  private val resourceReader: SplitModeInspectionResourceReader,
  private val resourcePath: String,
  private val readMode: SplitModeInspectionResourceReadMode,
) {
  @Volatile
  private var state: State<T> = State.NotLoaded

  fun isLoaded(): Boolean {
    return state !is State.NotLoaded
  }

  @Synchronized
  fun getValue(): T {
    return when (readMode) {
      SplitModeInspectionResourceReadMode.BUNDLED_ONLY -> getBundledValue()
      SplitModeInspectionResourceReadMode.PROJECT_ONLY,
      SplitModeInspectionResourceReadMode.PREFER_PROJECT_WITH_BUNDLED_FALLBACK,
        -> getTrackedValue()
    }
  }

  @Synchronized
  fun invalidate() {
    state = State.NotLoaded
  }

  protected abstract fun parse(text: String): T

  protected abstract fun getDefaultValue(): T

  private fun getBundledValue(): T {
    val cachedState = state
    if (cachedState is State.Bundled) {
      return cachedState.value
    }

    val value = loadValue()
    state = State.Bundled(value)
    return value
  }

  private fun getTrackedValue(): T {
    val currentStamp = getProjectResourceStamp()
    val cachedState = state
    if (cachedState is State.Tracked && cachedState.stamp == currentStamp) {
      return cachedState.value
    }

    val value = loadValue()
    state = State.Tracked(value, currentStamp)
    return value
  }

  private fun getProjectResourceStamp(): ProjectResourceStamp {
    val projectFile = resourceReader.findProjectResourceFile(resourcePath)
    if (projectFile == null) {
      return ProjectResourceStamp(projectFileExists = false, modificationStamp = 0)
    }

    return ProjectResourceStamp(projectFileExists = true, modificationStamp = projectFile.modificationStamp)
  }

  private fun loadValue(): T {
    val text = resourceReader.readText(resourcePath, readMode)
    return if (text == null) getDefaultValue() else parse(text)
  }

  private sealed interface State<out T : Any> {
    data object NotLoaded : State<Nothing>

    data class Bundled<T : Any>(
      val value: T,
    ) : State<T>

    data class Tracked<T : Any>(
      val value: T,
      val stamp: ProjectResourceStamp,
    ) : State<T>
  }

  private data class ProjectResourceStamp(
    val projectFileExists: Boolean,
    val modificationStamp: Long,
  )
}
