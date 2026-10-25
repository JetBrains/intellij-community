// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.model

import kotlinx.coroutines.flow.StateFlow

/*
 * Contract between the inspector model (picker, descriptor walk, render capture, debugger bridge) and the Jewel UI.
 * The UI only depends on this file; the model implements [IconInspectorController].
 */

/** What a tree node represents. Drives the node's badge/color in the UI. */
internal enum class InspectorNodeKind {
  /** The picked Swing icon (e.g. `SwingIcon`, or a legacy icon). */
  SWING_ICON,
  /** An icon renderer (`DefaultIconRenderer`, deferred renderer) and its descriptor. */
  ICON,
  /** A layer renderer (image, nested, layout, shape, animated, spacer, swing layer). */
  LAYER,
  /** An icon modifier (scale, margin, alpha, color filter, …), flattened from combined modifiers. */
  MODIFIER,
  /** An image resource location, described by its `ImageResourceLocationDescriber`. */
  RESOURCE,
  /** Anything else (e.g. a legacy Swing icon wrapped by a swing layer). */
  OTHER,
}

/** Which icon system a node belongs to. The UI renders the three visibly differently. */
internal enum class InspectorNodeOrigin {
  /** New icons API: descriptors, layers, modifiers, resource locations, their renderers. */
  ICONS_API,
  /** Legacy Swing / IntelliJ icons (`CachedImageIcon`, `LayeredIcon`, `RowIcon`, `EmptyIcon`, …). */
  LEGACY,
  /** Where one system wraps the other: `SwingIcon` (descriptor exposed as Swing), `SwingIconLayer` (Swing inside a descriptor). */
  BRIDGE,
}

internal data class InspectorProperty(
  val name: String,
  val value: String?,
  /** Optional group shown as a section header in the properties table, e.g. "Layout", "Scaling". */
  val group: String? = null,
)

internal data class InspectorNode(
  /** Stable within one inspection; used as the tree key and for selection. */
  val id: String,
  val kind: InspectorNodeKind,
  /** Primary label, e.g. "ImageIconLayer", "ScaleModifier". */
  val title: String,
  /** Secondary label, e.g. "expui/general/search.svg", "factor=1.25", "16×16 @2.0". */
  val subtitle: String? = null,
  val properties: List<InspectorProperty> = emptyList(),
  val children: List<InspectorNode> = emptyList(),
  /** True when this node is backed by a concrete renderer instance that can be flagged for debugging. */
  val debuggable: Boolean = false,
  /** Non-null when something looks wrong (e.g. "11 nested identical wrappers"). */
  val warning: String? = null,
  val origin: InspectorNodeOrigin = InspectorNodeOrigin.ICONS_API,
  /**
   * How many times the renderer instance at this tree position was replaced by a new instance while inspecting
   * (0 = stable, or not backed by a renderer). Lets the user spot icons that get recreated on every update.
   */
  val recreations: Int = 0,
  /** Source / device-pixel / painted sizes; null when the node has no notion of size (e.g. a modifier). */
  val sizes: IconSizes? = null,
  /** Editable modifiers of a new-API LAYER node, in application order; empty for other nodes. */
  val modifiers: List<EditableModifier> = emptyList(),
)

/** How a size value was obtained. The UI marks anything but [MEASURED] so estimates never pass for measurements. */
internal enum class SizeProvenance {
  /** Recorded from an actual paint. */
  MEASURED,
  /** Computed from the icon/descriptor and the known scales (e.g. logical size × screen scale). */
  COMPUTED,
  /** Not available for this node (e.g. not painted since the pick, or no notion of size). */
  UNAVAILABLE,
}

internal data class SizeValue(
  val width: Int,
  val height: Int,
  val provenance: SizeProvenance,
  /** Short explanation shown in a tooltip, e.g. "logical 16×16 × screen scale 2.0", "HeaderToolbarButtonLook → 20px". */
  val note: String? = null,
)

/**
 * The three sizes shown for every node, all in pixels:
 * - [source]: the icon's own unscaled size (SVG/raster size, descriptor at density 1, legacy logical size without OBJ scale);
 * - [devicePixels]: source × the screen / context scale where it is painted;
 * - [painted]: what actually reached the screen, including looks and wrappers that resize it (e.g. the main toolbar's 20px).
 * A null entry means "not applicable"; use [SizeProvenance.UNAVAILABLE] for "applicable but not known".
 */
internal data class IconSizes(
  val source: SizeValue?,
  val devicePixels: SizeValue?,
  val painted: SizeValue?,
)

/** Modifier types the inspector knows how to edit (new icons API). */
internal enum class EditableModifierKind {
  SCALE, MARGIN, ALPHA, ALIGN, COLOR_FILTER, STROKE, CUTOUT_MARGIN, SIZE_VARIANTS,
  /** Shown but not editable (e.g. SVG patcher, unknown modifier classes). */
  READ_ONLY,
}

internal enum class ModifierFieldType {
  /** Plain number, e.g. scale factor 1.25 or alpha 0.5. */
  NUMBER,
  /** Icon unit with suffix, e.g. "2dp" or "3px". */
  UNIT,
  /** Color as "#AARRGGBB" (or "#RRGGBB"). */
  COLOR,
  /** One of [ModifierField.options]. */
  CHOICE,
  TEXT,
}

internal data class ModifierField(
  val key: String,
  val label: String,
  val type: ModifierFieldType,
  /** Current value in the textual form of [type]. */
  val value: String,
  val options: List<String> = emptyList(),
  val editable: Boolean = true,
)

internal data class EditableModifier(
  /** Stable within its layer node for the current target, e.g. "m0". */
  val id: String,
  val kind: EditableModifierKind,
  /** e.g. "ScaleModifier". */
  val title: String,
  val fields: List<ModifierField>,
)

/** An edit of the modifiers of one new-API layer node; applied to an edited copy of the descriptor. */
internal sealed interface ModifierEdit {
  val layerNodeId: String
  data class SetField(override val layerNodeId: String, val modifierId: String, val key: String, val value: String) : ModifierEdit
  data class Add(override val layerNodeId: String, val kind: EditableModifierKind) : ModifierEdit
  data class Remove(override val layerNodeId: String, val modifierId: String) : ModifierEdit
  data class Move(override val layerNodeId: String, val modifierId: String, val toIndex: Int) : ModifierEdit
}

internal data class ModifierEditingState(
  /** Number of edits applied to the copy since the pick (0 = showing the original). */
  val editCount: Int,
  /** Whether the edited icon is currently installed in the picked component. */
  val appliedToComponent: Boolean,
  /** Whether the picked component supports installing an edited icon at all. */
  val canApplyToComponent: Boolean,
  /** Non-null when something needs attention, e.g. "the component replaced the edited icon on update". */
  val message: String? = null,
)

/** Which icon pipeline a [ResolvedSvgSource] describes. */
internal enum class IconPipeline { NEW_ICONS_API, LEGACY }

/**
 * The file one pipeline actually rasterizes for the picked icon at its painted size, after every substitution
 * (theme path mapping, `@WxH` size variant, `_dark` / stroke variant), with the SVG text and a rendering of it.
 */
internal data class ResolvedSvgSource(
  val pipeline: IconPipeline,
  /** The path the icon names, e.g. `expui/general/search.svg`. */
  val basePath: String?,
  /** Substitutions in order, e.g. "size variant @20x20 → expui/general/search@20x20.svg", "dark → …_dark.svg". */
  val steps: List<String>,
  /** The file that gets rasterized; null if it could not be determined. */
  val finalPath: String?,
  /** How [finalPath] was determined, e.g. "reproduced HeaderToolbarButtonLook decision" or "pixel match 100% vs search.svg". */
  val resolution: String,
  /** Contents of [finalPath] if it is an SVG and readable, else null. */
  val svgText: String?,
  /** SVG patches applied when rasterizing (color patcher, stroke), as readable lines; empty if none known. */
  val patches: List<String> = emptyList(),
  /** The pipeline's actual rendering at the painted size (device pixels), for side-by-side and overlay comparison. */
  val rendering: PreviewImage?,
  /** Visible content bounds of [rendering] (alpha > 0), "W×H at (x,y)"; null if empty/unknown. */
  val contentBounds: String? = null,
)

/** Renderer-instance stability of the inspected icon, measured over the captured repaints of the picked component. */
internal data class RendererStability(
  /** Captured paints observed since the pick (or the last reset). */
  val paints: Int,
  /** Paints whose top-level renderer instance differed from the previous paint's. */
  val recreations: Int,
  /** Distinct top-level renderer instances seen. */
  val distinctInstances: Int,
  /** Milliseconds since the top-level renderer was last replaced, or null if it never was. */
  val lastRecreationAgoMs: Long?,
  /** Milliseconds observed. */
  val observedMs: Long,
)

internal data class RenderingSnapshot(
  /** Ordered rows: density, context scale, scale factor, final dimensions, theme digest, image modifiers, … */
  val properties: List<InspectorProperty>,
  /** Last live paint, if captured: component, x/y, density, size. */
  val lastPaint: List<InspectorProperty> = emptyList(),
)

internal data class PreviewImage(
  /** Label such as "1×", "1.25×", "2×", "as painted". */
  val label: String,
  val width: Int,
  val height: Int,
  /** ARGB pixels, row-major, `width * height` entries. */
  val argb: IntArray,
)

internal data class InspectionTarget(
  /** e.g. "ActionButton", "JLabel", "Tree cell renderer of ProjectViewTree". */
  val componentDescription: String,
  /** Swing icon class name, e.g. "SwingIcon" or "CachedImageIcon". */
  val iconClass: String,
  /** True when the descriptor was bridged from a legacy Swing icon (not a native SwingIcon). */
  val bridged: Boolean,
  /** Extra summary rows: action id, place, presentation info, … */
  val summary: List<InspectorProperty> = emptyList(),
  val root: InspectorNode,
  val rendering: RenderingSnapshot,
  val previews: List<PreviewImage> = emptyList(),
  /** Null when no captured paints are available (static tree). */
  val stability: RendererStability? = null,
  /** What each pipeline rasterizes for this icon at its painted size (both, regardless of `ide.experimental.icons`). */
  val sources: List<ResolvedSvgSource> = emptyList(),
)

internal data class FlaggedRenderer(
  val nodeId: String,
  val title: String,
  val hitCount: Int,
)

internal sealed interface InspectorState {
  data object Empty : InspectorState
  data object Picking : InspectorState
  data class Inspecting(val target: InspectionTarget) : InspectorState
  data class Error(val message: String) : InspectorState
}

internal interface IconInspectorController {
  val state: StateFlow<InspectorState>
  val flagged: StateFlow<List<FlaggedRenderer>>

  /** Starts on-screen picking (hover highlight, click selects, Esc cancels). */
  fun startPicking()
  fun cancelPicking()

  /** Re-captures the current target (repaints the component and rebuilds the tree/snapshot). */
  fun refresh()
  fun clear()

  /** Flags / unflags the renderer instance behind [nodeId] so the debugger stops on its next render. */
  fun setFlagged(nodeId: String, flagged: Boolean)
  fun isFlagged(nodeId: String): Boolean
  fun clearFlags()

  /** Restarts the stability measurement (counters back to zero) for the current target. */
  fun resetStability() {}

  /** Modifier editing of the current target; null when the target has no new-API descriptor. */
  val modifierEditing: StateFlow<ModifierEditingState?>
    get() = NO_MODIFIER_EDITING

  /** Applies [edit] to the edited copy; returns an error message (e.g. invalid value) or null on success. */
  fun editModifiers(edit: ModifierEdit): String? = "Modifier editing is not available"

  /** Installs (or removes) the edited icon in the picked component. */
  fun setAppliedToComponent(applied: Boolean) {}

  /** Drops all edits and restores the original icon in the component. */
  fun revertModifierEdits() {}

  /** FQN to put an exception breakpoint on, shown in the Debug tab with a copy button. */
  val breakpointExceptionClass: String
}

/** Shared "unsupported" state for controllers without modifier editing. */
private val NO_MODIFIER_EDITING: StateFlow<ModifierEditingState?> = kotlinx.coroutines.flow.MutableStateFlow(null)
