// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.ui

import com.intellij.devkit.icons.inspector.model.FlaggedRenderer
import com.intellij.devkit.icons.inspector.model.IconInspectorController
import com.intellij.devkit.icons.inspector.model.EditableModifier
import com.intellij.devkit.icons.inspector.model.EditableModifierKind
import com.intellij.devkit.icons.inspector.model.IconPipeline
import com.intellij.devkit.icons.inspector.model.ModifierEdit
import com.intellij.devkit.icons.inspector.model.ModifierEditingState
import com.intellij.devkit.icons.inspector.model.ModifierField
import com.intellij.devkit.icons.inspector.model.ModifierFieldType
import com.intellij.devkit.icons.inspector.model.IconSizes
import com.intellij.devkit.icons.inspector.model.ResolvedSvgSource
import com.intellij.devkit.icons.inspector.model.InspectionTarget
import com.intellij.devkit.icons.inspector.model.SizeProvenance
import com.intellij.devkit.icons.inspector.model.SizeValue
import com.intellij.devkit.icons.inspector.model.InspectorNode
import com.intellij.devkit.icons.inspector.model.InspectorNodeKind
import com.intellij.devkit.icons.inspector.model.InspectorNodeOrigin
import com.intellij.devkit.icons.inspector.model.InspectorProperty
import com.intellij.devkit.icons.inspector.model.InspectorState
import com.intellij.devkit.icons.inspector.model.PreviewImage
import com.intellij.devkit.icons.inspector.model.RenderingSnapshot
import com.intellij.devkit.icons.inspector.model.RendererStability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * DEV/SAMPLE ONLY: an in-memory [IconInspectorController] with a canned [InspectionTarget], used to demo the UI
 * without the real picker/model. Not wired anywhere in production code.
 */
internal class FakeIconInspectorController : IconInspectorController {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val sample = buildSampleTarget()
  private val flaggedIds = LinkedHashMap<String, Int>()
  private var paintCount = 1
  private var churning = true
  private var mods: List<EditableModifier> = initialModifiers()
  private var editCount = 0
  private var applied = false
  private var nextModId = 100
  init {
    // Simulate the real controller: republish equal-but-fresh objects several times per second.
    scope.launch {
      while (true) {
        delay(250)
        paintCount++
        _state.update { cur ->
          if (cur is InspectorState.Inspecting) {
            val rendering = cur.target.rendering
            val lastPaint = rendering.lastPaint.map { if (it.name == "Paint count") it.copy(value = paintCount.toString()) else it }
            val fresh = mods.map { m -> m.copy(fields = m.fields.map { it.copy() }) }
            InspectorState.Inspecting(applyStability(cur.target.copy(rendering = rendering.copy(lastPaint = lastPaint)), churning, fresh))
          }
          else cur
        }
      }
    }
  }

  private val _editing = MutableStateFlow<ModifierEditingState?>(ModifierEditingState(0, false, true))

  private val _state = MutableStateFlow<InspectorState>(InspectorState.Inspecting(sample))
  private val _flagged = MutableStateFlow<List<FlaggedRenderer>>(emptyList())

  override val state: StateFlow<InspectorState> get() = _state
  override val flagged: StateFlow<List<FlaggedRenderer>> get() = _flagged

  override val modifierEditing: StateFlow<ModifierEditingState?> get() = _editing

  private fun restate() {
    _editing.value = ModifierEditingState(
      editCount, applied, true,
      if (applied && editCount > 0) "Sample: the component may replace the edited icon on its next update" else null,
    )
    _state.update { cur -> if (cur is InspectorState.Inspecting) InspectorState.Inspecting(applyStability(cur.target, churning, mods)) else cur }
  }

  override fun editModifiers(edit: ModifierEdit): String? {
    if (edit.layerNodeId != IMAGE_LAYER_ID) return "Only the sample image layer is editable in the fake controller"
    val list = mods.toMutableList()
    when (edit) {
      is ModifierEdit.SetField -> {
        val i = list.indexOfFirst { it.id == edit.modifierId }
        if (i < 0) return "No such modifier"
        val m = list[i]
        val f = m.fields.firstOrNull { it.key == edit.key } ?: return "No such field"
        if (f.type == ModifierFieldType.NUMBER) {
          val v = edit.value.toDoubleOrNull() ?: return "Not a number: ${edit.value}"
          if (m.kind == EditableModifierKind.ALPHA && v !in 0.0..1.0) return "Alpha must be within 0..1"
          if (m.kind == EditableModifierKind.SCALE && v <= 0.0) return "Scale must be > 0"
        }
        list[i] = m.copy(fields = m.fields.map { if (it.key == edit.key) it.copy(value = edit.value) else it })
      }
      is ModifierEdit.Add -> list.add(newModifier(edit.kind, "m${nextModId++}"))
      is ModifierEdit.Remove -> if (!list.removeAll { it.id == edit.modifierId }) return "No such modifier"
      is ModifierEdit.Move -> {
        val i = list.indexOfFirst { it.id == edit.modifierId }
        if (i < 0 || edit.toIndex !in list.indices) return "Cannot move"
        list.add(edit.toIndex, list.removeAt(i))
      }
    }
    mods = list
    editCount++
    restate()
    return null
  }

  override fun setAppliedToComponent(applied: Boolean) {
    this.applied = applied
    restate()
  }

  override fun revertModifierEdits() {
    mods = initialModifiers()
    editCount = 0
    applied = false
    restate()
  }

  override val breakpointExceptionClass: String = "com.intellij.platform.icons.impl.rendering.debug.IconRenderBreakpoint"

  override fun startPicking() {
    _state.value = InspectorState.Picking
    scope.launch {
      delay(1500)
      if (_state.value == InspectorState.Picking) _state.value = InspectorState.Inspecting(sample)
    }
  }

  override fun cancelPicking() {
    if (_state.value == InspectorState.Picking) _state.value = InspectorState.Empty
  }

  override fun refresh() {
    paintCount++
    for (id in flaggedIds.keys.toList()) flaggedIds[id] = (flaggedIds[id] ?: 0) + 1
    publishFlagged()
    _state.update { current ->
      if (current is InspectorState.Inspecting) {
        val rendering = current.target.rendering
        val lastPaint = rendering.lastPaint.map { if (it.name == "Paint count") it.copy(value = paintCount.toString()) else it }
        churning = !churning
        InspectorState.Inspecting(applyStability(current.target.copy(rendering = rendering.copy(lastPaint = lastPaint)), churning, mods))
      }
      else current
    }
  }

  override fun resetStability() {
    churning = false
    _state.update { current ->
      if (current is InspectorState.Inspecting) {
        InspectorState.Inspecting(applyStability(current.target, false, mods).let {
          it.copy(stability = RendererStability(0, 0, 1, null, 0))
        })
      }
      else current
    }
  }

  override fun clear() {
    _state.value = InspectorState.Empty
  }

  override fun setFlagged(nodeId: String, flagged: Boolean) {
    if (flagged) flaggedIds.putIfAbsent(nodeId, (1..5).random()) else flaggedIds.remove(nodeId)
    publishFlagged()
  }

  override fun isFlagged(nodeId: String): Boolean = flaggedIds.containsKey(nodeId)

  override fun clearFlags() {
    flaggedIds.clear()
    publishFlagged()
  }

  fun dispose() {
    scope.cancel()
  }

  private fun publishFlagged() {
    _flagged.value = flaggedIds.map { (id, hits) -> FlaggedRenderer(id, findNode(sample.root, id)?.title ?: id, hits) }
  }
}

private fun sz(w: Int, h: Int = w, p: SizeProvenance = SizeProvenance.MEASURED, note: String? = null) = SizeValue(w, h, p, note)
private val unpainted = SizeValue(0, 0, SizeProvenance.UNAVAILABLE, "not painted since pick")

private fun sampleSizes(id: String): IconSizes? = when (id) {
  "0" -> IconSizes(sz(16), sz(32, p = SizeProvenance.COMPUTED, note = "16×16 × screen 2.0"), sz(40, note = "HeaderToolbarButtonLook → 20px"))
  "0.0", "0.0.0", "0.0.0.0" -> IconSizes(sz(16), sz(32, p = SizeProvenance.COMPUTED, note = "16×16 × screen 2.0"), sz(40, note = "HeaderToolbarButtonLook → 20px"))
  "0.0.0.0.0" -> IconSizes(sz(16), sz(40, p = SizeProvenance.COMPUTED, note = "16×16 × scale 1.25 × screen 2.0"), sz(40))
  "0.0.0.0.0.0" -> IconSizes(sz(16), null, null)
  "0.0.0.0.0.1", "0.0.0.0.0.2" -> null
  "0.0.1", "0.0.1.0" -> IconSizes(sz(16), sz(32, p = SizeProvenance.COMPUTED, note = "16×16 × screen 2.0"), unpainted)
  "0.0.1.0.0" -> IconSizes(sz(16), sz(32, p = SizeProvenance.COMPUTED, note = "16×16 × screen 2.0"), unpainted)
  "0.0.1.0.1" -> IconSizes(sz(32, 16), sz(64, 32, SizeProvenance.COMPUTED, "32×16 × screen 2.0"), unpainted)
  "0.0.1.0.1.0" -> IconSizes(sz(16), sz(32, p = SizeProvenance.COMPUTED), unpainted)
  else -> if (id.startsWith("0.0.1.0.1.1")) IconSizes(sz(16), sz(32, p = SizeProvenance.COMPUTED, note = "16×16 × screen 2.0"), unpainted) else null
}

private fun applyStability(target: InspectionTarget, churning: Boolean, mods: List<EditableModifier> = initialModifiers()): InspectionTarget {
  fun walk(node: InspectorNode): InspectorNode {
    val recreations = if (node.id == "0.0.1") (if (churning) 7 else 0) else node.recreations
    return node.copy(recreations = recreations, sizes = sampleSizes(node.id), modifiers = if (node.id == IMAGE_LAYER_ID) mods else node.modifiers, children = node.children.map(::walk))
  }
  val stability = if (churning) RendererStability(42, 21, 22, 800, 12_000) else RendererStability(42, 0, 1, null, 12_000)
  return target.copy(root = walk(target.root), stability = stability)
}

private fun buildSampleTarget(): InspectionTarget {
  val resource = InspectorNode(
    id = "0.0.0.0.0.0",
    kind = InspectorNodeKind.RESOURCE,
    title = "ModuleImageResourceLocation",
    subtitle = "expui/general/search.svg",
    properties = listOf(
      InspectorProperty("path", "expui/general/search.svg", "Location"),
      InspectorProperty("pluginId", "com.intellij", "Location"),
      InspectorProperty("moduleId", null, "Location"),
    ),
  )
  val scale = InspectorNode(
    id = "0.0.0.0.0.1",
    kind = InspectorNodeKind.MODIFIER,
    title = "ScaleModifier",
    subtitle = "factor=1.25",
    properties = listOf(InspectorProperty("factor", "1.25", "Scale")),
  )
  val alpha = InspectorNode(
    id = "0.0.0.0.0.2",
    kind = InspectorNodeKind.MODIFIER,
    title = "AlphaIconModifier",
    subtitle = "0.5",
    properties = listOf(InspectorProperty("alpha", "0.5")),
  )
  val imageLayer = InspectorNode(
    id = "0.0.0.0.0",
    kind = InspectorNodeKind.LAYER,
    title = "ImageIconLayer",
    subtitle = "expui/general/search.svg",
    properties = listOf(
      InspectorProperty("class", "ImageIconLayer", "Layer"),
      InspectorProperty("modifiers", "2", "Layer"),
    ),
    children = listOf(resource, scale, alpha),
    debuggable = true,
  )
  val innerIcon = InspectorNode(
    id = "0.0.0.0",
    kind = InspectorNodeKind.ICON,
    title = "DefaultLayeredIconDescriptor",
    subtitle = "1 layer",
    properties = listOf(InspectorProperty("layers", "1", "Descriptor")),
    children = listOf(imageLayer),
    debuggable = true,
    warning = "11 nested identical wrappers",
  )
  val nestedLayer = InspectorNode(
    id = "0.0.0",
    kind = InspectorNodeKind.LAYER,
    title = "NestedIconLayer",
    properties = listOf(InspectorProperty("depth", "11", "Layer")),
    children = listOf(innerIcon),
    debuggable = true,
  )
  val legacyBranch = InspectorNode(
    id = "0.0.1",
    kind = InspectorNodeKind.LAYER,
    title = "SwingIconLayer",
    subtitle = "LayeredIcon",
    origin = InspectorNodeOrigin.BRIDGE,
    debuggable = true,
    properties = listOf(InspectorProperty("swingIcon", "com.intellij.ui.LayeredIcon", "Layer")),
    children = listOf(
      InspectorNode(
        id = "0.0.1.0",
        kind = InspectorNodeKind.OTHER,
        title = "LayeredIcon",
        subtitle = "2 layers",
        origin = InspectorNodeOrigin.LEGACY,
        children = listOf(
          InspectorNode(
            id = "0.0.1.0.0",
            kind = InspectorNodeKind.OTHER,
            title = "CachedImageIcon",
            subtitle = "general/search.svg",
            origin = InspectorNodeOrigin.LEGACY,
            properties = listOf(InspectorProperty("originalPath", "general/search.svg")),
          ),
          InspectorNode(
            id = "0.0.1.0.1",
            kind = InspectorNodeKind.OTHER,
            title = "RowIcon",
            origin = InspectorNodeOrigin.LEGACY,
            children = listOf(
              InspectorNode(
                id = "0.0.1.0.1.0",
                kind = InspectorNodeKind.OTHER,
                title = "EmptyIcon",
                subtitle = "16×16",
                origin = InspectorNodeOrigin.LEGACY,
              ),
              InspectorNode(
                id = "0.0.1.0.1.1",
                kind = InspectorNodeKind.SWING_ICON,
                title = "SwingIcon",
                subtitle = "16×16",
                origin = InspectorNodeOrigin.BRIDGE,
                children = listOf(
                  InspectorNode(
                    id = "0.0.1.0.1.1.0",
                    kind = InspectorNodeKind.ICON,
                    title = "DefaultLayeredIconDescriptor",
                    subtitle = "1 layer",
                    debuggable = true,
                    children = listOf(
                      InspectorNode(
                        id = "0.0.1.0.1.1.0.0",
                        kind = InspectorNodeKind.LAYER,
                        title = "ImageIconLayer",
                        subtitle = "expui/nodes/folder.svg",
                        debuggable = true,
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    ),
  )
  val descriptor = InspectorNode(
    id = "0.0",
    kind = InspectorNodeKind.ICON,
    title = "DefaultLayeredIconDescriptor",
    subtitle = "1 layer",
    properties = listOf(
      InspectorProperty("layers", "1", "Descriptor"),
      InspectorProperty("modifier", "RootIconModifier", "Descriptor"),
    ),
    children = listOf(nestedLayer, legacyBranch),
    debuggable = true,
  )
  val root = InspectorNode(
    id = "0",
    kind = InspectorNodeKind.SWING_ICON,
    title = "SwingIcon",
    subtitle = "16×16",
    origin = InspectorNodeOrigin.BRIDGE,
    properties = listOf(
      InspectorProperty("class", "com.intellij.platform.icons.impl.intellij.rendering.SwingIcon", "Icon"),
      InspectorProperty("iconWidth", "16", "Icon"),
      InspectorProperty("iconHeight", "16", "Icon"),
    ),
    children = listOf(descriptor),
  )
  return applyStability(InspectionTarget(
    componentDescription = "ActionButton (MainToolbar)",
    iconClass = "SwingIcon",
    bridged = true,
    summary = listOf(
      InspectorProperty("Action id", "SearchEverywhere"),
      InspectorProperty("Place", "MainToolbarRight"),
    ),
    root = root,
    rendering = RenderingSnapshot(
      properties = listOf(
        InspectorProperty("Density", "2.0"),
        InspectorProperty("Context scale", "1.25"),
        InspectorProperty("Final dimensions @1x", "20×20"),
        InspectorProperty("Final dimensions @2x", "40×40"),
        InspectorProperty("Theme digest", "a1b2c3d4"),
        InspectorProperty("Image modifiers", "ScaleModifier(1.25), AlphaIconModifier(0.5)"),
      ),
      lastPaint = listOf(
        InspectorProperty("Component", "ActionButton"),
        InspectorProperty("x / y", "6 / 4"),
        InspectorProperty("Density", "2.0"),
        InspectorProperty("Size", "20×20"),
        InspectorProperty("Paint count", "1"),
      ),
    ),
    sources = sampleSources(),
    previews = listOf(
      PreviewImage("1×", 16, 16, magnifier(16)),
      PreviewImage("1.25×", 20, 20, magnifier(20)),
      PreviewImage("2×", 32, 32, magnifier(32)),
    ),
  ), churning = true)
}

/** A tiny magnifier-ish ARGB pattern: a ring plus a diagonal handle. */
private fun magnifier(size: Int): IntArray {
  val pixels = IntArray(size * size)
  val cx = size * 0.42
  val cy = size * 0.42
  val radius = size * 0.32
  val thickness = maxOf(1.0, size / 10.0)
  for (y in 0 until size) {
    for (x in 0 until size) {
      val dx = x + 0.5 - cx
      val dy = y + 0.5 - cy
      val dist = Math.sqrt(dx * dx + dy * dy)
      val inRing = Math.abs(dist - radius) <= thickness / 2
      val onHandle = dx > 0 && dy > 0 && Math.abs(dx - dy) <= thickness / 2 && dist >= radius && dist <= size * 0.65
      if (inRing || onHandle) pixels[y * size + x] = 0xFF6C707E.toInt()
    }
  }
  return pixels
}

private const val NEW_SVG = """<!-- Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license. -->
<svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg">
<circle cx="7" cy="7" r="4.5" stroke="#6C707E"/>
<path d="M10.1992 10.2002L13.4992 13.4961" stroke="#6C707E" stroke-linecap="round"/>
</svg>"""

private const val LEGACY_SVG = """<!-- Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license. -->
<svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
<path fill-rule="evenodd" clip-rule="evenodd" d="M14.5 9C14.5 12.0376 12.0376 14.5 9 14.5C5.96243 14.5 3.5 12.0376 3.5 9C3.5 5.96243 5.96243 3.5 9 3.5C12.0376 3.5 14.5 5.96243 14.5 9ZM13.3584 14.478C12.1625 15.4307 10.6478 16 9 16C5.13401 16 2 12.866 2 9C2 5.13401 5.13401 2 9 2C12.866 2 16 5.13401 16 9C16 10.6478 15.4307 12.1625 14.478 13.3584C14.5086 13.3812 14.5379 13.4065 14.5657 13.4343L18.5657 17.4343C18.8781 17.7467 18.8781 18.2533 18.5657 18.5657C18.2533 18.8781 17.7467 18.8781 17.4343 18.5657L13.4343 14.5657C13.4065 14.5379 13.3812 14.5086 13.3584 14.478Z" fill="#6C707E"/>
</svg>"""

private fun sampleSources(): List<ResolvedSvgSource> {
  val newPixels = glyph(40, 30)
  val legacyPixels = glyph(40, 34)
  return listOf(
    ResolvedSvgSource(
      pipeline = IconPipeline.NEW_ICONS_API,
      basePath = "expui/general/search.svg",
      steps = listOf("no size variant: base artwork scaled 1.25×"),
      finalPath = "expui/general/search.svg",
      resolution = "pixel match 100.0% vs expui/general/search.svg",
      svgText = NEW_SVG,
      patches = listOf("color patcher: #6C707E → theme text color"),
      rendering = PreviewImage("as painted", 40, 40, newPixels),
      contentBounds = bounds(40, newPixels),
    ),
    ResolvedSvgSource(
      pipeline = IconPipeline.LEGACY,
      basePath = "general/search.svg",
      steps = listOf(
        "theme mapping general/search.svg → expui/general/search.svg",
        "size variant @20x20 → expui/general/search@20x20.svg",
      ),
      finalPath = "expui/general/search@20x20.svg",
      resolution = "reproduced HeaderToolbarButtonLook decision",
      svgText = LEGACY_SVG,
      rendering = PreviewImage("as painted", 40, 40, legacyPixels),
      contentBounds = bounds(40, legacyPixels),
    ),
  )
}

/** A ring plus handle whose overall glyph spans roughly [glyphSize] pixels, centered in a [frame]-pixel square. */
private fun glyph(frame: Int, glyphSize: Int): IntArray {
  val pixels = IntArray(frame * frame)
  val origin = (frame - glyphSize) / 2.0
  val radius = glyphSize * 0.36
  val cx = origin + radius + 1
  val cy = cx
  val thickness = glyphSize / 12.0
  for (y in 0 until frame) {
    for (x in 0 until frame) {
      val dx = x + 0.5 - cx
      val dy = y + 0.5 - cy
      val dist = Math.sqrt(dx * dx + dy * dy)
      val ring = Math.abs(dist - radius) <= thickness / 2
      val handle = dx > 0 && dy > 0 && Math.abs(dx - dy) <= thickness / 2 && dist >= radius && x < origin + glyphSize && y < origin + glyphSize
      if (ring || handle) pixels[y * frame + x] = 0xFF6C707E.toInt()
    }
  }
  return pixels
}

private fun bounds(frame: Int, pixels: IntArray): String? {
  var minX = frame; var minY = frame; var maxX = -1; var maxY = -1
  for (y in 0 until frame) for (x in 0 until frame) {
    if ((pixels[y * frame + x] ushr 24) != 0) {
      minX = minOf(minX, x); minY = minOf(minY, y); maxX = maxOf(maxX, x); maxY = maxOf(maxY, y)
    }
  }
  return if (maxX < 0) null else "${maxX - minX + 1}×${maxY - minY + 1} at ($minX,$minY)"
}

private const val IMAGE_LAYER_ID = "0.0.0.0.0"

private fun field(key: String, label: String, type: ModifierFieldType, value: String, options: List<String> = emptyList()) =
  ModifierField(key, label, type, value, options)

private fun initialModifiers() = listOf(
  EditableModifier("m0", EditableModifierKind.SCALE, "ScaleModifier", listOf(field("factor", "Factor", ModifierFieldType.NUMBER, "1.25"))),
  EditableModifier("m1", EditableModifierKind.ALPHA, "AlphaIconModifier", listOf(field("alpha", "Alpha", ModifierFieldType.NUMBER, "0.5"))),
  EditableModifier(
    "m2", EditableModifierKind.READ_ONLY, "SvgPatcherModifier",
    listOf(ModifierField("patcher", "Patcher", ModifierFieldType.TEXT, "ColorPatcher(#6C707E)", editable = false)),
  ),
)

private fun newModifier(kind: EditableModifierKind, id: String): EditableModifier {
  val align = listOf("Start", "Center", "End")
  val (title, fields) = when (kind) {
    EditableModifierKind.SCALE -> "ScaleModifier" to listOf(field("factor", "Factor", ModifierFieldType.NUMBER, "1.0"))
    EditableModifierKind.ALPHA -> "AlphaIconModifier" to listOf(field("alpha", "Alpha", ModifierFieldType.NUMBER, "1.0"))
    EditableModifierKind.MARGIN -> "MarginIconModifier" to listOf("top", "left", "bottom", "right").map { field(it, it, ModifierFieldType.UNIT, "0dp") }
    EditableModifierKind.ALIGN -> "AlignIconModifier" to listOf(
      field("horizontal", "Horizontal", ModifierFieldType.CHOICE, "Center", align),
      field("vertical", "Vertical", ModifierFieldType.CHOICE, "Center", align),
    )
    EditableModifierKind.COLOR_FILTER -> "ColorFilterModifier" to listOf(
      field("color", "Color", ModifierFieldType.COLOR, "#FF000000"),
      field("mode", "Blend mode", ModifierFieldType.CHOICE, "SrcIn", listOf("SrcIn", "SrcOver", "Multiply")),
    )
    EditableModifierKind.STROKE -> "StrokeModifier" to listOf(field("color", "Color", ModifierFieldType.COLOR, "#FFFFFFFF"))
    EditableModifierKind.CUTOUT_MARGIN -> "CutoutMarginModifier" to listOf(field("margin", "Margin", ModifierFieldType.UNIT, "2dp"))
    EditableModifierKind.SIZE_VARIANTS -> "SizeVariantsModifier" to listOf(field("sizes", "Sizes", ModifierFieldType.TEXT, "16,20"))
    EditableModifierKind.READ_ONLY -> "Unknown" to emptyList()
  }
  return EditableModifier(id, kind, title, fields)
}
