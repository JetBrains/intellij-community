// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.AbstractBundle;
import com.intellij.BundleBase;
import com.intellij.DynamicBundle;
import com.intellij.diagnostic.LoadingState;
import com.intellij.diagnostic.PluginException;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.icons.AllIcons;
import com.intellij.ide.ActivityTracker;
import com.intellij.ide.DataManager;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginDependency;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.ui.customization.ActionUrl;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.idea.IdeaLogger;
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionIdProvider;
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsCollectorImpl;
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsEventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventPair;
import com.intellij.internal.statistic.eventLog.events.ObjectEventData;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.ActionPopupMenuListener;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandlerBean;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.keymap.impl.DefaultKeymap;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectType;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.icons.IconLoadMeasurer;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.Timer;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.WindowEvent;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class ActionManagerImpl extends ActionManagerEx implements Disposable {
  private static final ExtensionPointName<ActionConfigurationCustomizer> EP =
    new ExtensionPointName<>("com.intellij.actionConfigurationCustomizer");
  private static final ExtensionPointName<DynamicActionConfigurationCustomizer> DYNAMIC_EP_NAME =
    new ExtensionPointName<>("com.intellij.dynamicActionConfigurationCustomizer");
  private static final ExtensionPointName<EditorActionHandlerBean> EDITOR_ACTION_HANDLER_EP =
    new ExtensionPointName<>("com.intellij.editorActionHandler");

  private static final String ACTION_ELEMENT_NAME = "action";
  private static final String GROUP_ELEMENT_NAME = "group";
  private static final String CLASS_ATTR_NAME = "class";
  private static final String ID_ATTR_NAME = "id";
  private static final String INTERNAL_ATTR_NAME = "internal";
  private static final String ICON_ATTR_NAME = "icon";
  private static final String ADD_TO_GROUP_ELEMENT_NAME = "add-to-group";
  private static final String SHORTCUT_ELEMENT_NAME = "keyboard-shortcut";
  private static final String MOUSE_SHORTCUT_ELEMENT_NAME = "mouse-shortcut";
  private static final String DESCRIPTION = "description";
  private static final String TEXT_ATTR_NAME = "text";
  private static final String KEY_ATTR_NAME = "key";
  private static final String POPUP_ATTR_NAME = "popup";
  private static final String COMPACT_ATTR_NAME = "compact";
  private static final String SEARCHABLE_ATTR_NAME = "searchable";
  private static final String SEPARATOR_ELEMENT_NAME = "separator";
  private static final String REFERENCE_ELEMENT_NAME = "reference";
  private static final String ABBREVIATION_ELEMENT_NAME = "abbreviation";
  private static final String GROUPID_ATTR_NAME = "group-id";
  private static final String ANCHOR_ELEMENT_NAME = "anchor";
  private static final String FIRST = "first";
  private static final String LAST = "last";
  private static final String BEFORE = "before";
  private static final String AFTER = "after";
  private static final String SECONDARY = "secondary";
  private static final String RELATIVE_TO_ACTION_ATTR_NAME = "relative-to-action";
  private static final String FIRST_KEYSTROKE_ATTR_NAME = "first-keystroke";
  private static final String SECOND_KEYSTROKE_ATTR_NAME = "second-keystroke";
  private static final String REMOVE_SHORTCUT_ATTR_NAME = "remove";
  private static final String REPLACE_SHORTCUT_ATTR_NAME = "replace-all";
  private static final String KEYMAP_ATTR_NAME = "keymap";
  private static final String KEYSTROKE_ATTR_NAME = "keystroke";
  private static final String REF_ATTR_NAME = "ref";
  private static final String VALUE_ATTR_NAME = "value";
  private static final String ACTIONS_BUNDLE = "messages.ActionsBundle";
  private static final String USE_SHORTCUT_OF_ATTR_NAME = "use-shortcut-of";
  private static final String OVERRIDES_ATTR_NAME = "overrides";
  private static final String KEEP_CONTENT_ATTR_NAME = "keep-content";
  private static final String PROJECT_TYPE = "project-type";
  private static final String UNREGISTER_ELEMENT_NAME = "unregister";
  private static final String PROHIBIT_ELEMENT_NAME = "prohibit";
  private static final String OVERRIDE_TEXT_ELEMENT_NAME = "override-text";
  private static final String SYNONYM_ELEMENT_NAME = "synonym";
  private static final String PLACE_ATTR_NAME = "place";
  private static final String USE_TEXT_OF_PLACE_ATTR_NAME = "use-text-of-place";
  private static final String RESOURCE_BUNDLE_ATTR_NAME = "resource-bundle";

  private static final Logger LOG = Logger.getInstance(ActionManagerImpl.class);
  private static final int DEACTIVATED_TIMER_DELAY = 5000;
  private static final int TIMER_DELAY = 500;
  private static final int UPDATE_DELAY_AFTER_TYPING = 500;

  private final Object myLock = new Object();
  private final Map<String, AnAction> idToAction = CollectionFactory.createSmallMemoryFootprintMap();
  private final MultiMap<PluginId, String> pluginToId = new MultiMap<>();
  private final Object2IntMap<String> idToIndex = new Object2IntOpenHashMap<>();
  private final Set<String> myProhibitedActionIds = new HashSet<>();
  private final Map<Object, String> actionToId = CollectionFactory.createSmallMemoryFootprintMap();
  private final MultiMap<String, String> idToGroupId = new MultiMap<>();
  private final List<String> myNotRegisteredInternalActionIds = new ArrayList<>();
  private final List<AnActionListener> myActionListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final List<ActionPopupMenuListener> myActionPopupMenuListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final List<Object/*ActionPopupMenuImpl|JBPopup*/> myPopups = new ArrayList<>();
  private MyTimer myTimer;
  private int myRegisteredActionsCount;
  private String myLastPreformedActionId;
  private String myPrevPerformedActionId;
  private long myLastTimeEditorWasTypedIn;
  private final Map<OverridingAction, AnAction> myBaseActions = new HashMap<>();
  private int myAnonymousGroupIdCounter;

  ActionManagerImpl() {
    Application app = ApplicationManager.getApplication();
    if (!app.isUnitTestMode()) {
      LoadingState.COMPONENTS_LOADED.checkOccurred();
      if (!app.isHeadlessEnvironment() && !app.isCommandLine()) {
        LOG.assertTrue(!app.isDispatchThread());
      }
    }

    registerActions(PluginManagerCore.getLoadedPlugins(null), true);

    EP.forEachExtensionSafe(customizer -> customizer.customize(this));
    DYNAMIC_EP_NAME.forEachExtensionSafe(customizer -> customizer.registerActions(this));
    DYNAMIC_EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull DynamicActionConfigurationCustomizer extension, @NotNull PluginDescriptor pluginDescriptor) {
        extension.registerActions(ActionManagerImpl.this);
      }

      @Override
      public void extensionRemoved(@NotNull DynamicActionConfigurationCustomizer extension, @NotNull PluginDescriptor pluginDescriptor) {
        extension.unregisterActions(ActionManagerImpl.this);
      }
    }, this);
    EDITOR_ACTION_HANDLER_EP.addChangeListener(this::updateAllHandlers, this);
  }

  @ApiStatus.Internal
  public void registerActions(@NotNull List<IdeaPluginDescriptorImpl> plugins, @SuppressWarnings("unused") boolean initialStartup) {
    KeymapManager keymapManager = Objects.requireNonNull(KeymapManager.getInstance());

    for (IdeaPluginDescriptorImpl plugin : plugins) {
      registerPluginActions(plugin, keymapManager);
      for (PluginDependency pluginDependency : plugin.getPluginDependencies()) {
        IdeaPluginDescriptorImpl subPlugin = pluginDependency.isDisabledOrBroken ? null : pluginDependency.subDescriptor;
        if (subPlugin == null) {
          continue;
        }

        registerPluginActions(subPlugin, keymapManager);
        for (PluginDependency subPluginDependency : subPlugin.getPluginDependencies()) {
          IdeaPluginDescriptorImpl subSubPlugin = subPluginDependency.isDisabledOrBroken ? null : subPluginDependency.subDescriptor;
          if (subSubPlugin != null) {
            registerPluginActions(subSubPlugin, keymapManager);
          }
        }
      }
    }
  }

  private static @NotNull AnActionListener publisher() {
    return ApplicationManager.getApplication().getMessageBus().syncPublisher(AnActionListener.TOPIC);
  }

  static @Nullable AnAction convertStub(@NotNull ActionStub stub) {
    AnAction anAction = instantiate(stub.getClassName(), stub.getPlugin(), AnAction.class);
    if (anAction == null) {
      return null;
    }

    stub.initAction(anAction);
    updateIconFromStub(stub, anAction);
    return anAction;
  }

  private static @Nullable <T> T instantiate(@NotNull String stubClassName, @NotNull PluginDescriptor pluginDescriptor, @NotNull Class<T> expectedClass) {
    Object obj;
    try {
      Class<?> aClass = Class.forName(stubClassName, true, pluginDescriptor.getPluginClassLoader());
      Constructor<?> constructor = aClass.getDeclaredConstructor();
      try {
        constructor.setAccessible(true);
      }
      catch (SecurityException ignored) {
      }
      obj = constructor.newInstance();
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (PluginException e) {
      LOG.error(e);
      return null;
    }
    catch (Throwable e) {
      LOG.error(new PluginException(e, pluginDescriptor.getPluginId()));
      return null;
    }

    if (!expectedClass.isInstance(obj)) {
      LOG.error(new PluginException("class with name '" +
                                    stubClassName + "' must be an instance of '" + expectedClass.getName() + "'; got " + obj, pluginDescriptor.getPluginId()));
      return null;
    }
    //noinspection unchecked
    return (T)obj;
  }

  private static void updateIconFromStub(@NotNull ActionStubBase stub, @NotNull AnAction anAction) {
    String iconPath = stub.getIconPath();
    if (iconPath != null) {
      setIconFromClass(anAction.getClass(), stub.getPlugin(), iconPath, anAction.getTemplatePresentation());
    }
  }

  private static @Nullable ActionGroup convertGroupStub(@NotNull ActionGroupStub stub, @NotNull ActionManager actionManager) {
    IdeaPluginDescriptor plugin = stub.getPlugin();
    ActionGroup group = instantiate(stub.getActionClass(), plugin, ActionGroup.class);
    if (group == null) {
      return null;
    }

    stub.initGroup(group, actionManager);
    updateIconFromStub(stub, group);
    return group;
  }

  private static void processAbbreviationNode(@NotNull Element e, @NotNull String id) {
    String abbr = e.getAttributeValue(VALUE_ATTR_NAME);
    if (!Strings.isEmpty(abbr)) {
      AbbreviationManagerImpl abbreviationManager = (AbbreviationManagerImpl)AbbreviationManager.getInstance();
      abbreviationManager.register(abbr, id, true);
    }
  }

  private static boolean isSecondary(Element element) {
    return "true".equalsIgnoreCase(element.getAttributeValue(SECONDARY));
  }

  private static void setIconFromClass(@Nullable Class<?> actionClass,
                                       @NotNull PluginDescriptor pluginDescriptor,
                                       @NotNull String iconPath,
                                       @NotNull Presentation presentation) {
    long start = StartUpMeasurer.getCurrentTimeIfEnabled();
    Icon icon = IconLoader.findIcon(iconPath, actionClass, pluginDescriptor.getPluginClassLoader(), null, true);
    if (icon == null) {
      reportActionError(pluginDescriptor.getPluginId(), "Icon cannot be found in '" + iconPath + "', action '" + actionClass + "'");
      icon = AllIcons.Nodes.Unknown;
    }
    IconLoadMeasurer.actionIcon.end(start);
    presentation.setIcon(icon);
  }

  @SuppressWarnings("HardCodedStringLiteral")
  private static @NlsActions.ActionDescription String computeDescription(@NotNull ResourceBundle bundle, String id, String elementType, String descriptionValue) {
    String key = elementType + "." + id + ".description";
    return AbstractBundle.messageOrDefault(bundle, key, Strings.notNullize(descriptionValue));
  }

  @SuppressWarnings("HardCodedStringLiteral")
  private static @NlsActions.ActionText String computeActionText(@Nullable ResourceBundle bundle, String id, String elementType, @Nullable String textValue) {
    String defaultValue = Strings.notNullize(textValue);
    return bundle == null ? defaultValue : AbstractBundle.messageOrDefault(bundle, elementType + "." + id + "." + TEXT_ATTR_NAME, defaultValue);
  }

  private static boolean checkRelativeToAction(String relativeToActionId,
                                               @NotNull Anchor anchor,
                                               @NotNull String actionName,
                                               @Nullable PluginId pluginId) {
    if ((Anchor.BEFORE == anchor || Anchor.AFTER == anchor) && relativeToActionId == null) {
      reportActionError(pluginId, actionName + ": \"relative-to-action\" cannot be null if anchor is \"after\" or \"before\"");
      return false;
    }
    return true;
  }

  @Nullable
  private static Anchor parseAnchor(String anchorStr, @Nullable String actionName, @Nullable PluginId pluginId) {
    if (anchorStr == null) {
      return Anchor.LAST;
    }

    if (FIRST.equalsIgnoreCase(anchorStr)) {
      return Anchor.FIRST;
    }
    else if (LAST.equalsIgnoreCase(anchorStr)) {
      return Anchor.LAST;
    }
    else if (BEFORE.equalsIgnoreCase(anchorStr)) {
      return Anchor.BEFORE;
    }
    else if (AFTER.equalsIgnoreCase(anchorStr)) {
      return Anchor.AFTER;
    }
    else {
      reportActionError(pluginId, actionName + ": anchor should be one of the following constants: \"first\", \"last\", \"before\" or \"after\"");
      return null;
    }
  }

  private static void processMouseShortcutNode(Element element, String actionId, PluginId pluginId, @NotNull KeymapManager keymapManager) {
    String keystrokeString = element.getAttributeValue(KEYSTROKE_ATTR_NAME);
    if (keystrokeString == null || keystrokeString.trim().isEmpty()) {
      reportActionError(pluginId, "\"keystroke\" attribute must be specified for action with id=" + actionId);
      return;
    }
    MouseShortcut shortcut;
    try {
      shortcut = KeymapUtil.parseMouseShortcut(keystrokeString);
    }
    catch (Exception ex) {
      reportActionError(pluginId, "\"keystroke\" attribute has invalid value for action with id=" + actionId);
      return;
    }

    String keymapName = element.getAttributeValue(KEYMAP_ATTR_NAME);
    if (keymapName == null || keymapName.isEmpty()) {
      reportActionError(pluginId, "attribute \"keymap\" should be defined");
      return;
    }
    Keymap keymap = keymapManager.getKeymap(keymapName);
    if (keymap == null) {
      reportKeymapNotFoundWarning(pluginId, keymapName);
      return;
    }
    processRemoveAndReplace(element, actionId, keymap, shortcut);
  }

  private static void reportActionError(@Nullable PluginId pluginId, @NotNull String message) {
    reportActionError(pluginId, message, null);
  }

  private static void reportActionError(@Nullable PluginId pluginId, @NotNull String message, @Nullable Throwable cause) {
    if (pluginId != null) {
      LOG.error(new PluginException(message, cause, pluginId));
    }
    else if (cause != null) {
      LOG.error(message, cause);
    }
    else {
      LOG.error(message);
    }
  }

  private static void reportKeymapNotFoundWarning(@Nullable PluginId pluginId, @NotNull String keymapName) {
    if (DefaultKeymap.isBundledKeymapHidden(keymapName)) {
      return;
    }
    String message = "keymap \"" + keymapName + "\" not found";
    LOG.warn(pluginId == null ? message : new PluginException(message, null, pluginId).getMessage());
  }

  private static String getPluginInfo(@Nullable PluginId id) {
    IdeaPluginDescriptor plugin = id == null ? null : PluginManagerCore.getPlugin(id);
    if (plugin == null) {
      return "";
    }

    String name = plugin.getName();
    if (name == null) {
      name = id.getIdString();
    }
    return " (Plugin: " + name + ")";
  }

  private static @NotNull DataContext getContextBy(Component contextComponent) {
    DataManager dataManager = DataManager.getInstance();
    return contextComponent != null ? dataManager.getDataContext(contextComponent) : dataManager.getDataContext();
  }

  @Override
  public void dispose() {
    if (myTimer != null) {
      myTimer.stop();
      myTimer = null;
    }
  }

  @Override
  public void addTimerListener(int unused, @NotNull final TimerListener listener) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    if (myTimer == null) {
      myTimer = new MyTimer();
      myTimer.start();
    }

    myTimer.listeners.add(listener);
  }

  @Override
  public void removeTimerListener(@NotNull TimerListener listener) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    if (LOG.assertTrue(myTimer != null)) {
      myTimer.listeners.remove(listener);
    }
  }

  @NotNull
  public ActionPopupMenu createActionPopupMenu(@NotNull String place, @NotNull ActionGroup group, @Nullable PresentationFactory presentationFactory) {
    return new ActionPopupMenuImpl(place, group, this, presentationFactory);
  }

  @NotNull
  @Override
  public ActionPopupMenu createActionPopupMenu(@NotNull String place, @NotNull ActionGroup group) {
    return new ActionPopupMenuImpl(place, group, this, null);
  }

  @NotNull
  @Override
  public ActionToolbar createActionToolbar(@NotNull final String place, @NotNull final ActionGroup group, final boolean horizontal) {
    return createActionToolbar(place, group, horizontal, false);
  }

  @NotNull
  @Override
  public ActionToolbar createActionToolbar(@NotNull final String place, @NotNull final ActionGroup group, final boolean horizontal, final boolean decorateButtons) {
    return new ActionToolbarImpl(place, group, horizontal, decorateButtons);
  }

  private void registerPluginActions(@NotNull IdeaPluginDescriptorImpl plugin, @NotNull KeymapManager keymapManager) {
    List<Element> elements = plugin.getActionDescriptionElements();
    if (elements == null) {
      return;
    }

    long startTime = StartUpMeasurer.getCurrentTime();

    String lastBundleName = null;
    ResourceBundle lastBundle = null;
    for (Element element : elements) {
      Element parent = element.getParentElement();

      String bundleName = parent == null ? null : parent.getAttributeValue(RESOURCE_BUNDLE_ATTR_NAME);
      if (bundleName == null) {
        bundleName = plugin.getPluginId() == PluginManagerCore.CORE_ID ? ACTIONS_BUNDLE : plugin.getResourceBundleBaseName();
      }

      ResourceBundle bundle;
      if (bundleName == null) {
        bundle = null;
      }
      else if (bundleName.equals(lastBundleName)) {
        bundle = lastBundle;
      }
      else {
        try {
          bundle = DynamicBundle.INSTANCE.getResourceBundle(bundleName, plugin.getPluginClassLoader());
          lastBundle = bundle;
          lastBundleName = bundleName;
        }
        catch (MissingResourceException e) {
          LOG.error(new PluginException("Cannot resolve resource bundle " + bundleName + " for action " + JDOMUtil.writeElement(element), e, plugin.getPluginId()));
          bundle = null;
        }
      }

      switch (element.getName()) {
        case ACTION_ELEMENT_NAME:
          processActionElement(element, plugin, bundle, keymapManager);
          break;
        case GROUP_ELEMENT_NAME:
          processGroupElement(element, plugin, bundle, keymapManager);
          break;
        case SEPARATOR_ELEMENT_NAME:
          processSeparatorNode(null, element, plugin.getPluginId(), bundle);
          break;
        case REFERENCE_ELEMENT_NAME:
          processReferenceNode(element, plugin.getPluginId(), bundle);
          break;
        case UNREGISTER_ELEMENT_NAME:
          processUnregisterNode(element, plugin.getPluginId());
          break;
        case PROHIBIT_ELEMENT_NAME:
          processProhibitNode(element, plugin.getPluginId());
          break;
        default:
          LOG.error(new PluginException("Unexpected name of element" + element.getName(), plugin.getPluginId()));
          break;
      }
    }
    StartUpMeasurer.addPluginCost(plugin.getPluginId().getIdString(), "Actions", StartUpMeasurer.getCurrentTime() - startTime);
  }

  @Override
  @Nullable
  public AnAction getAction(@NotNull String id) {
    return getActionImpl(id, false);
  }

  @Nullable
  private AnAction getActionImpl(@NotNull String id, boolean canReturnStub) {
    AnAction action;
    synchronized (myLock) {
      action = idToAction.get(id);
      if (canReturnStub || !(action instanceof ActionStubBase)) {
        return action;
      }
    }
    AnAction converted = action instanceof ActionStub ? convertStub((ActionStub)action) : convertGroupStub((ActionGroupStub)action, this);
    if (converted == null) {
      unregisterAction(id);
      return null;
    }

    synchronized (myLock) {
      action = idToAction.get(id);
      if (action instanceof ActionStubBase) {
        action = replaceStub((ActionStubBase)action, converted);
      }
      return action;
    }
  }

  @NotNull
  private AnAction replaceStub(@NotNull ActionStubBase stub, AnAction anAction) {
    LOG.assertTrue(actionToId.containsKey(stub));
    actionToId.remove(stub);

    LOG.assertTrue(idToAction.containsKey(stub.getId()));

    AnAction action = idToAction.remove(stub.getId());
    LOG.assertTrue(action != null);
    LOG.assertTrue(action.equals(stub));

    actionToId.put(anAction, stub.getId());
    updateHandlers(anAction);

    return addToMap(stub.getId(), anAction, stub.getPlugin().getPluginId(), stub instanceof ActionStub ? ((ActionStub)stub).getProjectType() : null);
  }

  @Override
  public String getId(@NotNull AnAction action) {
    if (action instanceof ActionStubBase) {
      return ((ActionStubBase)action).getId();
    }
    synchronized (myLock) {
      return actionToId.get(action);
    }
  }

  @Override
  public @NotNull List<String> getActionIdList(@NotNull String idPrefix) {
    List<String> result = new ArrayList<>();
    synchronized (myLock) {
      for (String id : idToAction.keySet()) {
        if (id.startsWith(idPrefix)) {
          result.add(id);
        }
      }
    }
    return result;
  }

  @Override
  public String @NotNull [] getActionIds(@NotNull String idPrefix) {
    return ArrayUtilRt.toStringArray(getActionIdList(idPrefix));
  }

  @Override
  public boolean isGroup(@NotNull String actionId) {
    return getActionImpl(actionId, true) instanceof ActionGroup;
  }

  @NotNull
  @Override
  public JComponent createButtonToolbar(@NotNull final String actionPlace, @NotNull final ActionGroup messageActionGroup) {
    return new ButtonToolbarImpl(actionPlace, messageActionGroup);
  }

  @Override
  public AnAction getActionOrStub(@NotNull String id) {
    return getActionImpl(id, true);
  }

  /**
   * @return instance of ActionGroup or ActionStub. The method never returns real subclasses of {@code AnAction}.
   */
  private @Nullable AnAction processActionElement(@NotNull Element element,
                                                  @NotNull IdeaPluginDescriptorImpl plugin,
                                                  @Nullable ResourceBundle bundle,
                                                  @NotNull KeymapManager keymapManager) {
    String className = element.getAttributeValue(CLASS_ATTR_NAME);
    if (className == null || className.isEmpty()) {
      reportActionError(plugin.getPluginId(), "action element should have specified \"class\" attribute");
      return null;
    }

    // read ID and register loaded action
    String id = obtainActionId(element, className);
    synchronized (myLock) {
      if (myProhibitedActionIds.contains(id)) {
        return null;
      }
    }
    if (Boolean.parseBoolean(element.getAttributeValue(INTERNAL_ATTR_NAME)) &&
        !ApplicationManager.getApplication().isInternal()) {
      myNotRegisteredInternalActionIds.add(id);
      return null;
    }

    String iconPath = element.getAttributeValue(ICON_ATTR_NAME);
    String projectType = element.getAttributeValue(PROJECT_TYPE);

    String textValue = element.getAttributeValue(TEXT_ATTR_NAME);
    //noinspection HardCodedStringLiteral
    String descriptionValue = element.getAttributeValue(DESCRIPTION);

    ActionStub stub = new ActionStub(className, id, plugin, iconPath, projectType, () -> {
      Supplier<String> text = () -> computeActionText(bundle, id, ACTION_ELEMENT_NAME, textValue);
      if (text.get() == null) {
        reportActionError(plugin.getPluginId(), "'text' attribute is mandatory (actionId=" + id +
                                                ", plugin=" + plugin + ")");
      }

      Presentation presentation = new Presentation();
      presentation.setText(text);
      if (bundle == null) {
        presentation.setDescription(descriptionValue);
      }
      else {
        presentation.setDescription(() -> computeDescription(bundle, id, ACTION_ELEMENT_NAME, descriptionValue));
      }
      return presentation;
    });

    // process all links and key bindings if any
    for (Element e : element.getChildren()) {
      switch (e.getName()) {
        case ADD_TO_GROUP_ELEMENT_NAME:
          processAddToGroupNode(stub, e, plugin.getPluginId(), isSecondary(e));
          break;
        case SHORTCUT_ELEMENT_NAME:
          processKeyboardShortcutNode(e, id, plugin.getPluginId(), keymapManager);
          break;
        case MOUSE_SHORTCUT_ELEMENT_NAME:
          processMouseShortcutNode(e, id, plugin.getPluginId(), keymapManager);
          break;
        case ABBREVIATION_ELEMENT_NAME:
          processAbbreviationNode(e, id);
          break;
        case OVERRIDE_TEXT_ELEMENT_NAME:
          processOverrideTextNode(stub, stub.getId(), e, plugin.getPluginId(), bundle);
          break;
        case SYNONYM_ELEMENT_NAME:
          processSynonymNode(stub, e, plugin.getPluginId(), bundle);
          break;
        default:
          reportActionError(plugin.getPluginId(), "unexpected name of element \"" + e.getName() + "\"");
          return null;
      }
    }

    String shortcutOfActionId = element.getAttributeValue(USE_SHORTCUT_OF_ATTR_NAME);
    if (shortcutOfActionId != null) {
      keymapManager.bindShortcuts(shortcutOfActionId, id);
    }

    registerOrReplaceActionInner(element, id, stub, plugin);
    return stub;
  }

  private static String obtainActionId(Element element, String className) {
    String id = element.getAttributeValue(ID_ATTR_NAME);
    return Strings.isEmpty(id) ? StringUtilRt.getShortName(className) : id;
  }

  private void registerOrReplaceActionInner(@NotNull Element element,
                                            @NotNull String id,
                                            @NotNull AnAction action,
                                            @NotNull IdeaPluginDescriptor plugin) {
    synchronized (myLock) {
      if (myProhibitedActionIds.contains(id)) {
        return;
      }
      if (Boolean.parseBoolean(element.getAttributeValue(OVERRIDES_ATTR_NAME))) {
        if (getActionOrStub(id) == null) {
          LOG.error(element.getName() + " '" + id + "' doesn't override anything");
          return;
        }
        AnAction prev = replaceAction(id, action, plugin.getPluginId());
        if (action instanceof DefaultActionGroup && prev instanceof DefaultActionGroup) {
          if (Boolean.parseBoolean(element.getAttributeValue(KEEP_CONTENT_ATTR_NAME))) {
            ((DefaultActionGroup)action).copyFromGroup((DefaultActionGroup)prev);
          }
        }
      }
      else {
        registerAction(id, action, plugin.getPluginId(), element.getAttributeValue(PROJECT_TYPE));
      }
      ActionsCollectorImpl.onActionLoadedFromXml(action, id, plugin);
    }
  }

  private AnAction processGroupElement(@NotNull Element element,
                                       @NotNull IdeaPluginDescriptorImpl plugin,
                                       @Nullable ResourceBundle bundle,
                                       @NotNull KeymapManager keymapManager) {
    if (!GROUP_ELEMENT_NAME.equals(element.getName())) {
      reportActionError(plugin.getPluginId(), "unexpected name of element \"" + element.getName() + "\"");
      return null;
    }

    String className = element.getAttributeValue(CLASS_ATTR_NAME);
    if (className == null) {
      // use default group if class isn't specified
      className = "true".equals(element.getAttributeValue(COMPACT_ATTR_NAME))
                  ? DefaultCompactActionGroup.class.getName()
                  : DefaultActionGroup.class.getName();
    }

    try {
      String id = element.getAttributeValue(ID_ATTR_NAME);
      if (id != null && id.isEmpty()) {
        reportActionError(plugin.getPluginId(), "ID of the group cannot be an empty string");
        return null;
      }
      synchronized (myLock) {
        if (myProhibitedActionIds.contains(id)) {
          return null;
        }
      }

      ActionGroup group;
      boolean customClass = false;
      if (DefaultActionGroup.class.getName().equals(className)) {
        group = new DefaultActionGroup();
      }
      else if (DefaultCompactActionGroup.class.getName().equals(className)) {
        group = new DefaultCompactActionGroup();
      }
      else if (id == null) {
        Object obj = ApplicationManager.getApplication().instantiateClass(className, plugin);
        if (!(obj instanceof ActionGroup)) {
          reportActionError(plugin.getPluginId(), "class with name \"" + className + "\" should be instance of " + ActionGroup.class.getName());
          return null;
        }
        if (element.getChildren().size() != element.getChildren(ADD_TO_GROUP_ELEMENT_NAME).size() ) {  //
          if (!(obj instanceof DefaultActionGroup)) {
            reportActionError(plugin.getPluginId(), "class with name \"" + className + "\" should be instance of " + DefaultActionGroup.class.getName() +
                                        " because there are children specified");
            return null;
          }
        }
        customClass = true;
        group = (ActionGroup)obj;
      }
      else {
        group = new ActionGroupStub(id, className, plugin);
        customClass = true;
      }
      // read ID and register loaded group
      if (Boolean.parseBoolean(element.getAttributeValue(INTERNAL_ATTR_NAME)) && !ApplicationManager.getApplication().isInternal()) {
        myNotRegisteredInternalActionIds.add(id);
        return null;
      }

      if (id == null) {
        id = "<anonymous-group-" + myAnonymousGroupIdCounter++ + ">";
      }

      registerOrReplaceActionInner(element, id, group, plugin);
      Presentation presentation = group.getTemplatePresentation();
      String finalId = id;

      // text
      Supplier<String> text = () -> computeActionText(bundle, finalId, GROUP_ELEMENT_NAME, element.getAttributeValue(TEXT_ATTR_NAME));
      // don't override value which was set in API with empty value from xml descriptor
      if (!Strings.isEmpty(text.get()) || presentation.getText() == null) {
        presentation.setText(text);
      }

      // description
      String description = element.getAttributeValue(DESCRIPTION); //NON-NLS
      if (bundle == null) {
        // don't override value which was set in API with empty value from xml descriptor
        if (!Strings.isEmpty(description) || presentation.getDescription() == null) {
          presentation.setDescription(description);
        }
      }
      else {
        Supplier<String> descriptionSupplier = () -> computeDescription(bundle, finalId, GROUP_ELEMENT_NAME, description);
        // don't override value which was set in API with empty value from xml descriptor
        if (!Strings.isEmpty(descriptionSupplier.get()) || presentation.getDescription() == null) {
          presentation.setDescription(descriptionSupplier);
        }
      }

      // icon
      String iconPath = element.getAttributeValue(ICON_ATTR_NAME);
      if (group instanceof ActionGroupStub) {
        ((ActionGroupStub)group).setIconPath(iconPath);
      }
      else if (iconPath != null) {
        setIconFromClass(null, plugin, iconPath, presentation);
      }

      // popup
      String popup = element.getAttributeValue(POPUP_ATTR_NAME);
      if (popup != null) {
        group.setPopup(Boolean.parseBoolean(popup));
        if (group instanceof ActionGroupStub) {
          ((ActionGroupStub)group).setPopupDefinedInXml(true);
        }
      }

      String searchable = element.getAttributeValue(SEARCHABLE_ATTR_NAME);
      if (searchable != null) {
        group.setSearchable(Boolean.parseBoolean(searchable));
      }

      String shortcutOfActionId = element.getAttributeValue(USE_SHORTCUT_OF_ATTR_NAME);
      if (customClass && shortcutOfActionId != null) {
        KeymapManagerEx.getInstanceEx().bindShortcuts(shortcutOfActionId, id);
      }

      // process all group's children. There are other groups, actions, references and links
      for (Element child : element.getChildren()) {
        String name = child.getName();
        if (ACTION_ELEMENT_NAME.equals(name)) {
          AnAction action = processActionElement(child, plugin, bundle, keymapManager);
          if (action != null) {
            addToGroupInner(group, action, Constraints.LAST, isSecondary(child));
          }
        }
        else if (SEPARATOR_ELEMENT_NAME.equals(name)) {
          processSeparatorNode((DefaultActionGroup)group, child, plugin.getPluginId(), bundle);
        }
        else if (GROUP_ELEMENT_NAME.equals(name)) {
          AnAction action = processGroupElement(child, plugin, bundle, keymapManager);
          if (action != null) {
            addToGroupInner(group, action, Constraints.LAST, false);
          }
        }
        else if (ADD_TO_GROUP_ELEMENT_NAME.equals(name)) {
          processAddToGroupNode(group, child, plugin.getPluginId(), isSecondary(child));
        }
        else if (REFERENCE_ELEMENT_NAME.equals(name)) {
          AnAction action = processReferenceElement(child, plugin.getPluginId());
          if (action != null) {
            addToGroupInner(group, action, Constraints.LAST, isSecondary(child));
          }
        }
        else if (OVERRIDE_TEXT_ELEMENT_NAME.equals(name)) {
          processOverrideTextNode(group, id, child, plugin.getPluginId(), bundle);
        }
        else {
          reportActionError(plugin.getPluginId(), "unexpected name of element \"" + name + "\n");
          return null;
        }
      }
      return group;
    }
    catch (Exception e) {
      String message = "cannot create class \"" + className + "\"";
      reportActionError(plugin.getPluginId(), message, e);
      return null;
    }
  }

  private void processReferenceNode(@NotNull Element element, @Nullable PluginId pluginId, @Nullable ResourceBundle bundle) {
    AnAction action = processReferenceElement(element, pluginId);
    if (action == null) {
      return;
    }

    for (Element child : element.getChildren()) {
      if (ADD_TO_GROUP_ELEMENT_NAME.equals(child.getName())) {
        processAddToGroupNode(action, child, pluginId, isSecondary(child));
      }
      else if (SYNONYM_ELEMENT_NAME.equals(child.getName())) {
        processSynonymNode(action, child, pluginId, bundle);
      }
    }
  }

  /**
   * @param element description of link
   */
  private void processAddToGroupNode(AnAction action, Element element, PluginId pluginId, boolean secondary) {
    String name = action instanceof ActionStub ? ((ActionStub)action).getClassName() : action.getClass().getName();
    String id = action instanceof ActionStub ? ((ActionStub)action).getId() : actionToId.get(action);
    String actionName = name + " (" + id + ")";

    if (!ADD_TO_GROUP_ELEMENT_NAME.equals(element.getName())) {
      reportActionError(pluginId, "unexpected name of element \"" + element.getName() + "\"");
      return;
    }

    // parent group
    final AnAction parentGroup = getParentGroup(element.getAttributeValue(GROUPID_ATTR_NAME), actionName, pluginId);
    if (parentGroup == null) {
      return;
    }

    // anchor attribute
    final Anchor anchor = parseAnchor(element.getAttributeValue(ANCHOR_ELEMENT_NAME), actionName, pluginId);
    if (anchor == null) {
      return;
    }

    final String relativeToActionId = element.getAttributeValue(RELATIVE_TO_ACTION_ATTR_NAME);
    if (!checkRelativeToAction(relativeToActionId, anchor, actionName, pluginId)) {
      return;
    }
    addToGroupInner(parentGroup, action, new Constraints(anchor, relativeToActionId), secondary);
  }

  private void addToGroupInner(AnAction group, AnAction action, Constraints constraints, boolean secondary) {
    String actionId = action instanceof ActionStub ? ((ActionStub)action).getId() : actionToId.get(action);
    ((DefaultActionGroup)group).addAction(action, constraints, this).setAsSecondary(secondary);
    idToGroupId.putValue(actionId, actionToId.get(group));
  }

  @Nullable
  public DefaultActionGroup getParentGroup(final String groupId,
                                           @Nullable final String actionName,
                                           @Nullable final PluginId pluginId) {
    if (groupId == null || groupId.isEmpty()) {
      reportActionError(pluginId, actionName + ": attribute \"group-id\" should be defined");
      return null;
    }
    AnAction parentGroup = getActionImpl(groupId, true);
    if (parentGroup == null) {
      reportActionError(pluginId, actionName + ": group with id \"" + groupId + "\" isn't registered; action will be added to the \"Other\" group", null);
      parentGroup = getActionImpl(IdeActions.GROUP_OTHER_MENU, true);
    }
    if (!(parentGroup instanceof DefaultActionGroup)) {
      reportActionError(pluginId, actionName + ": group with id \"" + groupId + "\" should be instance of " + DefaultActionGroup.class.getName() +
                                  " but was " + (parentGroup != null ? parentGroup.getClass() : "[null]"));
      return null;
    }
    return (DefaultActionGroup)parentGroup;
  }

  private static void processOverrideTextNode(AnAction action, String id, Element element, PluginId pluginId,
                                              @Nullable ResourceBundle bundle) {
    if (!OVERRIDE_TEXT_ELEMENT_NAME.equals(element.getName())) {
      reportActionError(pluginId, "unexpected name of element \"" + element.getName() + "\"");
      return;
    }
    String place = element.getAttributeValue(PLACE_ATTR_NAME);
    if (place == null) {
      reportActionError(pluginId, id + ": override-text specified without place");
      return;
    }
    String useTextOfPlace = element.getAttributeValue(USE_TEXT_OF_PLACE_ATTR_NAME);
    if (useTextOfPlace != null) {
      action.copyActionTextOverride(useTextOfPlace, place, id);
    }
    else {
      String text = element.getAttributeValue(TEXT_ATTR_NAME, "");
      if (text.isEmpty() && bundle != null) {
        String prefix = action instanceof ActionGroup ? "group" : "action";
        String key = prefix + "." + id + "." + place + ".text";
        action.addTextOverride(place, () -> BundleBase.message(bundle, key));
      }
      else {
        action.addTextOverride(place, () -> text);
      }
    }
  }

  private static void processSynonymNode(AnAction action, Element element, PluginId pluginId, @Nullable ResourceBundle bundle) {
    if (!SYNONYM_ELEMENT_NAME.equals(element.getName())) {
      reportActionError(pluginId, "unexpected name of element \"" + element.getName() + "\"");
      return;
    }

    //noinspection HardCodedStringLiteral
    String text = element.getAttributeValue(TEXT_ATTR_NAME, "");
    if (!text.isEmpty()) {
      action.addSynonym(() -> text);
    }
    else {
      String key = element.getAttributeValue(KEY_ATTR_NAME);
      if (key != null && bundle != null) {
        action.addSynonym(() -> BundleBase.message(bundle, key));
      }
      else {
        reportActionError(pluginId, "Can't process synonym: neither text nor resource bundle key is specified");
      }
    }
  }

  /**
   * @param parentGroup group which is the parent of the separator. It can be {@code null} in that
   *                    case separator will be added to group described in the <add-to-group ....> subelement.
   * @param element     XML element which represent separator.
   */
  private void processSeparatorNode(@Nullable DefaultActionGroup parentGroup, @NotNull Element element, PluginId pluginId, @Nullable ResourceBundle bundle) {
    if (!SEPARATOR_ELEMENT_NAME.equals(element.getName())) {
      reportActionError(pluginId, "unexpected name of element \"" + element.getName() + "\"");
      return;
    }
    @SuppressWarnings("HardCodedStringLiteral")
    String text = element.getAttributeValue(TEXT_ATTR_NAME);
    String key = element.getAttributeValue(KEY_ATTR_NAME);
    Separator separator =
      text != null ? new Separator(text) : key != null ? createSeparator(bundle, key) : Separator.getInstance();
    if (parentGroup != null) {
      parentGroup.add(separator, this);
    }
    // try to find inner <add-to-parent...> tag
    for (Element child : element.getChildren()) {
      if (ADD_TO_GROUP_ELEMENT_NAME.equals(child.getName())) {
        processAddToGroupNode(separator, child, pluginId, isSecondary(child));
      }
    }
  }

  @NotNull
  private static Separator createSeparator(@Nullable ResourceBundle bundle, @NotNull String key) {
    String text = bundle != null ? AbstractBundle.messageOrNull(bundle, key) : null;
    return text != null ? new Separator(text) : Separator.getInstance();
  }

  private void processProhibitNode(Element element, PluginId pluginId) {
    String id = element.getAttributeValue(ID_ATTR_NAME);
    if (id == null) {
      reportActionError(pluginId, "'id' attribute is required for 'unregister' elements");
      return;
    }

    prohibitAction(id);
  }

  private void processUnregisterNode(Element element, PluginId pluginId) {
    String id = element.getAttributeValue(ID_ATTR_NAME);
    if (id == null) {
      reportActionError(pluginId, "'id' attribute is required for 'unregister' elements");
      return;
    }
    AnAction action = getAction(id);
    if (action == null) {
      reportActionError(pluginId, "Trying to unregister non-existing action " + id);
      return;
    }

    AbbreviationManager.getInstance().removeAllAbbreviations(id);
    unregisterAction(id);
  }

  private static void processKeyboardShortcutNode(Element element,
                                                  String actionId,
                                                  PluginId pluginId,
                                                  @NotNull KeymapManager keymapManager) {
    String firstStrokeString = element.getAttributeValue(FIRST_KEYSTROKE_ATTR_NAME);
    if (firstStrokeString == null) {
      reportActionError(pluginId, "\"first-keystroke\" attribute must be specified for action with id=" + actionId);
      return;
    }
    KeyStroke firstKeyStroke = getKeyStroke(firstStrokeString);
    if (firstKeyStroke == null) {
      reportActionError(pluginId, "\"first-keystroke\" attribute has invalid value for action with id=" + actionId);
      return;
    }

    KeyStroke secondKeyStroke = null;
    String secondStrokeString = element.getAttributeValue(SECOND_KEYSTROKE_ATTR_NAME);
    if (secondStrokeString != null) {
      secondKeyStroke = getKeyStroke(secondStrokeString);
      if (secondKeyStroke == null) {
        reportActionError(pluginId, "\"second-keystroke\" attribute has invalid value for action with id=" + actionId);
        return;
      }
    }

    String keymapName = element.getAttributeValue(KEYMAP_ATTR_NAME);
    if (keymapName == null || keymapName.trim().isEmpty()) {
      reportActionError(pluginId, "attribute \"keymap\" should be defined");
      return;
    }
    Keymap keymap = keymapManager.getKeymap(keymapName);
    if (keymap == null) {
      reportKeymapNotFoundWarning(pluginId, keymapName);
      return;
    }
    final KeyboardShortcut shortcut = new KeyboardShortcut(firstKeyStroke, secondKeyStroke);
    processRemoveAndReplace(element, actionId, keymap, shortcut);
  }

  private static void processRemoveAndReplace(@NotNull Element element, String actionId, @NotNull Keymap keymap, @NotNull Shortcut shortcut) {
    boolean remove = Boolean.parseBoolean(element.getAttributeValue(REMOVE_SHORTCUT_ATTR_NAME));
    boolean replace = Boolean.parseBoolean(element.getAttributeValue(REPLACE_SHORTCUT_ATTR_NAME));
    if (remove) {
      keymap.removeShortcut(actionId, shortcut);
    }
    if (replace) {
      keymap.removeAllActionShortcuts(actionId);
    }
    if (!remove) {
      keymap.addShortcut(actionId, shortcut);
    }
  }

  private @Nullable AnAction processReferenceElement(Element element, PluginId pluginId) {
    if (!REFERENCE_ELEMENT_NAME.equals(element.getName())) {
      reportActionError(pluginId, "unexpected name of element \"" + element.getName() + "\"", null);
      return null;
    }

    String ref = getReferenceActionId(element);
    if (ref == null || ref.isEmpty()) {
      reportActionError(pluginId, "ID of reference element should be defined", null);
      return null;
    }

    synchronized (myLock) {
      if (myProhibitedActionIds.contains(ref)) {
        return null;
      }
    }

    AnAction action = getActionImpl(ref, true);
    if (action == null) {
      if (!myNotRegisteredInternalActionIds.contains(ref)) {
        reportActionError(pluginId, "action specified by reference isn't registered (ID=" + ref + ")", null);
      }
      return null;
    }
    return action;
  }

  private static String getReferenceActionId(@NotNull Element element) {
    String ref = element.getAttributeValue(REF_ATTR_NAME);
    if (ref == null) {
      // support old style references by id
      ref = element.getAttributeValue(ID_ATTR_NAME);
    }
    return ref;
  }

  @ApiStatus.Internal
  public static @Nullable String checkUnloadActions(PluginId pluginId, @NotNull IdeaPluginDescriptorImpl pluginDescriptor) {
    List<Element> elements = pluginDescriptor.getActionDescriptionElements();
    if (elements == null) {
      return null;
    }

    for (Element element : elements) {
      if (!element.getName().equals(ACTION_ELEMENT_NAME) &&
          !(element.getName().equals(GROUP_ELEMENT_NAME) && canUnloadGroup(element)) &&
          !element.getName().equals(REFERENCE_ELEMENT_NAME)) {
        return "Plugin " + pluginId + " is not unload-safe because of action element " + element.getName();
      }
    }
    return null;
  }

  private static boolean canUnloadGroup(@NotNull Element element) {
    if (element.getAttributeValue(ID_ATTR_NAME) == null) {
      return false;
    }
    for (Element child : element.getChildren()) {
      if (child.getName().equals(GROUP_ELEMENT_NAME) && !canUnloadGroup(child)) return false;
    }
    return true;
  }

  public void unloadActions(@NotNull IdeaPluginDescriptorImpl pluginDescriptor) {
    List<Element> elements = pluginDescriptor.getActionDescriptionElements();
    if (elements == null) {
      return;
    }

    for (Element element : ContainerUtil.reverse(elements)) {
      switch (element.getName()) {
        case ACTION_ELEMENT_NAME:
          unloadActionElement(element);
          break;
        case GROUP_ELEMENT_NAME:
          unloadGroupElement(element);
          break;
        case REFERENCE_ELEMENT_NAME:
          PluginId pluginId = pluginDescriptor.getPluginId();
          AnAction action = processReferenceElement(element, pluginId);
          if (action == null) return;
          String actionId = getReferenceActionId(element);

          for (Element child : element.getChildren(ADD_TO_GROUP_ELEMENT_NAME)) {
            String groupId = child.getAttributeValue(GROUPID_ATTR_NAME);
            final DefaultActionGroup parentGroup = getParentGroup(groupId, actionId, pluginId);
            if (parentGroup == null) return;
            parentGroup.remove(action);
            idToGroupId.remove(actionId, groupId);
          }
          break;
      }
    }
  }

  private void unloadGroupElement(Element element) {
    String id = element.getAttributeValue(ID_ATTR_NAME);
    if (id == null) {
      throw new IllegalStateException("Cannot unload groups with no ID");
    }
    for (Element groupChild : element.getChildren()) {
      if (groupChild.getName().equals(ACTION_ELEMENT_NAME)) {
        unloadActionElement(groupChild);
      }
      else if (groupChild.getName().equals(GROUP_ELEMENT_NAME)) {
        unloadGroupElement(groupChild);
      }
    }
    unregisterAction(id);
  }

  private void unloadActionElement(@NotNull Element element) {
    String className = element.getAttributeValue(CLASS_ATTR_NAME);
    String id = obtainActionId(element, className);
    unregisterAction(id);
  }

  @Override
  public void registerAction(@NotNull String actionId, @NotNull AnAction action, @Nullable PluginId pluginId) {
    registerAction(actionId, action, pluginId, null);
  }

  public void registerAction(@NotNull String actionId,
                             @NotNull AnAction action,
                             @Nullable PluginId pluginId,
                             @Nullable String projectType) {
    synchronized (myLock) {
      if (myProhibitedActionIds.contains(actionId)) {
        return;
      }
      if (addToMap(actionId, action, pluginId, projectType) == null) return;
      if (actionToId.containsKey(action)) {
        reportActionError(pluginId,
                          "ID \"" + actionToId.get(action) + "\" is already taken by action \"" + action + "\"" + getPluginInfo(pluginId) +
                          ". ID \"" + actionId + "\" cannot be registered for the same action");
        return;
      }
      idToIndex.put(actionId, myRegisteredActionsCount++);
      actionToId.put(action, actionId);
      if (pluginId != null) {
        pluginToId.putValue(pluginId, actionId);
      }
      action.registerCustomShortcutSet(new ProxyShortcutSet(actionId), null);
      notifyCustomActionsSchema(actionId);
      updateHandlers(action);
    }
  }

  private static void notifyCustomActionsSchema(@NotNull String registeredID) {
    CustomActionsSchema schema = ApplicationManager.getApplication().getServiceIfCreated(CustomActionsSchema.class);
    if (schema == null) return;
    for (ActionUrl url : schema.getActions()) {
      if (registeredID.equals(url.getComponent())) {
        schema.incrementModificationStamp();
        break;
      }
    }
  }

  private AnAction addToMap(String actionId, AnAction action, PluginId pluginId, String projectType) {
    if (projectType != null || idToAction.containsKey(actionId)) {
      return registerChameleon(actionId, action, pluginId, projectType);
    }
    else {
      idToAction.put(actionId, action);
      return action;
    }
  }

  private AnAction registerChameleon(String actionId, AnAction action, PluginId pluginId, String projectType) {
    ProjectType type = projectType == null ? null : new ProjectType(projectType);
    // make sure id+projectType is unique
    AnAction o = idToAction.get(actionId);
    ChameleonAction chameleonAction;
    if (o == null) {
      chameleonAction = new ChameleonAction(action, type);
      idToAction.put(actionId, chameleonAction);
      return chameleonAction;
    }
    if (o instanceof ChameleonAction) {
      chameleonAction = (ChameleonAction)o;
    }
    else {
      chameleonAction = new ChameleonAction(o, type);
      idToAction.put(actionId, chameleonAction);
    }
    AnAction old = chameleonAction.addAction(action, type);
    if (old != null) {
      String oldPluginInfo = pluginToId.keySet().stream()
        .filter(p -> pluginToId.get(p).contains(actionId))
        .map(ActionManagerImpl::getPluginInfo).collect(Collectors.joining(","));
      reportActionError(pluginId,
                        "ID \"" + actionId + "\" is already taken by action \"" + idToAction.get(actionId) + "\"" + oldPluginInfo +
                        ". Action \"" + action + "\"" + getPluginInfo(pluginId) + " cannot use the same ID");
      return null;
    }
    return chameleonAction;
  }

  @Override
  public void registerAction(@NotNull String actionId, @NotNull AnAction action) {
    registerAction(actionId, action, null);
  }

  @Override
  public void unregisterAction(@NotNull String actionId) {
    unregisterAction(actionId, true);
  }

  private void unregisterAction(@NotNull String actionId, boolean removeFromGroups) {
    synchronized (myLock) {
      if (!idToAction.containsKey(actionId)) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("action with ID " + actionId + " wasn't registered");
        }
        return;
      }
      AnAction actionToRemove = idToAction.remove(actionId);
      actionToId.remove(actionToRemove);
      idToIndex.removeInt(actionId);

      for (Map.Entry<PluginId, Collection<String>> entry : pluginToId.entrySet()) {
        entry.getValue().remove(actionId);
      }

      if (removeFromGroups) {
        CustomActionsSchema customActionSchema = ApplicationManager.getApplication().getServiceIfCreated(CustomActionsSchema.class);
        for (String groupId : idToGroupId.get(actionId)) {
          if (customActionSchema != null) {
            customActionSchema.invalidateCustomizedActionGroup(groupId);
          }
          DefaultActionGroup group = (DefaultActionGroup)getActionOrStub(groupId);
          if (group == null) {
            LOG.error("Trying to remove action " + actionId + " from non-existing group " + groupId);
            continue;
          }
          group.remove(actionToRemove, actionId);
          if (!(group instanceof ActionGroupStub)) {
            //group can be used as a stub in other actions
            for (String parentOfGroup : idToGroupId.get(groupId)) {
              DefaultActionGroup parentOfGroupAction = (DefaultActionGroup) getActionOrStub(parentOfGroup);
              if (parentOfGroupAction == null) {
                LOG.error("Trying to remove action " + actionId + " from non-existing group " + parentOfGroup);
                continue;
              }
              for (AnAction stub : parentOfGroupAction.getChildActionsOrStubs()) {
                if (stub instanceof ActionGroupStub && ((ActionGroupStub)stub).getId() == groupId) {
                  ((ActionGroupStub)stub).remove(actionToRemove, actionId);
                }
              }
            }
          }
        }
      }
      if (actionToRemove instanceof ActionGroup) {
        for (Map.Entry<String, Collection<String>> entry : idToGroupId.entrySet()) {
          entry.getValue().remove(actionId);
        }
      }
      updateHandlers(actionToRemove);
    }
  }

  /**
   * Unregisters already registered action and prevents the action from being registered in future.
   * Should be used only in IDE configuration
   */
  @ApiStatus.Internal
  public void prohibitAction(@NotNull String actionId) {
    synchronized (myLock) {
      myProhibitedActionIds.add(actionId);
    }
    AnAction action = getAction(actionId);
    if (action != null) {
      AbbreviationManager.getInstance().removeAllAbbreviations(actionId);
      unregisterAction(actionId);
    }
  }

  @TestOnly
  public void resetProhibitedActions() {
    synchronized (myLock) {
      myProhibitedActionIds.clear();
    }
  }

  @NotNull
  @Override
  public Comparator<String> getRegistrationOrderComparator() {
    return Comparator.comparingInt(idToIndex::getInt);
  }

  @Override
  public String @NotNull [] getPluginActions(@NotNull PluginId pluginName) {
    return ArrayUtilRt.toStringArray(pluginToId.get(pluginName));
  }

  public void addActionPopup(@NotNull Object menu) {
    myPopups.add(menu);
    if (menu instanceof ActionPopupMenu) {
      for (ActionPopupMenuListener listener : myActionPopupMenuListeners) {
        listener.actionPopupMenuCreated((ActionPopupMenu)menu);
      }
    }
  }

  void removeActionPopup(@NotNull Object menu) {
    final boolean removed = myPopups.remove(menu);
    if (removed && menu instanceof ActionPopupMenu) {
      for (ActionPopupMenuListener listener : myActionPopupMenuListeners) {
        listener.actionPopupMenuReleased((ActionPopupMenu)menu);
      }
    }
  }

  @Override
  public void queueActionPerformedEvent(@NotNull final AnAction action, @NotNull DataContext context, @NotNull AnActionEvent event) {
    if (myPopups.isEmpty()) {
      fireAfterActionPerformed(action, context, event);
    }
  }

  public boolean isToolWindowContextMenuVisible() {
    for (Object popup : myPopups) {
      if (popup instanceof ActionPopupMenuImpl &&
          ((ActionPopupMenuImpl)popup).isToolWindowContextMenu()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isActionPopupStackEmpty() {
    return myPopups.isEmpty();
  }

  @Override
  public void addActionPopupMenuListener(@NotNull ActionPopupMenuListener listener, @NotNull Disposable parentDisposable) {
    myActionPopupMenuListeners.add(listener);
    Disposer.register(parentDisposable, () -> myActionPopupMenuListeners.remove(listener));
  }

  @Override
  public void replaceAction(@NotNull String actionId, @NotNull AnAction newAction) {
    Class<?> callerClass = ReflectionUtil.getGrandCallerClass();
    PluginId pluginId = callerClass != null ? PluginManagerCore.getPluginByClassName(callerClass.getName()) : null;
    replaceAction(actionId, newAction, pluginId);
  }

  private AnAction replaceAction(@NotNull String actionId, @NotNull AnAction newAction, @Nullable PluginId pluginId) {
    synchronized (myLock) {
      if (myProhibitedActionIds.contains(actionId)) {
        return null;
      }
    }

    AnAction oldAction = newAction instanceof OverridingAction ? getAction(actionId) : getActionOrStub(actionId);
    if (oldAction != null) {
      if (newAction instanceof OverridingAction) {
        myBaseActions.put((OverridingAction)newAction, oldAction);
      }
      boolean isGroup = oldAction instanceof ActionGroup;
      if (isGroup != newAction instanceof ActionGroup) {
        throw new IllegalStateException("cannot replace a group with an action and vice versa: " + actionId);
      }
      for (String groupId : idToGroupId.get(actionId)) {
        DefaultActionGroup group = (DefaultActionGroup)getActionOrStub(groupId);
        if (group == null) {
          throw new IllegalStateException("Trying to replace action which has been added to a non-existing group " + groupId);
        }
        group.replaceAction(oldAction, newAction);
      }
      unregisterAction(actionId, false);
    }
    registerAction(actionId, newAction, pluginId);
    return oldAction;
  }

  /**
   * Returns the action overridden by the specified overriding action (with overrides="true" in plugin.xml).
   */
  public AnAction getBaseAction(OverridingAction overridingAction) {
    return myBaseActions.get(overridingAction);
  }

  public Collection<String> getParentGroupIds(String actionId) {
    return idToGroupId.get(actionId);
  }

  @Override
  public void addAnActionListener(AnActionListener listener) {
    myActionListeners.add(listener);
  }

  @Override
  public void removeAnActionListener(AnActionListener listener) {
    myActionListeners.remove(listener);
  }

  @Override
  public void fireBeforeActionPerformed(@NotNull AnAction action, @NotNull DataContext dataContext, @NotNull AnActionEvent event) {
    myPrevPerformedActionId = myLastPreformedActionId;
    myLastPreformedActionId = getId(action);
    if (myLastPreformedActionId == null && action instanceof ActionIdProvider) {
      myLastPreformedActionId = ((ActionIdProvider)action).getId();
    }
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    IdeaLogger.ourLastActionId = myLastPreformedActionId;
    final List<EventPair<?>> customData = new ArrayList<>();
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    Language hostFileLanguage = getHostFileLanguage(dataContext, project);
    customData.add(EventFields.CurrentFile.with(hostFileLanguage));
    if (hostFileLanguage == null || hostFileLanguage == PlainTextLanguage.INSTANCE) {
      final PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
      final Language language = file != null ? file.getLanguage() : null;
      customData.add(EventFields.Language.with(language));
    }
    if (action instanceof FusAwareAction) {
      List<EventPair<?>> additionalUsageData = ((FusAwareAction)action).getAdditionalUsageData(event);
      customData.add(ActionsEventLogGroup.ADDITIONAL.with(new ObjectEventData(additionalUsageData)));
    }
    ActionsCollectorImpl.recordActionInvoked(project, action, event, customData);
    for (AnActionListener listener : myActionListeners) {
      listener.beforeActionPerformed(action, dataContext, event);
    }
    publisher().beforeActionPerformed(action, dataContext, event);
  }

  private static @Nullable Language getHostFileLanguage(@NotNull DataContext dataContext, @Nullable Project project) {
    if (project == null) return null;
    Editor editor = CommonDataKeys.HOST_EDITOR.getData(dataContext);
    if (editor == null) return null;
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    return file != null ? file.getLanguage() : null;
  }

  @Override
  public void fireAfterActionPerformed(@NotNull AnAction action, @NotNull DataContext dataContext, @NotNull AnActionEvent event) {
    myPrevPerformedActionId = myLastPreformedActionId;
    myLastPreformedActionId = getId(action);
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    IdeaLogger.ourLastActionId = myLastPreformedActionId;
    for (AnActionListener listener : myActionListeners) {
      try {
        listener.afterActionPerformed(action, dataContext, event);
      }
      catch (AbstractMethodError ignored) {
      }
    }
    publisher().afterActionPerformed(action, dataContext, event);
  }

  @Override
  public KeyboardShortcut getKeyboardShortcut(@NotNull String actionId) {
    AnAction action = ActionManager.getInstance().getAction(actionId);
    if (action == null) return null;
    final ShortcutSet shortcutSet = action.getShortcutSet();
    final Shortcut[] shortcuts = shortcutSet.getShortcuts();
    for (final Shortcut shortcut : shortcuts) {
      // Shortcut can be MouseShortcut here.
      // For example IdeaVIM often assigns them
      if (shortcut instanceof KeyboardShortcut) {
        final KeyboardShortcut kb = (KeyboardShortcut)shortcut;
        if (kb.getSecondKeyStroke() == null) {
          return (KeyboardShortcut)shortcut;
        }
      }
    }

    return null;
  }

  @Override
  public void fireBeforeEditorTyping(char c, @NotNull DataContext dataContext) {
    myLastTimeEditorWasTypedIn = System.currentTimeMillis();
    for (AnActionListener listener : myActionListeners) {
      listener.beforeEditorTyping(c, dataContext);
    }
    publisher().beforeEditorTyping(c, dataContext);
  }

  @Override
  public void fireAfterEditorTyping(char c, @NotNull DataContext dataContext) {
    for (AnActionListener listener : myActionListeners) {
      listener.afterEditorTyping(c, dataContext);
    }
    publisher().afterEditorTyping(c, dataContext);
  }

  @Override
  public String getLastPreformedActionId() {
    return myLastPreformedActionId;
  }

  @Override
  public String getPrevPreformedActionId() {
    return myPrevPerformedActionId;
  }

  public @NotNull Set<String> getActionIds() {
    synchronized (myLock) {
      return new HashSet<>(idToAction.keySet());
    }
  }

  public void preloadActions(@NotNull ProgressIndicator indicator) {
    List<String> ids;
    synchronized (myLock) {
      ids = new ArrayList<>(idToAction.keySet());
    }
    for (String id : ids) {
      indicator.checkCanceled();
      getActionImpl(id, false);
      // don't preload ActionGroup.getChildren() because that would un-stub child actions
      // and make it impossible to replace the corresponding actions later
      // (via unregisterAction+registerAction, as some app components do)
    }
  }

  @NotNull
  @Override
  public ActionCallback tryToExecute(@NotNull AnAction action,
                                     @NotNull InputEvent inputEvent,
                                     @Nullable Component contextComponent,
                                     @Nullable String place,
                                     boolean now) {
    assert ApplicationManager.getApplication().isDispatchThread();

    ActionCallback result = new ActionCallback();
    Runnable doRunnable = () -> tryToExecuteNow(action, inputEvent, contextComponent, place, result);
    if (now) {
      doRunnable.run();
    }
    else {
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(doRunnable);
    }

    return result;
  }

  private void tryToExecuteNow(@NotNull AnAction action, @NotNull InputEvent inputEvent, @Nullable Component contextComponent, String place, ActionCallback result) {
    Presentation presentation = action.getTemplatePresentation().clone();
    IdeFocusManager.findInstanceByContext(getContextBy(contextComponent)).doWhenFocusSettlesDown(() -> {
      ((TransactionGuardImpl)TransactionGuard.getInstance()).performUserActivity(() -> {
        DataContext context = getContextBy(contextComponent);

        AnActionEvent event = new AnActionEvent(
          inputEvent, context,
          place != null ? place : ActionPlaces.UNKNOWN,
          presentation, this,
          inputEvent.getModifiersEx()
        );

        ActionUtil.performDumbAwareUpdate(LaterInvocator.isInModalContext(), action, event, false);
        if (!event.getPresentation().isEnabled()) {
          result.setRejected();
          return;
        }

        ActionUtil.lastUpdateAndCheckDumb(action, event, false);
        if (!event.getPresentation().isEnabled()) {
          result.setRejected();
          return;
        }

        Component component = PlatformDataKeys.CONTEXT_COMPONENT.getData(context);
        if (component != null && !component.isShowing() && !ActionPlaces.TOUCHBAR_GENERAL.equals(place)) {
          result.setRejected();
          return;
        }

        fireBeforeActionPerformed(action, context, event);

        UIUtil.addAwtListener(event1 -> {
          if (event1.getID() == WindowEvent.WINDOW_OPENED || event1.getID() == WindowEvent.WINDOW_ACTIVATED) {
            if (!result.isProcessed()) {
              final WindowEvent we = (WindowEvent)event1;
              IdeFocusManager.findInstanceByComponent(we.getWindow()).doWhenFocusSettlesDown(result.createSetDoneRunnable(),
                                                                                             ModalityState.defaultModalityState());
            }
          }
        }, AWTEvent.WINDOW_EVENT_MASK, result);

        ActionUtil.performActionDumbAware(action, event);
        result.setDone();
        queueActionPerformedEvent(action, context, event);
      });
    }, ModalityState.defaultModalityState());
  }

  @Override
  public @NotNull List<EditorActionHandlerBean> getRegisteredHandlers(@NotNull EditorAction editorAction) {
    List<EditorActionHandlerBean> result = new ArrayList<>();
    String id = getId(editorAction);
    if (id != null) {
      List<EditorActionHandlerBean> extensions = EDITOR_ACTION_HANDLER_EP.getExtensionList();
      for (int i = extensions.size() - 1; i >= 0; i--) {
        EditorActionHandlerBean handlerBean = extensions.get(i);
        if (handlerBean.action.equals(id)) {
          result.add(handlerBean);
        }
      }
    }
    return result;
  }

  private void updateAllHandlers() {
    synchronized (myLock) {
      actionToId.keySet().forEach(ActionManagerImpl::updateHandlers);
    }
  }

  private static void updateHandlers(Object action) {
    if (action instanceof EditorAction) {
      ((EditorAction)action).clearDynamicHandlersCache();
    }
  }

  private final class MyTimer extends Timer implements ActionListener {
    final List<TimerListener> listeners = ContainerUtil.createLockFreeCopyOnWriteList();
    private int myLastTimePerformed;

    private MyTimer() {
      super(TIMER_DELAY, null);
      addActionListener(this);
      setRepeats(true);
      final MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
      connection.subscribe(ApplicationActivationListener.TOPIC, new ApplicationActivationListener() {
        @Override
        public void applicationActivated(@NotNull IdeFrame ideFrame) {
          setDelay(TIMER_DELAY);
          restart();
        }

        @Override
        public void applicationDeactivated(@NotNull IdeFrame ideFrame) {
          setDelay(DEACTIVATED_TIMER_DELAY);
        }
      });
    }

    @Override
    public String toString() {
      return "Action manager timer";
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      if (myLastTimeEditorWasTypedIn + UPDATE_DELAY_AFTER_TYPING > System.currentTimeMillis()) {
        return;
      }

      final int lastEventCount = myLastTimePerformed;
      myLastTimePerformed = ActivityTracker.getInstance().getCount();

      if (myLastTimePerformed == lastEventCount) {
        return;
      }
      for (TimerListener listener : listeners) {
        runListenerAction(listener);
      }
    }

    private void runListenerAction(@NotNull TimerListener listener) {
      ModalityState modalityState = listener.getModalityState();
      if (modalityState == null) return;
      LOG.debug("notify ", listener);
      if (!ModalityState.current().dominates(modalityState)) {
        try {
          listener.run();
        }
        catch (ProcessCanceledException ex) {
          // ignore
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }
  }
}