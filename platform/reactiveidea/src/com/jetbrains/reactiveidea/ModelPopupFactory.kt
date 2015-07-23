/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.reactiveidea

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.*
import com.intellij.openapi.util.Condition
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.popup.ActionPopupStep
import com.intellij.ui.popup.ActionStepBuilder
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.popup.list.ListPopupImpl
import com.jetbrains.reactivemodel.Path
import com.jetbrains.reactivemodel.ReactiveModel
import java.awt.Color
import java.awt.Component
import java.awt.Point
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.event.HyperlinkListener
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

public class ModelPopupFactory : JBPopupFactory() {

  val delegate = PopupFactoryImpl()

  override fun createConfirmation(title: String?, onYes: Runnable?, defaultOptionIndex: Int): ListPopup {
    println("title = [${title}], onYes = [${onYes}], defaultOptionIndex = [${defaultOptionIndex}]")
    throw UnsupportedOperationException()
  }

  override fun createConfirmation(title: String?, yesText: String?, noText: String?, onYes: Runnable?, defaultOptionIndex: Int): ListPopup {
    println("title = [${title}], yesText = [${yesText}], noText = [${noText}], onYes = [${onYes}], defaultOptionIndex = [${defaultOptionIndex}]")
    throw UnsupportedOperationException()
  }

  override fun createConfirmation(title: String?, yesText: String?, noText: String?, onYes: Runnable?, onNo: Runnable?, defaultOptionIndex: Int): ListPopup {
    println("title = [${title}], yesText = [${yesText}], noText = [${noText}], onYes = [${onYes}], onNo = [${onNo}], defaultOptionIndex = [${defaultOptionIndex}]")
    throw UnsupportedOperationException()
  }

  override fun createActionsStep(actionGroup: ActionGroup, dataContext: DataContext, showNumbers: Boolean, showDisabledActions: Boolean, title: String?, component: Component?, honorActionMnemonics: Boolean): ListPopupStep<*> {
    println("actionGroup = [${actionGroup}], dataContext = [${dataContext}], showNumbers = [${showNumbers}], showDisabledActions = [${showDisabledActions}], title = [${title}], component = [${component}], honorActionMnemonics = [${honorActionMnemonics}]")
    throw UnsupportedOperationException()
  }

  override fun createActionsStep(actionGroup: ActionGroup, dataContext: DataContext, showNumbers: Boolean, showDisabledActions: Boolean, title: String?, component: Component?, honorActionMnemonics: Boolean, defaultOptionIndex: Int, autoSelectionEnabled: Boolean): ListPopupStep<*> {
    println("actionGroup = [${actionGroup}], dataContext = [${dataContext}], showNumbers = [${showNumbers}], showDisabledActions = [${showDisabledActions}], title = [${title}], component = [${component}], honorActionMnemonics = [${honorActionMnemonics}], defaultOptionIndex = [${defaultOptionIndex}], autoSelectionEnabled = [${autoSelectionEnabled}]")
    throw UnsupportedOperationException()
  }

  override fun guessBestPopupLocation(component: JComponent): RelativePoint {
    println("component = [${component}]")
    throw UnsupportedOperationException()
  }

  override fun createActionGroupPopup(title: String?, actionGroup: ActionGroup, dataContext: DataContext, selectionAidMethod: JBPopupFactory.ActionSelectionAid?, showDisabledActions: Boolean): ListPopup {
    return createActionGroupPopup(title, actionGroup, dataContext, selectionAidMethod === JBPopupFactory.ActionSelectionAid.NUMBERING || selectionAidMethod === JBPopupFactory.ActionSelectionAid.ALPHA_NUMBERING, selectionAidMethod === JBPopupFactory.ActionSelectionAid.ALPHA_NUMBERING, showDisabledActions, selectionAidMethod === JBPopupFactory.ActionSelectionAid.MNEMONICS, null, -1)
  }

  override fun createActionGroupPopup(title: String?, actionGroup: ActionGroup, dataContext: DataContext, selectionAidMethod: JBPopupFactory.ActionSelectionAid?, showDisabledActions: Boolean, actionPlace: String?): ListPopup {
    return createActionGroupPopup(title, actionGroup, dataContext, selectionAidMethod === JBPopupFactory.ActionSelectionAid.NUMBERING || selectionAidMethod === JBPopupFactory.ActionSelectionAid.ALPHA_NUMBERING, selectionAidMethod === JBPopupFactory.ActionSelectionAid.ALPHA_NUMBERING, showDisabledActions, selectionAidMethod === JBPopupFactory.ActionSelectionAid.MNEMONICS, null, -1, null, actionPlace)
  }

  override fun createActionGroupPopup(title: String?, actionGroup: ActionGroup, dataContext: DataContext, selectionAidMethod: JBPopupFactory.ActionSelectionAid?, showDisabledActions: Boolean, disposeCallback: Runnable?, maxRowCount: Int): ListPopup {
    return createActionGroupPopup(title, actionGroup, dataContext, selectionAidMethod === JBPopupFactory.ActionSelectionAid.NUMBERING || selectionAidMethod === JBPopupFactory.ActionSelectionAid.ALPHA_NUMBERING, selectionAidMethod === JBPopupFactory.ActionSelectionAid.ALPHA_NUMBERING, showDisabledActions, selectionAidMethod === JBPopupFactory.ActionSelectionAid.MNEMONICS, disposeCallback, maxRowCount)
  }

  override fun createActionGroupPopup(title: String?, actionGroup: ActionGroup, dataContext: DataContext, showNumbers: Boolean, showDisabledActions: Boolean, honorActionMnemonics: Boolean, disposeCallback: Runnable?, maxRowCount: Int, preselectActionCondition: Condition<AnAction>?): ListPopup {
    return createActionGroupPopup(title, actionGroup, dataContext, showNumbers, true, showDisabledActions, honorActionMnemonics, disposeCallback, maxRowCount, preselectActionCondition, null)
  }

  private fun createActionGroupPopup(title: String?, actionGroup: ActionGroup, dataContext: DataContext, showNumbers: Boolean, useAlphaAsNumbers: Boolean, showDisabledActions: Boolean, honorActionMnemonics: Boolean, disposeCallback: Runnable?, maxRowCount: Int): ListPopup {
    return createActionGroupPopup(title, actionGroup, dataContext, showNumbers, useAlphaAsNumbers, showDisabledActions, honorActionMnemonics, disposeCallback, maxRowCount, null, null)
  }

  public fun createActionGroupPopup(title: String?, actionGroup: ActionGroup, dataContext: DataContext, showNumbers: Boolean, showDisabledActions: Boolean, honorActionMnemonics: Boolean, disposeCallback: Runnable, maxRowCount: Int): ListPopup {
    return createActionGroupPopup(title, actionGroup, dataContext, showNumbers, showDisabledActions, honorActionMnemonics, disposeCallback, maxRowCount, null)
  }

  private fun createActionGroupPopup(title: String?, actionGroup: ActionGroup, dataContext: DataContext, showNumbers: Boolean, useAlphaAsNumbers: Boolean, showDisabledActions: Boolean, honorActionMnemonics: Boolean, disposeCallback: Runnable?, maxRowCount: Int, preselectActionCondition: Condition<AnAction>?, actionPlace: String?): ListPopup {
    return ActionGroupPopup(title, actionGroup, dataContext, showNumbers, useAlphaAsNumbers, showDisabledActions, honorActionMnemonics, disposeCallback, maxRowCount, preselectActionCondition, actionPlace)
  }


  class ActionGroupPopup(title: String?, actionGroup: ActionGroup, dataContext: DataContext, showNumbers: Boolean, useAlphaAsNumbers: Boolean, showDisabledActions: Boolean, honorActionMnemonics: Boolean, disposeCallback: Runnable?, maxRowCount: Int, preselectActionCondition: Condition<AnAction>?, actionPlace: String?) :
      ModelListPopup(ActionGroupPopup.createStep(title, actionGroup, dataContext, showNumbers, useAlphaAsNumbers, showDisabledActions, honorActionMnemonics, preselectActionCondition, actionPlace) as ListPopupStep<Any>,
          ReactiveModel.current()!!, Path("popup")) {

    companion object {
      private fun itemsHaveMnemonics(items: List<PopupFactoryImpl.ActionItem>): Boolean {
        for (item in items) {
          if (item.getAction().getTemplatePresentation().getMnemonic() != 0) return true
        }

        return false
      }

      private fun createStep(title: String?, actionGroup: ActionGroup, dataContext: DataContext, showNumbers: Boolean, useAlphaAsNumbers: Boolean, showDisabledActions: Boolean, honorActionMnemonics: Boolean, preselectActionCondition: Condition<AnAction>?, actionPlace: String?): ActionPopupStep {

//        LOG.assertTrue(component != null, "dataContext has no component for new ListPopupStep")

        val builder = ActionStepBuilder(dataContext, showNumbers, useAlphaAsNumbers, showDisabledActions, honorActionMnemonics)
        if (actionPlace != null) {
          builder.setActionPlace(actionPlace)
        }
        builder.buildGroup(actionGroup)
        val items = builder.getItems()

        return ActionPopupStep(items, title, dataContext, showNumbers || honorActionMnemonics && itemsHaveMnemonics(items), preselectActionCondition, false, showDisabledActions)
      }
    }

    init {
      addListSelectionListener(object : ListSelectionListener {
        override fun valueChanged(e: ListSelectionEvent) {
          val list = e.getSource() as JList<Any>
          val actionItem = list.getSelectedValue() as PopupFactoryImpl.ActionItem ?: return
          val action = actionItem!!.getAction()
          val presentation = Presentation()
          presentation.setDescription(action.getTemplatePresentation().getDescription())
          val actualActionPlace = actionPlace ?: ActionPlaces.UNKNOWN
          val actionEvent = AnActionEvent(null, dataContext, actualActionPlace, presentation, ActionManager.getInstance(), 0)
          actionEvent.setInjectedContext(action.isInInjectedContext())
          ActionUtil.performDumbAwareUpdate(action, actionEvent, false)
//          ActionMenu.showDescriptionInStatusBar(true, myComponent, presentation.getDescription())
        }
      })

    }
  }

  override fun createWizardStep(step: PopupStep<*>): ListPopup {
    println("step = [${step}]")
    throw UnsupportedOperationException()
  }

  override fun createListPopup(step: ListPopupStep<*>): ListPopup {
    println("step = [${step}]")
    val reactiveModel = ReactiveModel.current()
    if (reactiveModel != null) {
      return ModelListPopup(step as ListPopupStep<Any>, reactiveModel, Path("popup"))
    }
    return ListPopupImpl(step)
  }

  override fun createListPopup(step: ListPopupStep<*>, maxRowCount: Int): ListPopup {
    println("step = [${step}], maxRowCount = [${maxRowCount}]")
    throw UnsupportedOperationException()
  }

  override fun createTree(parent: JBPopup?, step: TreePopupStep<*>, parentValue: Any?): TreePopup {
    println("parent = [${parent}], step = [${step}], parentValue = [${parentValue}]")
    throw UnsupportedOperationException()
  }

  override fun createTree(step: TreePopupStep<*>): TreePopup {
    println("step = [${step}]")
    throw UnsupportedOperationException()
  }

  override fun createComponentPopupBuilder(content: JComponent, preferableFocusComponent: JComponent?): ComponentPopupBuilder {
    println("content = [${content}], preferableFocusComponent = [${preferableFocusComponent}]")
    throw UnsupportedOperationException()
  }

  override fun guessBestPopupLocation(dataContext: DataContext): RelativePoint {
    println("dataContext = [${dataContext}]")
    throw UnsupportedOperationException()
  }

  override fun guessBestPopupLocation(editor: Editor): RelativePoint {
    println("editor = [${editor}]")
    return RelativePoint(Point())
  }

  override fun isBestPopupLocationVisible(editor: Editor): Boolean {
    println("editor = [${editor}]")
    throw UnsupportedOperationException()
  }

  override fun getCenterOf(container: JComponent?, content: JComponent?): Point? {
    println("container = [${container}], content = [${content}]")
    throw UnsupportedOperationException()
  }

  override fun getChildPopups(parent: Component): MutableList<JBPopup> {
    println("parent = [${parent}]")
    return delegate.getChildPopups(parent)
  }

  override fun isPopupActive(): Boolean {
    println("")
    throw UnsupportedOperationException()
  }

  override fun createBalloonBuilder(content: JComponent): BalloonBuilder {
    println("content = [${content}]")
    throw UnsupportedOperationException()
  }

  override fun createDialogBalloonBuilder(content: JComponent, title: String?): BalloonBuilder {
    println("content = [${content}], title = [${title}]")
    throw UnsupportedOperationException()
  }

  override fun createHtmlTextBalloonBuilder(htmlContent: String, icon: Icon?, fillColor: Color?, listener: HyperlinkListener?): BalloonBuilder {
    println("htmlContent = [${htmlContent}], icon = [${icon}], fillColor = [${fillColor}], listener = [${listener}]")
    throw UnsupportedOperationException()
  }

  override fun createHtmlTextBalloonBuilder(htmlContent: String, messageType: MessageType?, listener: HyperlinkListener?): BalloonBuilder {
    println("htmlContent = [${htmlContent}], messageType = [${messageType}], listener = [${listener}]")
    throw UnsupportedOperationException()
  }

  override fun createMessage(text: String?): JBPopup {
    println("text = [${text}]")
    throw UnsupportedOperationException()
  }

  override fun getParentBalloonFor(c: Component?): Balloon? {
    return null;
  }

}
