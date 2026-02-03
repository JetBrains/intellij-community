// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceIsEmptyWithIfEmpty")

package com.intellij.ui.dsl.builder.impl

import com.intellij.ide.TooltipTitle
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.validation.DialogValidation
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.openapi.ui.validation.invoke
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.dsl.UiDslException
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.ChangeContext
import com.intellij.ui.dsl.builder.HyperlinkEventAction
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_NO_WRAP
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.components.DslLabel
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.toUnscaled
import com.intellij.ui.dsl.validation.CellValidation
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.ui.layout.ValidationInfoBuilder
import com.intellij.util.containers.map2Array
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JScrollPane
import javax.swing.event.DocumentEvent
import javax.swing.text.BadLocationException

@ApiStatus.Internal
internal class CellImpl<T : JComponent>(
  private val dialogPanelConfig: DialogPanelConfig,
  component: T,
  private val parent: RowImpl,
  val viewComponent: JComponent = component,
) : CellBaseImpl<Cell<T>>(), Cell<T> {

  data class ContextHelpInfo(val description: @NlsContexts.Tooltip String, val title: @TooltipTitle String?)

  override var component: T = component
    private set

  override var comment: DslLabel? = null
    private set

  override var commentRight: DslLabel? = null
    private set

  var contextHelpLabel: ContextHelpLabel? = null
    private set

  var contextHelpInfo: ContextHelpInfo? = null
    private set

  var label: JLabel? = null
    private set

  var labelPosition: LabelPosition = LabelPosition.LEFT
    private set

  var widthGroup: String? = null
    private set

  private var applyIfEnabled = false

  private var visible = viewComponent.isVisible
  private var enabled = viewComponent.isEnabled

  private val cellValidation = CellValidationImpl(dialogPanelConfig, component, component.interactiveComponent)
  private var lastAutoCalculatedAccessibleDescription: @NlsSafe String? = null

  val onChangeManager: OnChangeManager<T> = OnChangeManager(component)

  @Deprecated("Use align(AlignX.LEFT/CENTER/RIGHT/FILL) method instead", level = DeprecationLevel.HIDDEN)
  @ApiStatus.ScheduledForRemoval
  override fun horizontalAlign(horizontalAlign: HorizontalAlign): CellImpl<T> {
    super.horizontalAlign(horizontalAlign)
    return this
  }

  @Deprecated("Use align(AlignY.TOP/CENTER/BOTTOM/FILL) method instead", level = DeprecationLevel.HIDDEN)
  @ApiStatus.ScheduledForRemoval
  override fun verticalAlign(verticalAlign: VerticalAlign): CellImpl<T> {
    super.verticalAlign(verticalAlign)
    return this
  }

  override fun align(align: Align): CellImpl<T> {
    super.align(align)

    (component as? DslLabel)?.let {
      if (it.maxLineLength == MAX_LINE_LENGTH_WORD_WRAP) {
        it.limitPreferredSize = horizontalAlign == HorizontalAlign.FILL
      }
    }

    return this
  }

  override fun resizableColumn(): CellImpl<T> {
    super.resizableColumn()
    return this
  }

  override fun gap(rightGap: RightGap): CellImpl<T> {
    super.gap(rightGap)
    return this
  }

  override fun focused(): CellImpl<T> {
    dialogPanelConfig.preferredFocusedComponent = component
    return this
  }

  override fun applyToComponent(task: T.() -> Unit): CellImpl<T> {
    component.task()
    return this
  }

  override fun enabledFromParent(parentEnabled: Boolean) {
    doEnabled(parentEnabled && enabled)
  }

  override fun enabled(isEnabled: Boolean): CellImpl<T> {
    enabled = isEnabled
    if (parent.isEnabled()) {
      doEnabled(enabled)
    }
    return this
  }

  override fun enabledIf(predicate: ComponentPredicate): Cell<T> {
    super.enabledIf(predicate)
    return this
  }

  override fun enabledIf(property: ObservableProperty<Boolean>): Cell<T> {
    super.enabledIf(property)
    return this
  }

  override fun visibleFromParent(parentVisible: Boolean) {
    doVisible(parentVisible && visible)
  }

  override fun visible(isVisible: Boolean): CellImpl<T> {
    visible = isVisible
    if (parent.isVisible()) {
      doVisible(visible)
    }
    return this
  }

  override fun visibleIf(predicate: ComponentPredicate): CellImpl<T> {
    super.visibleIf(predicate)
    return this
  }

  override fun visibleIf(property: ObservableProperty<Boolean>): Cell<T> {
    super.visibleIf(property)
    return this
  }

  override fun bold(): CellImpl<T> {
    component.font = JBFont.create(component.font.deriveFont(Font.BOLD), false)
    return this
  }

  override fun comment(@NlsContexts.DetailedDescription comment: String?, maxLineLength: Int, action: HyperlinkEventAction): CellImpl<T> {
    this.comment = if (comment == null) null
    else createComment(comment, maxLineLength, action).apply {
      registerCreationStacktrace(this)
      document.addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          updateAccessibleContextDescription()
        }
      })
    }
    updateAccessibleContextDescription()
    return this
  }

  override fun commentRight(comment: String?, action: HyperlinkEventAction): Cell<T> {
    this.commentRight = if (comment == null) null
    else createComment(comment, MAX_LINE_LENGTH_NO_WRAP, action).apply {
      registerCreationStacktrace(this)
      document.addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          updateAccessibleContextDescription()
        }
      })
    }
    updateAccessibleContextDescription()
    return this
  }

  override fun contextHelp(@NlsContexts.Tooltip description: String, @TooltipTitle title: String?): Cell<T> {
    checkDeniedHtmlTags(description)
    if (title != null) {
      checkDeniedHtmlTags(title)
    }

    val contextHelpLabel = createContextHelp(description, title)
    this.contextHelpLabel = contextHelpLabel
    contextHelpInfo = ContextHelpInfo(description, title)

    registerCreationStacktrace(contextHelpLabel)
    updateAccessibleContextDescription()

    return this
  }

  override fun label(label: String, position: LabelPosition): CellImpl<T> {
    return label(createLabel(label), position)
  }

  override fun label(label: JLabel, position: LabelPosition): CellImpl<T> {
    this.label = label
    labelPosition = position
    label.putClientProperty(DslComponentPropertyInternal.CELL_LABEL, true)
    registerCreationStacktrace(label)
    return this
  }

  override fun widthGroup(group: String): CellImpl<T> {
    widthGroup = group
    return this
  }

  override fun applyIfEnabled(): CellImpl<T> {
    applyIfEnabled = true
    return this
  }

  override fun accessibleName(name: String): CellImpl<T> {
    component.accessibleContext.accessibleName = name
    return this
  }

  override fun accessibleDescription(description: String): CellImpl<T> {
    component.accessibleContext.accessibleDescription = description
    return this
  }

  override fun <V> bind(componentGet: (T) -> V, componentSet: (T, V) -> Unit, prop: MutableProperty<V>): CellImpl<T> {
    onChangeManager.applyBinding {
      componentSet(component, prop.get())
    }

    onApply {
      if (shouldSaveOnApply()) prop.set(componentGet(component))
    }
    onReset {
      onChangeManager.applyBinding {
        componentSet(component, prop.get())
      }
    }
    onIsModified {
      shouldSaveOnApply() && componentGet(component) != prop.get()
    }
    return this
  }

  override fun validationRequestor(validationRequestor: (() -> Unit) -> Unit): CellImpl<T> {
    return validationRequestor(DialogValidationRequestor { _, it -> validationRequestor(it) })
  }

  override fun validationRequestor(validationRequestor: DialogValidationRequestor): CellImpl<T> {
    val interactiveComponent = component.interactiveComponent
    dialogPanelConfig.validationRequestors.list(interactiveComponent).add(validationRequestor)
    return this
  }

  override fun validationRequestor(validationRequestor: DialogValidationRequestor.WithParameter<T>): CellImpl<T> {
    return validationRequestor(validationRequestor(component))
  }

  override fun validationInfo(validation: ValidationInfoBuilder.(T) -> ValidationInfo?): CellImpl<T> {
    validationOnInput(validation)
    validationOnApply(validation)
    return this
  }

  override fun validation(vararg validations: DialogValidation): CellImpl<T> {
    validationOnInput(*validations)
    validationOnApply(*validations)
    return this
  }

  override fun validation(vararg validations: DialogValidation.WithParameter<T>): CellImpl<T> {
    validationOnInput(*validations)
    validationOnApply(*validations)
    return this
  }

  override fun cellValidation(init: CellValidation<T>.(T) -> Unit): CellImpl<T> {
    cellValidation.init(component)
    return this
  }

  override fun validationOnInput(validation: ValidationInfoBuilder.(T) -> ValidationInfo?): CellImpl<T> {
    val interactiveComponent = component.interactiveComponent
    return validationOnInput(DialogValidation { ValidationInfoBuilder(interactiveComponent).validation(component) })
  }

  override fun validationOnInput(vararg validations: DialogValidation): CellImpl<T> {
    CellValidationImpl.installValidationOnInput(dialogPanelConfig, component.interactiveComponent, *validations)
    return this
  }

  override fun validationOnInput(vararg validations: DialogValidation.WithParameter<T>): CellImpl<T> {
    return validationOnInput(*validations.map2Array { it(component) })
  }

  override fun validationOnApply(validation: ValidationInfoBuilder.(T) -> ValidationInfo?): CellImpl<T> {
    val origin = component.interactiveComponent
    return validationOnApply(DialogValidation { ValidationInfoBuilder(origin).validation(component) })
  }

  override fun validationOnApply(vararg validations: DialogValidation): CellImpl<T> {
    CellValidationImpl.installValidationOnApply(dialogPanelConfig, component.interactiveComponent, *validations)
    return this
  }

  override fun validationOnApply(vararg validations: DialogValidation.WithParameter<T>): CellImpl<T> {
    return validationOnApply(*validations.map2Array { it(component) })
  }

  override fun addValidationRule(message: String, condition: (T) -> Boolean): Cell<T> {
    return validationOnApply { if (condition(it)) error(message) else null }
  }

  override fun errorOnApply(message: String, condition: (T) -> Boolean): Cell<T> = addValidationRule(message, condition)

  override fun onApply(callback: () -> Unit): CellImpl<T> {
    dialogPanelConfig.applyCallbacks.list(component).add(callback)
    return this
  }

  override fun onReset(callback: () -> Unit): CellImpl<T> {
    dialogPanelConfig.resetCallbacks.list(component).add(callback)
    return this
  }

  override fun onIsModified(callback: () -> Boolean): CellImpl<T> {
    dialogPanelConfig.isModifiedCallbacks.list(component).add(callback)
    return this
  }

  @Throws(UiDslException::class)
  override fun onChangedContext(listener: (component: T, context: ChangeContext) -> Unit): CellImpl<T> {
    onChangeManager.register(listener)
    return this
  }

  @Throws(UiDslException::class)
  override fun onChanged(listener: (component: T) -> Unit): Cell<T> {
    onChangeManager.register { component, _ -> listener(component) }
    return this
  }

  @Deprecated("Use customize(UnscaledGaps) instead")
  @ApiStatus.ScheduledForRemoval
  override fun customize(customGaps: Gaps): CellImpl<T> {
    return customize(customGaps.toUnscaled())
  }

  override fun customize(customGaps: UnscaledGaps): CellImpl<T> {
    super.customize(customGaps)
    return this
  }

  private fun shouldSaveOnApply(): Boolean {
    return !(applyIfEnabled && !viewComponent.isEnabled)
  }

  private fun doVisible(isVisible: Boolean) {
    if (viewComponent.isVisible != isVisible) {
      viewComponent.isVisible = isVisible
      comment?.let { it.isVisible = isVisible }
      commentRight?.let { it.isVisible = isVisible }
      contextHelpLabel?.let { it.isVisible = isVisible }
      label?.let { it.isVisible = isVisible }

      // Force parent to re-layout
      viewComponent.parent?.revalidate()
    }
  }

  private fun doEnabled(isEnabled: Boolean) {
    when (viewComponent) {
      is JScrollPane -> {
        if (viewComponent === component) {
          // ScrollPane was added via [Row.cell] method
          viewComponent.viewport?.view?.isEnabled = isEnabled
        }
        else {
          component.isEnabled = isEnabled
        }
      }

      is JLabel -> patchedEnableJLabel(viewComponent, isEnabled)
      else -> viewComponent.isEnabled = isEnabled
    }
    comment?.let { it.isEnabled = isEnabled }
    commentRight?.let { it.isEnabled = isEnabled }
    contextHelpLabel?.let { it.isEnabled = isEnabled }
    label?.let { patchedEnableJLabel(it, isEnabled) }
  }

  private fun updateAccessibleContextDescription() {
    val accessibleContext = component.accessibleContext ?: return
    val currentDescription = accessibleContext.accessibleDescription

    if (currentDescription != null && currentDescription != lastAutoCalculatedAccessibleDescription) {
      // Description is set from another place, don't change it
      return
    }

    lastAutoCalculatedAccessibleDescription = AccessibleContextUtil.joinAccessibleStrings("\n",
      commentRight?.getPlainText(),
      comment?.getPlainText(),
      contextHelpInfo?.title?.stripHtml(),
      contextHelpInfo?.description?.stripHtml(),
    )

    component.accessibleContext.accessibleDescription = lastAutoCalculatedAccessibleDescription
  }

  /**
   * Extract text without html tags
   */
  @Suppress("HardCodedStringLiteral")
  private fun DslLabel.getPlainText(): @Nls String? {
    val document = document ?: return null
    try {
      val result = document.getText(0, document.length) ?: return null
      return result.trim().takeIf { it.isNotEmpty() }
    }
    catch (_: BadLocationException) {
      return null
    }
  }

  companion object {
    internal fun Cell<*>.installValidationRequestor(property: ObservableProperty<*>) {
      CellValidationImpl.installDefaultValidationRequestor(component.interactiveComponent, property)
    }
  }
}

/**
 * Changing JLabel.isEnabled can lead to icon change and therefore requires revalidation
 */
private fun patchedEnableJLabel(label: JLabel, enabled: Boolean) {
  if (label.isEnabled == enabled) return

  val initialIcon = if (label.isEnabled) label.icon else label.getDisabledIcon()
  label.isEnabled = enabled

  val newIcon = if (label.isEnabled) label.icon else label.getDisabledIcon()
  if (initialIcon !== newIcon) {
    label.parent?.apply {
      revalidate()
      repaint()
    }
  }
}