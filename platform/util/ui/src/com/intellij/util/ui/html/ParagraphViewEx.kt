// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.html

import com.intellij.util.asSafely
import java.awt.Shape
import java.awt.Toolkit
import java.util.*
import javax.swing.SizeRequirements
import javax.swing.text.*
import javax.swing.text.Position.Bias
import javax.swing.text.html.CSS
import javax.swing.text.html.ParagraphView
import kotlin.math.max
import kotlin.math.min

/**
 * Supports line-height (%, px and no-unit) property in paragraphs.
 */
open class ParagraphViewEx(elem: Element) : ParagraphView(elem) {

  @Suppress("ProtectedInFinal")
  @JvmField
  protected var fixedLineHeight: Float? = null

  @Suppress("ProtectedInFinal")
  @JvmField
  protected var scaledLineHeight: Float? = null

  @Suppress("ProtectedInFinal")
  @JvmField
  protected var fontLineHeight: Int? = null

  @Suppress("ProtectedInFinal")
  @JvmField
  protected var justification: Int = 0

  override fun setPropertiesFromAttributes() {
    super.setPropertiesFromAttributes()

    val lineHeight = attributes.getAttribute(CSS.Attribute.LINE_HEIGHT)
      ?.asSafely<String>()
      ?.trim()

    when {
      lineHeight == null -> {}
      lineHeight.endsWith("%") ->
        lineHeight
          .removeSuffix("%")
          .toFloatOrNull()
          ?.let { scaledLineHeight = it / 100f }
      lineHeight.endsWith("px") ->
        lineHeight
          .removeSuffix("px")
          .toFloatOrNull()
          ?.let { fixedLineHeight = it }
      else ->
        lineHeight
          .toFloatOrNull()
          ?.let { scaledLineHeight = it }
    }
  }

  override fun calculateMinorAxisRequirements(axis: Int, r: SizeRequirements?): SizeRequirements {
    val font = document.asSafely<StyledDocument>()?.getFont(attributes)
               ?: container?.font
    font
      ?.let { container?.getFontMetrics(it) ?: Toolkit.getDefaultToolkit().getFontMetrics(font) }
      .let { fontLineHeight = it?.height }
    return super.calculateMinorAxisRequirements(axis, r)
  }

  override fun createRow(): View {
    return Row(element)
  }

  inner class Row internal constructor(elem: Element) : BoxView(elem, X_AXIS) {
    /**
     * This is reimplemented to do nothing since the
     * paragraph fills in the row with its needed
     * children.
     */
    override fun loadChildren(f: ViewFactory) {
    }

    /**
     * Fetches the attributes to use when rendering.  This view
     * isn't directly responsible for an element so it returns
     * the outer classes attributes.
     */
    override fun getAttributes(): AttributeSet? {
      val p = parent
      return if ((p != null)) p.attributes else null
    }

    override fun getAlignment(axis: Int): Float {
      if (axis == X_AXIS) {
        when (justification) {
          StyleConstants.ALIGN_LEFT -> return 0f
          StyleConstants.ALIGN_RIGHT -> return 1f
          StyleConstants.ALIGN_CENTER -> return 0.5f
          StyleConstants.ALIGN_JUSTIFIED -> {
            var rv = 0.5f
            //if we can justifiy the content always align to
            //the left.
            if (isJustifiableDocument) {
              rv = 0f
            }
            return rv
          }
        }
      }
      return super.getAlignment(axis)
    }

    /**
     * Provides a mapping from the document model coordinate space
     * to the coordinate space of the view mapped to it.  This is
     * implemented to let the superclass find the position along
     * the major axis and the allocation of the row is used
     * along the minor axis, so that even though the children
     * are different heights they all get the same caret height.
     *
     * @param pos the position to convert
     * @param a the allocated region to render into
     * @return the bounding box of the given position
     * @exception BadLocationException  if the given position does not represent a
     * valid location in the associated document
     * @see View.modelToView
     */
    @Throws(BadLocationException::class)
    override fun modelToView(pos: Int, a: Shape, b: Bias): Shape {
      var r = a.bounds
      val v = getViewAtPosition(pos, r)
      if ((v != null) && (!v.element.isLeaf)) {
        // Don't adjust the height if the view represents a branch.
        return super.modelToView(pos, a, b)
      }
      r = a.bounds
      val height = r.height
      val y = r.y
      val loc = super.modelToView(pos, a, b)
      val bounds = loc.bounds2D
      bounds.setRect(bounds.x, y.toDouble(), bounds.width, height.toDouble())
      return bounds
    }

    /**
     * Range represented by a row in the paragraph is only
     * a subset of the total range of the paragraph element.
     */
    override fun getStartOffset(): Int {
      var offs = Int.MAX_VALUE
      val n = viewCount
      for (i in 0 until n) {
        val v = getView(i)
        offs = min(offs.toDouble(), v.startOffset.toDouble()).toInt()
      }
      return offs
    }

    override fun getEndOffset(): Int {
      var offs = 0
      val n = viewCount
      for (i in 0 until n) {
        val v = getView(i)
        offs = max(offs.toDouble(), v.endOffset.toDouble()).toInt()
      }
      return offs
    }

    /**
     * Perform layout for the minor axis of the box (i.e. the
     * axis orthogonal to the axis that it represents).  The results
     * of the layout should be placed in the given arrays which represent
     * the allocations to the children along the minor axis.
     *
     *
     * This is implemented to do a baseline layout of the children
     * by calling BoxView.baselineLayout.
     *
     * @param targetSpan the total span given to the view, which
     * would be used to layout the children.
     * @param axis the axis being layed out.
     * @param offsets the offsets from the origin of the view for
     * each of the child views.  This is a return value and is
     * filled in by the implementation of this method.
     * @param spans the span of each child view.  This is a return
     * value and is filled in by the implementation of this method.
     * @return the offset and span for each child view in the
     * offsets and spans parameters
     */
    override fun layoutMinorAxis(targetSpan: Int, axis: Int, offsets: IntArray, spans: IntArray) {
      baselineLayout(targetSpan, axis, offsets, spans)
    }

    override fun calculateMinorAxisRequirements(axis: Int,
                                                r: SizeRequirements?): SizeRequirements {
      return baselineRequirements(axis, r)
    }


    private val isLastRow: Boolean
      get() {
        var parent: View
        return ((getParent().also { parent = it }) == null
                || this === parent.getView(parent.viewCount - 1))
      }

    private val isBrokenRow: Boolean
      get() {
        var rv = false
        val viewsCount = viewCount
        if (viewsCount > 0) {
          val lastView = getView(viewsCount - 1)
          if (lastView.getBreakWeight(X_AXIS, 0f, 0f) >=
              ForcedBreakWeight) {
            rv = true
          }
        }
        return rv
      }

    private val isJustifiableDocument: Boolean
      get() = (true != document.getProperty("i18n"))

    private val isJustifyEnabled: Boolean
      /**
       * Whether we need to justify this `Row`.
       * At this time (jdk1.6) we support justification on for non
       * 18n text.
       *
       * @return `true` if this `Row` should be justified.
       */
      get() {
        var ret = (justification == StyleConstants.ALIGN_JUSTIFIED)

        //no justification for i18n documents
        ret = ret && isJustifiableDocument

        //no justification for the last row
        ret = ret && !isLastRow

        //no justification for the broken rows
        ret = ret && !isBrokenRow

        return ret
      }


    //Calls super method after setting spaceAddon to 0.
    //Justification should not affect MajorAxisRequirements
    override fun calculateMajorAxisRequirements(axis: Int,
                                                r: SizeRequirements?): SizeRequirements {
      val oldJustficationData = justificationData
      justificationData = null
      val ret = super.calculateMajorAxisRequirements(axis, r)
      if (isJustifyEnabled) {
        justificationData = oldJustficationData
      }
      return ret
    }

    override fun layoutMajorAxis(targetSpan: Int, axis: Int,
                                 offsets: IntArray, spans: IntArray) {
      val oldJustficationData = justificationData
      justificationData = null
      super.layoutMajorAxis(targetSpan, axis, offsets, spans)
      if (!isJustifyEnabled) {
        return
      }

      var currentSpan = 0
      for (span in spans) {
        currentSpan += span
      }
      if (currentSpan == targetSpan) {
        //no need to justify
        return
      }

      // we justify text by enlarging spaces by the {@code spaceAddon}.
      // justification is started to the right of the rightmost TAB.
      // leading and trailing spaces are not extendable.
      //
      // GlyphPainter1 uses
      // justificationData
      // for all painting and measurement.
      var extendableSpaces = 0
      var startJustifiableContent = -1
      var endJustifiableContent = -1
      var lastLeadingSpaces = 0

      val rowStartOffset = startOffset
      val rowEndOffset = endOffset
      val spaceMap = IntArray(rowEndOffset - rowStartOffset)
      Arrays.fill(spaceMap, 0)
      for (i in viewCount - 1 downTo 0) {
        val view = getView(i)
        if (view is GlyphView) {
          val justificationInfo = view.getJustificationInfo(rowStartOffset)
          val viewStartOffset = view.getStartOffset()
          val offset = viewStartOffset - rowStartOffset
          for (j in 0 until justificationInfo.spaceMap.length()) {
            if (justificationInfo.spaceMap[j]) {
              spaceMap[j + offset] = 1
            }
          }
          if (startJustifiableContent > 0) {
            if (justificationInfo.end >= 0) {
              extendableSpaces += justificationInfo.trailingSpaces
            }
            else {
              lastLeadingSpaces += justificationInfo.trailingSpaces
            }
          }
          if (justificationInfo.start >= 0) {
            startJustifiableContent =
              justificationInfo.start + viewStartOffset
            extendableSpaces += lastLeadingSpaces
          }
          if (justificationInfo.end >= 0
              && endJustifiableContent < 0) {
            endJustifiableContent =
              justificationInfo.end + viewStartOffset
          }
          extendableSpaces += justificationInfo.contentSpaces
          lastLeadingSpaces = justificationInfo.leadingSpaces
          if (justificationInfo.hasTab) {
            break
          }
        }
      }
      if (extendableSpaces <= 0) {
        //there is nothing we can do to justify
        return
      }
      val adjustment = targetSpan - currentSpan
      val spaceAddon = adjustment / extendableSpaces
      var spaceAddonLeftoverEnd = -1
      var i = startJustifiableContent - rowStartOffset
      var leftover = adjustment - spaceAddon * extendableSpaces
      while (leftover > 0
      ) {
        spaceAddonLeftoverEnd = i
        leftover -= spaceMap[i]
        i++
      }
      if (spaceAddon > 0 || spaceAddonLeftoverEnd >= 0) {
        justificationData = if ((oldJustficationData != null)
        ) oldJustficationData
        else IntArray(END_JUSTIFIABLE + 1)
        justificationData!![SPACE_ADDON] = spaceAddon
        justificationData!![SPACE_ADDON_LEFTOVER_END] =
          spaceAddonLeftoverEnd
        justificationData!![START_JUSTIFIABLE] =
          startJustifiableContent - rowStartOffset
        justificationData!![END_JUSTIFIABLE] =
          endJustifiableContent - rowStartOffset
        super.layoutMajorAxis(targetSpan, axis, offsets, spans)
      }
    }

    //for justified row we assume the maximum horizontal span
    //is MAX_VALUE.
    override fun getMaximumSpan(axis: Int): Float {
      val ret = if (X_AXIS == axis
                    && isJustifyEnabled) {
        Float.MAX_VALUE
      }
      else {
        super.getMaximumSpan(axis)
      }
      return ret
    }

    /**
     * Fetches the child view index representing the given position in
     * the model.
     *
     * @param pos the position &gt;= 0
     * @return  index of the view representing the given position, or
     * -1 if no view represents that position
     */
    override fun getViewIndexAtPosition(pos: Int): Int {
      // This is expensive, but are views are not necessarily layed
      // out in model order.
      if (pos < startOffset || pos >= endOffset) return -1
      for (counter in viewCount - 1 downTo 0) {
        val v = getView(counter)
        if (pos >= v.startOffset &&
            pos < v.endOffset) {
          return counter
        }
      }
      return -1
    }

    /**
     * Gets the left inset.
     *
     * @return the inset
     */
    override fun getLeftInset(): Short {
      var parentView: View
      var adjustment = 0
      if ((parent.also { parentView = it }) != null) { //use firstLineIdent for the first row
        if (this === parentView.getView(0)) {
          adjustment = firstLineIndent
        }
      }
      return (super.getLeftInset() + adjustment).toShort()
    }

    override fun getTopInset(): Short {
      return (super.getTopInset() + lineHeightCorrection / 2).toShort()
    }

    override fun getBottomInset(): Short {
      return (super.getBottomInset() + lineHeightCorrection / 2).toShort()
    }

    private val lineHeightCorrection: Int
      get() {
        val expectedLineHeight = scaledLineHeight?.let { it * (fontLineHeight ?: return 0) }
                                 ?: fixedLineHeight
                                 ?: return 0
        val actualLineHeight = minorRequest?.preferred ?: 0
        return max(0f, expectedLineHeight - actualLineHeight).toInt()
      }

    private var justificationData: IntArray? = null

  }

  companion object {
    const val SPACE_ADDON: Int = 0
    const val SPACE_ADDON_LEFTOVER_END: Int = 1
    const val START_JUSTIFIABLE: Int = 2

    //this should be the last index in justificationData
    const val END_JUSTIFIABLE: Int = 3
  }


}