// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Key
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon

/**
 * Key for caching inter-line breakpoint configurations per editor.
 * Configs are calculated asynchronously and stored via [Editor.putUserData].
 */
private val INTER_LINE_BREAKPOINT_CONFIGS_KEY: Key<Map<String, InterLineBreakpointConfiguration>> = Key.create("editor.inter.line.breakpoint.configs")

/**
 * Configuration for inter-line hit detection and rendering.
 *
 * @param icon icon to show in the inter-line area
 * @param hoverTooltip tooltip to show on hover
 * @param breakpointProperties properties for the breakpoint (e.g., logging)
 * @param animator optional animator for line shift effects (null for no animation)
 * @param availableFor predicate to check if this config applies to the given line (for inter-line placement)
 */
@ApiStatus.Internal
class InterLineBreakpointConfiguration(
  val icon: Icon,
  val hoverTooltip: @Nls String,
  val breakpointProperties: InterLineBreakpointProperties,
  val animator: InterLineShiftAnimator? = null,
  val availableFor: (line: Int) -> Boolean = { false },
)

@ApiStatus.Internal
data class InterLineBreakpointProperties(
  val isLogging: Boolean,
) {
  companion object {
    val KEY: DataKey<InterLineBreakpointProperties> = DataKey.create("interLineBreakpointProperties")
  }
}

/**
 * Provides configuration for inter-line hit detection in the gutter.
 *
 * Inter-line hit detection allows distinguishing clicks/hovers between lines from those on line numbers.
 *
 * Configurations are collected once per editor and cached. The [InterLineBreakpointConfiguration.availableFor]
 * predicate is used to determine if a config applies to a specific line during hover/click detection.
 */
@ApiStatus.Internal
interface InterLineBreakpointConfigurationProvider {

  /**
   * Unique identifier for this provider, used as a key when caching configurations per editor.
   *
   * Must be stable and unique across all providers to avoid collisions in the configuration map.
   */
  val uniqueId: String

  /**
   * Returns a flow of configurations for the given [editor].
   *
   * The flow should emit:
   * - A non-null [InterLineBreakpointConfiguration] when inter-line breakpoints are available
   * - `null` when inter-line breakpoints should be disabled
   *
   * The flow is collected for the lifetime of the editor.
   * Each emission updates the cached configuration used by [findConfigurationForLine].
   *
   * @param editor the editor to provide configuration for
   * @return a flow that emits configuration updates
   */
  fun getConfiguration(editor: Editor): Flow<InterLineBreakpointConfiguration?>

  companion object {
    private val EP: ExtensionPointName<InterLineBreakpointConfigurationProvider> =
      ExtensionPointName.create("com.intellij.editor.interLineBreakpointConfigurationProvider")

    /**
     * Finds the first configuration that applies to the given [line] in the given [editor].
     *
     * @param editor the editor to check
     * @param line the logical line to check
     * @return the first config where [InterLineBreakpointConfiguration.availableFor] returns true, or null
     */
    @JvmStatic
    fun findConfigurationForLine(editor: Editor, line: Int): InterLineBreakpointConfiguration? {
      return findFirstConfiguration(editor) { it.availableFor(line) }
    }

    private fun findFirstConfiguration(editor: Editor, predicate: (InterLineBreakpointConfiguration) -> Boolean): InterLineBreakpointConfiguration? {
      val project = editor.project ?: return null

      if (editor.getUserData(INTER_LINE_BREAKPOINT_CONFIGS_KEY) == null) {
        val configs = ConcurrentHashMap<String, InterLineBreakpointConfiguration>()
        editor.putUserData(INTER_LINE_BREAKPOINT_CONFIGS_KEY, configs)
        val editorScope = EditorScopeProvider.getInstance(project).getEditorScope(editor)
        editorScope.launch(Dispatchers.Default) {
          for (configurationProvider in EP.extensionList) {
            launch {
              configurationProvider.getConfiguration(editor).collectLatest {
                val id = configurationProvider.uniqueId
                if (it == null) {
                  configs.remove(id)
                }
                else {
                  configs[id] = it
                }
              }
            }
          }
        }
      }

      return editor.getUserData(INTER_LINE_BREAKPOINT_CONFIGS_KEY)?.values?.find { predicate(it) }
    }
  }
}

/**
 * Result of mapping a Y coordinate to a logical line with inter-line detection.
 *
 * Used by [EditorUtil.yToLogicalLineWithInterLineDetection] to distinguish between
 * clicks/hovers on a line vs between lines (for features like interline breakpoints).
 */
@ApiStatus.Internal
sealed class BreakpointArea(open val line: Int) {

  abstract val isBetweenLines: Boolean

  @ApiStatus.Internal
  data class OnLine(override val line: Int) : BreakpointArea(line) {
    override val isBetweenLines: Boolean get() = false
  }

  @ApiStatus.Internal
  data class InterLine(
    override val line: Int,
    val configuration: InterLineBreakpointConfiguration,
  ) : BreakpointArea(line) {
    override val isBetweenLines: Boolean get() = true
  }

  companion object {
    @JvmField
    val INVALID: BreakpointArea = OnLine(-1)
  }
}
