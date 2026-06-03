// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet",
               "ReplacePutWithAssignment",
               "ReplaceJavaStaticMethodWithKotlinAnalog",
               "OVERRIDE_DEPRECATION",
               "RemoveRedundantQualifierName")

package com.intellij.openapi.actionSystem.impl

import com.intellij.DynamicBundle
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.AbbreviationManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionStub
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Anchor
import com.intellij.openapi.actionSystem.Constraints
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.DefaultCompactActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectType
import com.intellij.platform.pluginSystem.parser.impl.elements.ActionElement.ActionDescriptorAction
import com.intellij.platform.pluginSystem.parser.impl.elements.ActionElement.ActionElementGroup
import com.intellij.platform.pluginSystem.parser.impl.elements.ActionElement.ActionElementName
import com.intellij.util.xml.dom.XmlElement
import java.util.ResourceBundle
import java.util.concurrent.CancellationException

internal class ActionPluginRegistrar {
  private val state = ActionPluginRegistrarState()

  fun registerActions(
    descriptors: Sequence<IdeaPluginDescriptorImpl>,
    keymapToOperations: MutableMap<String, MutableList<KeymapShortcutOperation>>,
    actionRegistrar: ActionRegistrar,
  ) {
    registerActionsImpl(state = state,
                        descriptors = descriptors,
                        keymapToOperations = keymapToOperations,
                        actionRegistrar = actionRegistrar)
  }

  fun unloadActions(
    module: IdeaPluginDescriptorImpl,
    actionRegistrar: PostInitActionRegistrar,
    unregisterAction: (String) -> Unit,
    replaceAction: (String, AnAction) -> Unit,
  ) {
    unloadActionsImpl(state = state,
                      module = module,
                      actionRegistrar = actionRegistrar,
                      unregisterAction = unregisterAction,
                      replaceAction = replaceAction)
  }
}

private class ActionPluginRegistrarState {
  private val notRegisteredInternalActionIds = HashSet<String>()
  private var anonymousGroupIdCounter = 0

  fun rememberNotRegisteredInternalActionId(id: String) {
    notRegisteredInternalActionIds.add(id)
  }

  fun wasInternalActionSkipped(id: String): Boolean = notRegisteredInternalActionIds.contains(id)

  fun nextAnonymousGroupId(): String = "<anonymous-group-${anonymousGroupIdCounter++}>"
}

private fun registerActionsImpl(
  state: ActionPluginRegistrarState,
  descriptors: Sequence<IdeaPluginDescriptorImpl>,
  keymapToOperations: MutableMap<String, MutableList<KeymapShortcutOperation>>,
  actionRegistrar: ActionRegistrar,
) {
  for (module in descriptors) {
    registerPluginActions(state = state, module = module, keymapToOperations = keymapToOperations, actionRegistrar = actionRegistrar)
  }
}

@Suppress("DEPRECATION")
private fun registerPluginActions(
  state: ActionPluginRegistrarState,
  module: IdeaPluginDescriptorImpl,
  keymapToOperations: MutableMap<String, MutableList<KeymapShortcutOperation>>,
  actionRegistrar: ActionRegistrar,
) {
  val elements = module.actions
  if (elements.isEmpty()) {
    return
  }

  val startTime = System.nanoTime()
  for (descriptor in elements) {
    val bundleName = descriptor.resourceBundle
                     ?: if (PluginManagerCore.CORE_ID == module.pluginId) "messages.ActionsBundle" else module.resourceBundleBaseName
    val element = descriptor.element

    val bundleSupplier = {
      bundleName?.let {
        DynamicBundle.getResourceBundle(module.classLoader, bundleName)
      }
    }

    when (descriptor) {
      is ActionDescriptorAction -> {
        processActionElement(state = state,
                             className = descriptor.className,
                             isInternal = descriptor.isInternal,
                             element = element,
                             actionRegistrar = actionRegistrar,
                             module = module,
                             bundleSupplier = bundleSupplier,
                             keymapToOperations = keymapToOperations,
                             classLoader = module.classLoader)
      }
      is ActionElementGroup -> {
        processGroupElement(state = state,
                            className = descriptor.className,
                            id = descriptor.id,
                            element = element,
                            actionRegistrar = actionRegistrar,
                            module = module,
                            bundleSupplier = bundleSupplier,
                            keymapToOperations = keymapToOperations,
                            classLoader = module.classLoader)
      }
      else -> {
        when (descriptor.name) {
          ActionElementName.separator -> processSeparatorNode(parentGroup = null,
                                                              element = element,
                                                              module = module,
                                                              bundleSupplier = bundleSupplier,
                                                              actionRegistrar = actionRegistrar)
          ActionElementName.reference -> processReferenceNode(state = state,
                                                              element = element,
                                                              module = module,
                                                              bundleSupplier = bundleSupplier,
                                                              actionRegistrar = actionRegistrar)
          ActionElementName.unregister -> processUnregisterNode(element = element, module = module, actionRegistrar = actionRegistrar)
          ActionElementName.prohibit -> processProhibitNode(element = element, module = module, actionRegistrar = actionRegistrar)
          else -> actionManagerImplLog.error("${descriptor.name} is unknown")
        }
      }
    }
  }
  StartUpMeasurer.addPluginCost(module.pluginId.idString, "Actions", System.nanoTime() - startTime)
}

/**
 * @return instance of ActionGroup or ActionStub. The method never returns real subclasses of `AnAction`.
 */
private fun processActionElement(
  state: ActionPluginRegistrarState,
  className: String,
  isInternal: Boolean,
  element: XmlElement,
  actionRegistrar: ActionRegistrar,
  module: IdeaPluginDescriptorImpl,
  bundleSupplier: () -> ResourceBundle?,
  keymapToOperations: MutableMap<String, MutableList<KeymapShortcutOperation>>,
  classLoader: ClassLoader,
): AnAction? {
  // read ID and register a loaded action
  val id = obtainActionId(element = element, className = className)
  if (actionRegistrar.state.isActionProhibited(id)) {
    return null
  }

  if (isInternal && !ApplicationManager.getApplication().isInternal) {
    state.rememberNotRegisteredInternalActionId(id)
    return null
  }

  val iconPath = element.attributes.get(ICON_ATTR_NAME)
  val projectType = element.attributes.get(PROJECT_TYPE)
  val textValue = element.attributes.get(TEXT_ATTR_NAME)

  @Suppress("HardCodedStringLiteral")
  val descriptionValue = element.attributes.get(DESCRIPTION)
  val stub = ActionStub(className, id, module, iconPath, ProjectType.create(projectType)) {
    val presentation = Presentation.newTemplatePresentation()
    presentation.setText {
      computeActionText(bundleSupplier = bundleSupplier,
                        id = id,
                        elementType = ACTION_ELEMENT_NAME,
                        textValue = textValue,
                        classLoader = classLoader)
    }
    if (bundleSupplier() == null) {
      presentation.description = descriptionValue
    }
    else {
      presentation.setDescription {
        computeDescription(bundleSupplier = bundleSupplier,
                           id = id,
                           elementType = ACTION_ELEMENT_NAME,
                           descriptionValue = descriptionValue,
                           classLoader = classLoader)
      }
    }
    presentation
  }

  // Stage links and key bindings until the action is registered successfully.
  val pendingEffects = PendingActionRegistrationEffects()
  for (child in element.children) {
    when (child.name) {
      ADD_TO_GROUP_ELEMENT_NAME -> pendingEffects.addToGroup(child)
      "keyboard-shortcut" -> processKeyboardShortcutNode(element = child,
                                                         actionId = id,
                                                         module = module,
                                                         keymapToOperations = pendingEffects.keymapToOperations)
      "keyboard-gesture-shortcut" -> processKeyboardGestureShortcutNode(element = child,
                                                                        actionId = id,
                                                                        module = module,
                                                                        keymapToOperations = pendingEffects.keymapToOperations)
      "mouse-shortcut" -> processMouseShortcutNode(element = child,
                                                   actionId = id,
                                                   module = module,
                                                   keymapToOperations = pendingEffects.keymapToOperations)
      "abbreviation" -> pendingEffects.addAbbreviation(child)
      OVERRIDE_TEXT_ELEMENT_NAME -> processOverrideTextNode(action = stub,
                                                            id = stub.id,
                                                            element = child,
                                                            module = module,
                                                            bundleSupplier = bundleSupplier)
      SYNONYM_ELEMENT_NAME -> processSynonymNode(action = stub, element = child, module = module, bundleSupplier = bundleSupplier)
      else -> {
        reportActionManagerError(module, "unexpected name of element \"${child.name}\"")
        return null
      }
    }
  }

  pendingEffects.shortcutOfActionId = element.attributes.get(USE_SHORTCUT_OF_ATTR_NAME)
  if (!registerOrReplaceActionInner(element = element, id = id, action = stub, plugin = module, actionRegistrar = actionRegistrar)) {
    return null
  }

  pendingEffects.publishKeymapOperations(keymapToOperations)
  pendingEffects.shortcutOfActionId?.let {
    actionRegistrar.bindShortcuts(sourceActionId = it, targetActionId = id)
  }
  pendingEffects.publishAbbreviations(id)
  pendingEffects.publishAddToGroupNodes(action = stub, module = module, actionRegistrar = actionRegistrar)
  return stub
}

private class PendingActionRegistrationEffects {
  val keymapToOperations: MutableMap<String, MutableList<KeymapShortcutOperation>> = HashMap()
  var shortcutOfActionId: String? = null
  private val addToGroupNodes = ArrayList<XmlElement>()
  private val abbreviationNodes = ArrayList<XmlElement>()

  fun addToGroup(element: XmlElement) {
    addToGroupNodes.add(element)
  }

  fun addAbbreviation(element: XmlElement) {
    abbreviationNodes.add(element)
  }

  fun publishKeymapOperations(target: MutableMap<String, MutableList<KeymapShortcutOperation>>) {
    for ((keymap, operations) in keymapToOperations) {
      target.computeIfAbsent(keymap) { ArrayList() }.addAll(operations)
    }
  }

  fun publishAbbreviations(id: String) {
    for (element in abbreviationNodes) {
      processAbbreviationNode(e = element, id = id)
    }
  }

  fun publishAddToGroupNodes(action: AnAction, module: IdeaPluginDescriptor, actionRegistrar: ActionRegistrar) {
    for (element in addToGroupNodes) {
      processAddToGroupNode(action = action,
                            element = element,
                            module = module,
                            secondary = isSecondary(element),
                            actionRegistrar = actionRegistrar)
    }
  }
}

private fun processGroupElement(
  state: ActionPluginRegistrarState,
  className: String?,
  id: String?,
  element: XmlElement,
  module: IdeaPluginDescriptorImpl,
  bundleSupplier: () -> ResourceBundle?,
  keymapToOperations: MutableMap<String, MutableList<KeymapShortcutOperation>>,
  actionRegistrar: ActionRegistrar,
  classLoader: ClassLoader,
): AnAction? {
  try {
    if (id != null && actionRegistrar.state.isActionProhibited(id)) {
      return null
    }

    // icon
    val iconPath = element.attributes.get(ICON_ATTR_NAME)

    val group: ActionGroup
    var customClass = false
    if (className == null || className == DEFAULT_ACTION_GROUP_CLASS_NAME) {
      if (id == null || iconPath == null) {
        group = DefaultActionGroup()
      }
      else {
        group = ActionGroupStub(id = id, actionClass = DEFAULT_ACTION_GROUP_CLASS_NAME, plugin = module, iconPath = iconPath)
      }
    }
    else if (className == DefaultCompactActionGroup::class.java.name) {
      group = DefaultCompactActionGroup()
    }
    else if (id == null) {
      val obj = ApplicationManager.getApplication().instantiateClass<Any>(className, module)
      if (obj !is ActionGroup) {
        reportActionManagerError(module, "class with name \"$className\" should be instance of ${ActionGroup::class.java.name}")
        return null
      }

      if (element.children.size != element.count(ADD_TO_GROUP_ELEMENT_NAME)) {
        if (obj !is DefaultActionGroup) {
          reportActionManagerError(module, "class with name \"$className\" should be instance of $DEFAULT_ACTION_GROUP_CLASS_NAME" +
                                           " because there are children specified")
          return null
        }
      }
      customClass = true
      group = obj
    }
    else {
      group = ActionGroupStub(id = id, actionClass = className, plugin = module, iconPath = iconPath)
      customClass = true
    }

    // read ID and register loaded group
    if (element.attributes.get(INTERNAL_ATTR_NAME).toBoolean() && !ApplicationManager.getApplication().isInternal) {
      state.rememberNotRegisteredInternalActionId(id!!)
      return null
    }

    @Suppress("NAME_SHADOWING")
    val id = id ?: state.nextAnonymousGroupId()
    val popup = element.attributes.get("popup")
    if (popup != null) {
      group.isPopup = popup.toBoolean()
      if (group is ActionGroupStub) {
        group.popupDefinedInXml = true
      }
    }

    if (!registerOrReplaceActionInner(element = element, id = id, action = group, plugin = module, actionRegistrar = actionRegistrar)) {
      return null
    }

    configureGroupDescriptionAndIcon(presentation = group.templatePresentation,
                                     description = element.attributes.get(DESCRIPTION),
                                     textValue = element.attributes.get(TEXT_ATTR_NAME),
                                     group = group,
                                     bundleSupplier = bundleSupplier,
                                     id = id,
                                     classLoader = classLoader,
                                     iconPath = iconPath,
                                     module = module,
                                     className = className)

    val searchable = element.attributes.get("searchable")
    if (searchable != null) {
      group.isSearchable = searchable.toBoolean()
    }
    val shortcutOfActionId = element.attributes.get(USE_SHORTCUT_OF_ATTR_NAME)
    if (customClass && shortcutOfActionId != null) {
      actionRegistrar.bindShortcuts(sourceActionId = shortcutOfActionId, targetActionId = id)
    }

    // Process all group's children. There are other groups, actions, references and links
    for (child in element.children) {
      when (child.name) {
        ACTION_ELEMENT_NAME -> {
          val childClassName = child.attributes.get(CLASS_ATTR_NAME)
          if (childClassName.isNullOrEmpty()) {
            reportActionManagerError(module = module, message = "action element should have specified \"class\" attribute")
          }
          else {
            val action = processActionElement(state = state,
                                              className = childClassName,
                                              isInternal = child.attributes.get(INTERNAL_ATTR_NAME).toBoolean(),
                                              element = child,
                                              module = module,
                                              bundleSupplier = bundleSupplier,
                                              actionRegistrar = actionRegistrar,
                                              keymapToOperations = keymapToOperations,
                                              classLoader = classLoader)
            if (action != null) {
              addToGroup(group = group,
                         action = action,
                         constraints = Constraints.LAST,
                         module = module,
                         state = actionRegistrar.state,
                         secondary = isSecondary(child))
            }
          }
        }
        SEPARATOR_ELEMENT_NAME -> {
          processSeparatorNode(parentGroup = group as DefaultActionGroup,
                               element = child,
                               module = module,
                               bundleSupplier = bundleSupplier,
                               actionRegistrar = actionRegistrar)
        }
        GROUP_ELEMENT_NAME -> {
          var childClassName = child.attributes.get(CLASS_ATTR_NAME)
          if (childClassName == null) {
            // use a default group if class isn't specified
            childClassName = if ("true" == child.attributes.get("compact")) {
              DefaultCompactActionGroup::class.java.name
            }
            else {
              DEFAULT_ACTION_GROUP_CLASS_NAME
            }
          }
          val childId = child.attributes.get(ID_ATTR_NAME)
          if (childId != null && childId.isEmpty()) {
            reportActionManagerError(module, "ID of the group cannot be an empty string")
          }
          else {
            val action = processGroupElement(state = state,
                                             className = childClassName,
                                             id = childId,
                                             element = child,
                                             module = module,
                                             bundleSupplier = bundleSupplier,
                                             keymapToOperations = keymapToOperations,
                                             actionRegistrar = actionRegistrar,
                                             classLoader = classLoader)
            if (action != null) {
              addToGroup(group = group,
                         action = action,
                         constraints = Constraints.LAST,
                         module = module,
                         state = actionRegistrar.state,
                         secondary = false)
            }
          }
        }
        ADD_TO_GROUP_ELEMENT_NAME -> {
          processAddToGroupNode(action = group,
                                element = child,
                                module = module,
                                secondary = isSecondary(child),
                                actionRegistrar = actionRegistrar)
        }
        REFERENCE_ELEMENT_NAME -> {
          val action = processReferenceElement(state = state, element = child, module = module, actionRegistrar = actionRegistrar)
          if (action != null) {
            addToGroup(group = group,
                       action = action,
                       constraints = Constraints.LAST,
                       module = module,
                       state = actionRegistrar.state,
                       secondary = isSecondary(child))
          }
        }
        OVERRIDE_TEXT_ELEMENT_NAME -> processOverrideTextNode(action = group,
                                                              id = id,
                                                              element = child,
                                                              module = module,
                                                              bundleSupplier = bundleSupplier)
        else -> {
          reportActionManagerError(module, "unexpected name of element \"${child.name}\n")
          return null
        }
      }
    }
    return group
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Exception) {
    reportActionManagerError(module = module, message = "cannot create class \"$className\"", cause = e)
    return null
  }
}

private fun processReferenceNode(
  state: ActionPluginRegistrarState,
  element: XmlElement,
  module: IdeaPluginDescriptor,
  bundleSupplier: () -> ResourceBundle?,
  actionRegistrar: ActionRegistrar,
) {
  val action = processReferenceElement(state = state, element = element, module = module, actionRegistrar = actionRegistrar) ?: return
  for (child in element.children) {
    if (ADD_TO_GROUP_ELEMENT_NAME == child.name) {
      processAddToGroupNode(action = action,
                            element = child,
                            module = module,
                            secondary = isSecondary(child),
                            actionRegistrar = actionRegistrar)
    }
    else if (SYNONYM_ELEMENT_NAME == child.name) {
      processSynonymNode(action = action, element = child, module = module, bundleSupplier = bundleSupplier)
    }
  }
}

/**
 * @param element description of a link
 */
private fun processAddToGroupNode(
  action: AnAction,
  element: XmlElement,
  module: IdeaPluginDescriptor,
  secondary: Boolean,
  actionRegistrar: ActionRegistrar,
) {
  val name = if (action is ActionStub) action.className else action.javaClass.name
  val id = if (action is ActionStub) action.id else actionRegistrar.state.getActionId(action)!!
  val actionName = "$name ($id)"

  // parent group
  val parentGroup = getParentGroup(groupId = element.attributes.get(GROUP_ID_ATTR_NAME),
                                   actionName = actionName,
                                   module = module,
                                   actionRegistrar = actionRegistrar) ?: return

  // anchor attribute
  val anchor = parseAnchor(element.attributes.get("anchor"), actionName, module) ?: return
  val relativeToActionId = element.attributes.get("relative-to-action")
  if ((Anchor.BEFORE == anchor || Anchor.AFTER == anchor) && relativeToActionId == null) {
    reportActionManagerError(module, "$actionName: \"relative-to-action\" cannot be null if anchor is \"after\" or \"before\"")
    return
  }

  addToGroup(group = parentGroup,
             action = action,
             state = actionRegistrar.state,
             constraints = Constraints(anchor, relativeToActionId),
             module = module,
             secondary = secondary)
}

private fun getParentGroup(
  groupId: String?,
  actionName: String?,
  module: IdeaPluginDescriptor,
  actionRegistrar: ActionRegistrar,
): DefaultActionGroup? {
  if (groupId.isNullOrEmpty()) {
    reportActionManagerError(module, "$actionName: attribute \"group-id\" should be defined")
    return null
  }

  val parentGroup = getAction(id = groupId, canReturnStub = true, actionRegistrar = actionRegistrar)
  if (parentGroup == null) {
    reportActionManagerError(module = module,
                             message = "$actionName: group with id \"$groupId\" isn't registered so the action won't be added to it; the action can be invoked via \"Find Action\"",
                             cause = null)
    return null
  }
  if (parentGroup !is DefaultActionGroup) {
    reportActionManagerError(module,
                             "$actionName: group with id \"$groupId\" should be instance of ${DefaultActionGroup::class.java.name}" +
                             " but was ${parentGroup.javaClass}")
    return null
  }
  return parentGroup
}

/**
 * @param parentGroup group which is the parent of the separator. It can be `null` in that
 * case separator will be added to a group described in the <add-to-group ...> sub element.
 * @param element     XML element which represent separator. `</add-to-group>`
 */
@Suppress("HardCodedStringLiteral")
private fun processSeparatorNode(
  parentGroup: DefaultActionGroup?,
  element: XmlElement,
  module: IdeaPluginDescriptor,
  bundleSupplier: () -> ResourceBundle?,
  actionRegistrar: ActionRegistrar,
) {
  val text = element.attributes.get(TEXT_ATTR_NAME)
  val key = element.attributes.get(KEY_ATTR_NAME)
  val separator = when {
    text != null -> Separator(text)
    key != null -> createSeparator(bundleSupplier, key)
    else -> Separator.getInstance()
  }
  if (parentGroup != null) {
    addToGroup(group = parentGroup,
               action = separator,
               constraints = Constraints.LAST,
               module = module,
               state = actionRegistrar.state,
               secondary = false)
  }
  // try to find inner <add-to-parent...> tag
  for (child in element.children) {
    if (ADD_TO_GROUP_ELEMENT_NAME == child.name) {
      processAddToGroupNode(action = separator,
                            element = child,
                            module = module,
                            secondary = isSecondary(child),
                            actionRegistrar = actionRegistrar)
    }
  }
}

private fun processProhibitNode(element: XmlElement, module: IdeaPluginDescriptor, actionRegistrar: ActionRegistrar) {
  val id = element.attributes.get(ID_ATTR_NAME)
  if (id == null) {
    reportActionManagerError(module, "'id' attribute is required for 'unregister' elements")
    return
  }
  prohibitAction(id, actionRegistrar)
}

private fun prohibitAction(actionId: String, actionRegistrar: ActionRegistrar) {
  val state = actionRegistrar.state
  state.prohibitAction(actionId)
  val action = getAction(
    id = actionId,
    canReturnStub = false,
    actionRegistrar = actionRegistrar
  )
  if (action != null) {
    if (actionRegistrar.isPostInit) {
      AbbreviationManager.getInstance().removeAllAbbreviations(actionId)
    }
    state.withLock {
      unregisterAction(actionId = actionId, actionRegistrar = actionRegistrar)
    }
  }
}

private fun processUnregisterNode(element: XmlElement, module: IdeaPluginDescriptor, actionRegistrar: ActionRegistrar) {
  val id = element.attributes.get(ID_ATTR_NAME)
  if (id == null) {
    reportActionManagerError(module, "'id' attribute is required for 'unregister' elements")
    return
  }

  val action = getAction(id = id, canReturnStub = false, actionRegistrar = actionRegistrar)
  if (action == null) {
    reportActionManagerError(module, "Trying to unregister non-existing action $id")
    return
  }

  AbbreviationManager.getInstance().removeAllAbbreviations(id)
  unregisterAction(actionId = id, actionRegistrar = actionRegistrar)
}

private fun processReferenceElement(
  state: ActionPluginRegistrarState,
  element: XmlElement,
  module: IdeaPluginDescriptor,
  actionRegistrar: ActionRegistrar,
): AnAction? {
  val ref = getReferenceActionId(element)
  if (ref.isNullOrEmpty()) {
    reportActionManagerError(module, "ID of reference element should be defined", null)
    return null
  }

  if (actionRegistrar.state.isActionProhibited(ref)) {
    return null
  }

  val action = getAction(id = ref, canReturnStub = true, actionRegistrar = actionRegistrar)
  if (action == null) {
    if (!state.wasInternalActionSkipped(ref)) {
      reportActionManagerError(module, "action specified by reference isn't registered (ID=$ref)", null)
    }
    return null
  }
  return action
}

private fun unloadActionsImpl(
  state: ActionPluginRegistrarState,
  module: IdeaPluginDescriptorImpl,
  actionRegistrar: PostInitActionRegistrar,
  unregisterAction: (String) -> Unit,
  replaceAction: (String, AnAction) -> Unit,
) {
  val descriptors = module.actions
  for (i in descriptors.indices.reversed()) {
    val descriptor = descriptors[i]
    val element = descriptor.element
    when (descriptor.name) {
      ActionElementName.action -> unloadActionElement(element, actionRegistrar, unregisterAction, replaceAction)
      ActionElementName.group -> unloadGroupElement(element, actionRegistrar, unregisterAction, replaceAction)
      ActionElementName.reference -> {
        val action = processReferenceElement(state = state, element = element, module = module, actionRegistrar = actionRegistrar) ?: return
        val actionId = getReferenceActionId(element)
        for ((name, attributes) in element.children) {
          if (name != ADD_TO_GROUP_ELEMENT_NAME) {
            continue
          }

          val groupId = attributes.get(GROUP_ID_ATTR_NAME)
          val parentGroup = getParentGroup(groupId = groupId,
                                           actionName = actionId,
                                           module = module,
                                           actionRegistrar = actionRegistrar) ?: return
          removeFromGroup(group = parentGroup,
                          action = action,
                          actionId = actionId,
                          groupId = groupId,
                          state = actionRegistrar.state)
        }
      }
      else -> {
      }
    }
  }
}

private fun unloadGroupElement(
  element: XmlElement,
  actionRegistrar: PostInitActionRegistrar,
  unregisterAction: (String) -> Unit,
  replaceAction: (String, AnAction) -> Unit,
) {
  val id = element.attributes.get(ID_ATTR_NAME) ?: throw IllegalStateException("Cannot unload groups with no ID")
  for (groupChild in element.children) {
    if (groupChild.name == ACTION_ELEMENT_NAME) {
      unloadActionElement(groupChild, actionRegistrar, unregisterAction, replaceAction)
    }
    else if (groupChild.name == GROUP_ELEMENT_NAME) {
      unloadGroupElement(groupChild, actionRegistrar, unregisterAction, replaceAction)
    }
  }
  unregisterAction(id)
}

private fun unloadActionElement(
  element: XmlElement,
  actionRegistrar: PostInitActionRegistrar,
  unregisterAction: (String) -> Unit,
  replaceAction: (String, AnAction) -> Unit,
) {
  val className = element.attributes.get(CLASS_ATTR_NAME)
  val overrides = element.attributes.get(OVERRIDES_ATTR_NAME).toBoolean()
  val id = obtainActionId(element = element, className = className)
  if (overrides) {
    val baseAction = actionRegistrar.state.getBaseAction(id)
    if (baseAction != null) {
      replaceAction(id, baseAction)
      actionRegistrar.state.removeBaseAction(id)
      return
    }
  }
  unregisterAction(id)
}
