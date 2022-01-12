// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.ide.ProhibitAWTEvents;
import com.intellij.ide.plugins.*;
import com.intellij.ide.ui.customization.ActionUrl;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.idea.IdeaLogger;
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionIdProvider;
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsCollectorImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.ActionPopupMenuListener;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.keymap.impl.DefaultKeymap;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.ProjectType;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.serviceContainer.PrecomputedExtensionModelKt;
import com.intellij.ui.icons.IconLoadMeasurer;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.DefaultBundleService;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.XmlElement;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import kotlin.Unit;
import kotlin.sequences.Sequence;
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
import java.util.List;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ActionManagerImpl extends ActionManagerEx implements Disposable {
  private static final ExtensionPointName<ActionConfigurationCustomizer> EP =
    new ExtensionPointName<>("com.intellij.actionConfigurationCustomizer");
  private static final ExtensionPointName<DynamicActionConfigurationCustomizer> DYNAMIC_EP_NAME =
    new ExtensionPointName<>("com.intellij.dynamicActionConfigurationCustomizer");

  private static final String ACTION_ELEMENT_NAME = "action";
  private static final String GROUP_ELEMENT_NAME = "group";
  private static final String CLASS_ATTR_NAME = "class";
  private static final String ID_ATTR_NAME = "id";
  private static final String INTERNAL_ATTR_NAME = "internal";
  private static final String ICON_ATTR_NAME = "icon";
  private static final String ADD_TO_GROUP_ELEMENT_NAME = "add-to-group";
  private static final String DESCRIPTION = "description";
  private static final String TEXT_ATTR_NAME = "text";
  private static final String KEY_ATTR_NAME = "key";
  private static final String SEPARATOR_ELEMENT_NAME = "separator";
  private static final String REFERENCE_ELEMENT_NAME = "reference";
  private static final String GROUP_ID_ATTR_NAME = "group-id";
  private static final String KEYMAP_ATTR_NAME = "keymap";
  private static final String REF_ATTR_NAME = "ref";
  private static final String USE_SHORTCUT_OF_ATTR_NAME = "use-shortcut-of";
  private static final String PROJECT_TYPE = "project-type";
  private static final String OVERRIDE_TEXT_ELEMENT_NAME = "override-text";
  private static final String SYNONYM_ELEMENT_NAME = "synonym";

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

  protected ActionManagerImpl() {
    Application app = ApplicationManager.getApplication();
    if (!app.isUnitTestMode()) {
      LoadingState.COMPONENTS_LOADED.checkOccurred();
      if (!app.isHeadlessEnvironment() && !app.isCommandLine()) {
        LOG.assertTrue(!app.isDispatchThread(), "assert !app.isDispatchThread()");
      }
    }

    registerActions(PluginManagerCore.getPluginSet().getEnabledModules());

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
    ApplicationManager.getApplication().getExtensionArea().getExtensionPoint("com.intellij.editorActionHandler")
      .addChangeListener(() -> {
        synchronized (myLock) {
          actionToId.keySet().forEach(ActionManagerImpl::updateHandlers);
        }
      }, this);
  }

  @ApiStatus.Internal
  public void registerActions(@NotNull Sequence<IdeaPluginDescriptorImpl> modules) {
    KeymapManagerEx keymapManager = Objects.requireNonNull(KeymapManagerEx.getInstanceEx());
    for (Iterator<IdeaPluginDescriptorImpl> iter = modules.iterator(); iter.hasNext(); ) {
      IdeaPluginDescriptorImpl module = iter.next();
      registerPluginActions(module, keymapManager);
      PrecomputedExtensionModelKt.executeRegisterTaskForOldContent(module, it -> {
        registerPluginActions(it, keymapManager);
        return Unit.INSTANCE;
      });
    }
  }

  private static @NotNull AnActionListener publisher() {
    return ApplicationManager.getApplication().getMessageBus().syncPublisher(AnActionListener.TOPIC);
  }

  private static @NotNull ActionManagerListener managerPublisher() {
    return ApplicationManager.getApplication().getMessageBus().syncPublisher(ActionManagerListener.TOPIC);
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

  private static @Nullable <T> T instantiate(@NotNull String stubClassName,
                                             @NotNull PluginDescriptor pluginDescriptor,
                                             @NotNull Class<T> expectedClass) {
    Object obj;
    try {
      obj = ApplicationManager.getApplication().instantiateClass(stubClassName, pluginDescriptor);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.error(e);
      return null;
    }

    if (expectedClass.isInstance(obj)) {
      //noinspection unchecked
      return (T)obj;
    }

    LOG.error(new PluginException("class with name '" + stubClassName + "' must be an instance of '" + expectedClass.getName() + "'; " +
                                  "got " + obj, pluginDescriptor.getPluginId()));
    return null;
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

  private static void processAbbreviationNode(@NotNull XmlElement e, @NotNull String id) {
    String abbr = e.attributes.get("value");
    if (!Strings.isEmpty(abbr)) {
      AbbreviationManagerImpl abbreviationManager = (AbbreviationManagerImpl)AbbreviationManager.getInstance();
      abbreviationManager.register(abbr, id, true);
    }
  }

  private static boolean isSecondary(XmlElement element) {
    return "true".equalsIgnoreCase(element.attributes.get("secondary"));
  }

  private static void setIconFromClass(@Nullable Class<?> actionClass,
                                       @NotNull PluginDescriptor module,
                                       @NotNull String iconPath,
                                       @NotNull Presentation presentation) {
    long start = StartUpMeasurer.getCurrentTimeIfEnabled();
    Icon icon = IconLoader.findIcon(iconPath, actionClass, module.getClassLoader(), null, true);
    if (icon == null) {
      reportActionError(module, "Icon cannot be found in '" + iconPath + "', action '" + actionClass + "'");
      icon = AllIcons.Nodes.Unknown;
    }
    IconLoadMeasurer.actionIcon.end(start);
    presentation.setIcon(icon);
  }

  @SuppressWarnings("HardCodedStringLiteral")
  private static @NlsActions.ActionDescription String computeDescription(@Nullable ResourceBundle bundle,
                                                                         String id,
                                                                         String elementType,
                                                                         String descriptionValue,
                                                                         @NotNull ClassLoader classLoader) {
    if (bundle != null && DefaultBundleService.isDefaultBundle()) {
      bundle = DynamicBundle.INSTANCE.getResourceBundle(bundle.getBaseBundleName(), classLoader);
    }
    return AbstractBundle.messageOrDefault(bundle, elementType + "." + id + "." + DESCRIPTION, Strings.notNullize(descriptionValue));
  }

  @SuppressWarnings("HardCodedStringLiteral")
  private static @NlsActions.ActionText String computeActionText(@Nullable ResourceBundle bundle,
                                                                 String id,
                                                                 String elementType,
                                                                 @Nullable String textValue,
                                                                 @NotNull ClassLoader classLoader) {
    String defaultValue = Strings.notNullize(textValue);
    if (bundle != null && DefaultBundleService.isDefaultBundle()) {
      bundle = DynamicBundle.INSTANCE.getResourceBundle(bundle.getBaseBundleName(), classLoader);
    }
    return bundle == null ? defaultValue : AbstractBundle.messageOrDefault(bundle, elementType + "." + id + "." + TEXT_ATTR_NAME, defaultValue);
  }

  private static @Nullable Anchor parseAnchor(String anchorStr, @Nullable String actionName, @NotNull IdeaPluginDescriptor module) {
    if (anchorStr == null) {
      return Anchor.LAST;
    }

    if ("first".equalsIgnoreCase(anchorStr)) {
      return Anchor.FIRST;
    }
    else if ("last".equalsIgnoreCase(anchorStr)) {
      return Anchor.LAST;
    }
    else if ("before".equalsIgnoreCase(anchorStr)) {
      return Anchor.BEFORE;
    }
    else if ("after".equalsIgnoreCase(anchorStr)) {
      return Anchor.AFTER;
    }
    else {
      reportActionError(module,
                        actionName + ": anchor should be one of the following constants: \"first\", \"last\", \"before\" or \"after\"");
      return null;
    }
  }

  private static void processMouseShortcutNode(@NotNull XmlElement element,
                                               String actionId,
                                               @NotNull IdeaPluginDescriptor module,
                                               @NotNull KeymapManager keymapManager) {
    String keystrokeString = element.attributes.get("keystroke");
    if (keystrokeString == null || keystrokeString.trim().isEmpty()) {
      reportActionError(module, "\"keystroke\" attribute must be specified for action with id=" + actionId);
      return;
    }
    MouseShortcut shortcut;
    try {
      shortcut = KeymapUtil.parseMouseShortcut(keystrokeString);
    }
    catch (Exception ex) {
      reportActionError(module, "\"keystroke\" attribute has invalid value for action with id=" + actionId);
      return;
    }

    String keymapName = element.attributes.get(KEYMAP_ATTR_NAME);
    if (keymapName == null || keymapName.isEmpty()) {
      reportActionError(module, "attribute \"keymap\" should be defined");
      return;
    }
    Keymap keymap = keymapManager.getKeymap(keymapName);
    if (keymap == null) {
      reportKeymapNotFoundWarning(module, keymapName);
      return;
    }
    processRemoveAndReplace(element, actionId, keymap, shortcut);
  }

  private static void reportActionError(@NotNull PluginDescriptor module, @NotNull String message) {
    reportActionError(module, message, null);
  }

  private static void reportActionError(@NotNull PluginDescriptor module, @NotNull String message, @Nullable Throwable cause) {
    LOG.error(new PluginException(message + " (module=" + module + ")", cause, module.getPluginId()));
  }

  private static void reportKeymapNotFoundWarning(@NotNull PluginDescriptor module, @NotNull String keymapName) {
    if (!DefaultKeymap.Companion.isBundledKeymapHidden(keymapName)) {
      LOG.warn("keymap \"" + keymapName + "\" not found" + " " + module);
    }
  }

  private static @NotNull String getPluginInfo(@Nullable PluginId id) {
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
    //noinspection deprecation
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
  public void addTimerListener(final @NotNull TimerListener listener) {
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

  public @NotNull ActionPopupMenu createActionPopupMenu(@NotNull String place, @NotNull ActionGroup group, @Nullable PresentationFactory presentationFactory) {
    return new ActionPopupMenuImpl(place, group, this, presentationFactory);
  }

  @Override
  public @NotNull ActionPopupMenu createActionPopupMenu(@NotNull String place, @NotNull ActionGroup group) {
    return new ActionPopupMenuImpl(place, group, this, null);
  }

  @Override
  public @NotNull ActionToolbar createActionToolbar(final @NotNull String place, final @NotNull ActionGroup group, final boolean horizontal) {
    return createActionToolbar(place, group, horizontal, false);
  }

  @Override
  public @NotNull ActionToolbar createActionToolbar(@NotNull String place, @NotNull ActionGroup group, boolean horizontal, boolean decorateButtons) {
    ActionToolbar toolbar = new ActionToolbarImpl(place, group, horizontal, decorateButtons);
    managerPublisher().toolbarCreated(place, group, horizontal, toolbar);
    return toolbar;
  }

  private void registerPluginActions(@NotNull IdeaPluginDescriptorImpl module, @NotNull KeymapManagerEx keymapManager) {
    List<RawPluginDescriptor.ActionDescriptor> elements = module.actions;
    if (elements.isEmpty()) {
      return;
    }

    long startTime = StartUpMeasurer.getCurrentTime();

    String lastBundleName = null;
    ResourceBundle lastBundle = null;
    for (RawPluginDescriptor.ActionDescriptor descriptor : elements) {
      String bundleName = descriptor.resourceBundle;
      if (bundleName == null) {
        bundleName = PluginManagerCore.CORE_ID.equals(module.getPluginId()) ? "messages.ActionsBundle" : module.getResourceBundleBaseName();
      }

      XmlElement element = descriptor.element;

      ResourceBundle bundle;
      if (bundleName == null) {
        bundle = null;
      }
      else if (bundleName.equals(lastBundleName)) {
        bundle = lastBundle;
      }
      else {
        try {
          bundle = DynamicBundle.INSTANCE.getResourceBundle(bundleName, module.getClassLoader());
          lastBundle = bundle;
          lastBundleName = bundleName;
        }
        catch (MissingResourceException e) {
          LOG.error(new PluginException("Cannot resolve resource bundle " + bundleName + " for action " + element, e, module.getPluginId()));
          bundle = null;
        }
      }

      switch (descriptor.name) {
        case ACTION_ELEMENT_NAME:
          processActionElement(element, module, bundle, keymapManager, module.getClassLoader());
          break;
        case GROUP_ELEMENT_NAME:
          processGroupElement(element, module, bundle, keymapManager, module.getClassLoader());
          break;
        case SEPARATOR_ELEMENT_NAME:
          processSeparatorNode(null, element, module, bundle);
          break;
        case REFERENCE_ELEMENT_NAME:
          processReferenceNode(element, module, bundle);
          break;
        case "unregister":
          processUnregisterNode(element, module);
          break;
        case "prohibit":
          processProhibitNode(element, module);
          break;
        default:
          LOG.error(new PluginException("Unexpected name of element" + descriptor.name, module.getPluginId()));
          break;
      }
    }
    StartUpMeasurer.addPluginCost(module.getPluginId().getIdString(), "Actions", StartUpMeasurer.getCurrentTime() - startTime);
  }

  @Override
  public @Nullable AnAction getAction(@NotNull String id) {
    return getActionImpl(id, false);
  }

  private @Nullable AnAction getActionImpl(@NotNull String id, boolean canReturnStub) {
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

  private @Nullable AnAction replaceStub(@NotNull ActionStubBase stub, @NotNull AnAction anAction) {
    LOG.assertTrue(actionToId.containsKey(stub));
    actionToId.remove(stub);

    LOG.assertTrue(idToAction.containsKey(stub.getId()));

    AnAction action = idToAction.remove(stub.getId());
    LOG.assertTrue(action != null);
    LOG.assertTrue(action.equals(stub));

    actionToId.put(anAction, stub.getId());
    updateHandlers(anAction);

    AnAction result = addToMap(stub.getId(),
                               anAction,
                               stub instanceof ActionStub ? ((ActionStub)stub).getProjectType() : null);
    if (result == null) {
      reportActionIdCollision(stub.getId(), action, stub.getPlugin().getPluginId());
    }
    return result;
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

  @Override
  public @NotNull JComponent createButtonToolbar(final @NotNull String actionPlace, final @NotNull ActionGroup messageActionGroup) {
    //noinspection deprecation
    return new ButtonToolbarImpl(actionPlace, messageActionGroup);
  }

  @Override
  public AnAction getActionOrStub(@NotNull String id) {
    return getActionImpl(id, true);
  }

  /**
   * @return instance of ActionGroup or ActionStub. The method never returns real subclasses of {@code AnAction}.
   */
  private @Nullable AnAction processActionElement(@NotNull XmlElement element,
                                                  @NotNull IdeaPluginDescriptorImpl module,
                                                  @Nullable ResourceBundle bundle,
                                                  @NotNull KeymapManager keymapManager,
                                                  @NotNull ClassLoader classLoader) {
    String className = element.attributes.get(CLASS_ATTR_NAME);
    if (className == null || className.isEmpty()) {
      reportActionError(module, "action element should have specified \"class\" attribute");
      return null;
    }

    // read ID and register loaded action
    String id = obtainActionId(element, className);
    synchronized (myLock) {
      if (myProhibitedActionIds.contains(id)) {
        return null;
      }
    }
    if (Boolean.parseBoolean(element.attributes.get(INTERNAL_ATTR_NAME)) &&
        !ApplicationManager.getApplication().isInternal()) {
      myNotRegisteredInternalActionIds.add(id);
      return null;
    }

    String iconPath = element.attributes.get(ICON_ATTR_NAME);
    String projectType = element.attributes.get(PROJECT_TYPE);

    String textValue = element.attributes.get(TEXT_ATTR_NAME);
    //noinspection HardCodedStringLiteral
    String descriptionValue = element.attributes.get(DESCRIPTION);

    ActionStub stub = new ActionStub(className, id, module, iconPath, ProjectType.create(projectType), () -> {
      Supplier<String> text = () -> computeActionText(bundle, id, ACTION_ELEMENT_NAME, textValue, classLoader);
      if (text.get() == null) {
        LOG.error(new PluginException("'text' attribute is mandatory (actionId=" + id + ", module=" + " " + module + ")",
                                      module.getPluginId()));
      }

      Presentation presentation = Presentation.newTemplatePresentation();
      presentation.setText(text);
      if (bundle == null) {
        presentation.setDescription(descriptionValue);
      }
      else {
        presentation.setDescription(() -> computeDescription(bundle, id, ACTION_ELEMENT_NAME, descriptionValue, classLoader));
      }
      return presentation;
    });

    // process all links and key bindings if any
    for (XmlElement e : element.children) {
      switch (e.name) {
        case ADD_TO_GROUP_ELEMENT_NAME:
          processAddToGroupNode(stub, e, module, isSecondary(e));
          break;
        case "keyboard-shortcut":
          processKeyboardShortcutNode(e, id, module, keymapManager);
          break;
        case "mouse-shortcut":
          processMouseShortcutNode(e, id, module, keymapManager);
          break;
        case "abbreviation":
          processAbbreviationNode(e, id);
          break;
        case OVERRIDE_TEXT_ELEMENT_NAME:
          processOverrideTextNode(stub, stub.getId(), e, module, bundle);
          break;
        case SYNONYM_ELEMENT_NAME:
          processSynonymNode(stub, e, module, bundle);
          break;
        default:
          reportActionError(module, "unexpected name of element \"" + e.name + "\"");
          return null;
      }
    }

    String shortcutOfActionId = element.attributes.get(USE_SHORTCUT_OF_ATTR_NAME);
    if (shortcutOfActionId != null) {
      keymapManager.bindShortcuts(shortcutOfActionId, id);
    }

    registerOrReplaceActionInner(element, id, stub, module);
    return stub;
  }

  private static String obtainActionId(XmlElement element, String className) {
    String id = element.attributes.get(ID_ATTR_NAME);
    return Strings.isEmpty(id) ? StringUtilRt.getShortName(className) : id;
  }

  private void registerOrReplaceActionInner(@NotNull XmlElement element,
                                            @NotNull String id,
                                            @NotNull AnAction action,
                                            @NotNull IdeaPluginDescriptor plugin) {
    synchronized (myLock) {
      if (myProhibitedActionIds.contains(id)) {
        return;
      }
      if (Boolean.parseBoolean(element.attributes.get("overrides"))) {
        if (getActionOrStub(id) == null) {
          LOG.error(element + " '" + id + "' doesn't override anything");
          return;
        }
        AnAction prev = replaceAction(id, action, plugin.getPluginId());
        if (action instanceof DefaultActionGroup && prev instanceof DefaultActionGroup) {
          if (Boolean.parseBoolean(element.attributes.get("keep-content"))) {
            ((DefaultActionGroup)action).copyFromGroup((DefaultActionGroup)prev);
          }
        }
      }
      else {
        registerAction(id, action, plugin.getPluginId(), element.attributes.get(PROJECT_TYPE));
      }
      ActionsCollectorImpl.onActionLoadedFromXml(action, id, plugin);
    }
  }

  private AnAction processGroupElement(@NotNull XmlElement element,
                                       @NotNull IdeaPluginDescriptorImpl module,
                                       @Nullable ResourceBundle bundle,
                                       @NotNull KeymapManagerEx keymapManager,
                                       @NotNull ClassLoader classLoader) {
    String className = element.attributes.get(CLASS_ATTR_NAME);
    if (className == null) {
      // use default group if class isn't specified
      className = "true".equals(element.attributes.get("compact"))
                  ? DefaultCompactActionGroup.class.getName()
                  : DefaultActionGroup.class.getName();
    }

    try {
      String id = element.attributes.get(ID_ATTR_NAME);
      if (id != null && id.isEmpty()) {
        reportActionError(module, "ID of the group cannot be an empty string");
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
        Object obj = ApplicationManager.getApplication().instantiateClass(className, module);
        if (!(obj instanceof ActionGroup)) {
          reportActionError(module, "class with name \"" + className + "\" should be instance of " + ActionGroup.class.getName());
          return null;
        }
        if (element.children.size() != element.count(ADD_TO_GROUP_ELEMENT_NAME)) {  //
          if (!(obj instanceof DefaultActionGroup)) {
            reportActionError(module, "class with name \"" + className + "\" should be instance of " + DefaultActionGroup.class.getName() +
                                        " because there are children specified");
            return null;
          }
        }
        customClass = true;
        group = (ActionGroup)obj;
      }
      else {
        group = new ActionGroupStub(id, className, module);
        customClass = true;
      }
      // read ID and register loaded group
      if (Boolean.parseBoolean(element.attributes.get(INTERNAL_ATTR_NAME)) && !ApplicationManager.getApplication().isInternal()) {
        myNotRegisteredInternalActionIds.add(id);
        return null;
      }

      if (id == null) {
        id = "<anonymous-group-" + myAnonymousGroupIdCounter++ + ">";
      }

      registerOrReplaceActionInner(element, id, group, module);
      Presentation presentation = group.getTemplatePresentation();
      String finalId = id;

      // text
      Supplier<String> text = () -> computeActionText(bundle, finalId, GROUP_ELEMENT_NAME, element.attributes.get(TEXT_ATTR_NAME), classLoader);
      // don't override value which was set in API with empty value from xml descriptor
      if (!Strings.isEmpty(text.get()) || presentation.getText() == null) {
        presentation.setText(text);
      }

      // description
      String description = element.attributes.get(DESCRIPTION); //NON-NLS
      if (bundle == null) {
        // don't override value which was set in API with empty value from xml descriptor
        if (!Strings.isEmpty(description) || presentation.getDescription() == null) {
          presentation.setDescription(description);
        }
      }
      else {
        Supplier<String> descriptionSupplier = () -> computeDescription(bundle, finalId, GROUP_ELEMENT_NAME, description, classLoader);
        // don't override value which was set in API with empty value from xml descriptor
        if (!Strings.isEmpty(descriptionSupplier.get()) || presentation.getDescription() == null) {
          presentation.setDescription(descriptionSupplier);
        }
      }

      // icon
      String iconPath = element.attributes.get(ICON_ATTR_NAME);
      if (group instanceof ActionGroupStub) {
        ((ActionGroupStub)group).setIconPath(iconPath);
      }
      else if (iconPath != null) {
        setIconFromClass(null, module, iconPath, presentation);
      }

      // popup
      String popup = element.attributes.get("popup");
      if (popup != null) {
        group.setPopup(Boolean.parseBoolean(popup));
        if (group instanceof ActionGroupStub) {
          ((ActionGroupStub)group).setPopupDefinedInXml(true);
        }
      }

      String searchable = element.attributes.get("searchable");
      if (searchable != null) {
        group.setSearchable(Boolean.parseBoolean(searchable));
      }

      String shortcutOfActionId = element.attributes.get(USE_SHORTCUT_OF_ATTR_NAME);
      if (customClass && shortcutOfActionId != null) {
        keymapManager.bindShortcuts(shortcutOfActionId, id);
      }

      // process all group's children. There are other groups, actions, references and links
      for (XmlElement child : element.children) {
        switch (child.name) {
          case ACTION_ELEMENT_NAME: {
            AnAction action = processActionElement(child, module, bundle, keymapManager, classLoader);
            if (action != null) {
              addToGroupInner(group, action, Constraints.LAST, module, isSecondary(child));
            }
            break;
          }
          case SEPARATOR_ELEMENT_NAME:
            processSeparatorNode((DefaultActionGroup)group, child, module, bundle);
            break;
          case GROUP_ELEMENT_NAME: {
            AnAction action = processGroupElement(child, module, bundle, keymapManager, classLoader);
            if (action != null) {
              addToGroupInner(group, action, Constraints.LAST, module, false);
            }
            break;
          }
          case ADD_TO_GROUP_ELEMENT_NAME:
            processAddToGroupNode(group, child, module, isSecondary(child));
            break;
          case REFERENCE_ELEMENT_NAME:
            AnAction action = processReferenceElement(child, module);
            if (action != null) {
              addToGroupInner(group, action, Constraints.LAST, module, isSecondary(child));
            }
            break;
          case OVERRIDE_TEXT_ELEMENT_NAME:
            processOverrideTextNode(group, id, child, module, bundle);
            break;
          default:
            reportActionError(module, "unexpected name of element \"" + child.name + "\n");
            return null;
        }
      }
      return group;
    }
    catch (Exception e) {
      String message = "cannot create class \"" + className + "\"";
      reportActionError(module, message, e);
      return null;
    }
  }

  private void processReferenceNode(@NotNull XmlElement element, @NotNull IdeaPluginDescriptor module, @Nullable ResourceBundle bundle) {
    AnAction action = processReferenceElement(element, module);
    if (action == null) {
      return;
    }

    for (XmlElement child : element.children) {
      if (ADD_TO_GROUP_ELEMENT_NAME.equals(child.name)) {
        processAddToGroupNode(action, child, module, isSecondary(child));
      }
      else if (SYNONYM_ELEMENT_NAME.equals(child.name)) {
        processSynonymNode(action, child, module, bundle);
      }
    }
  }

  /**
   * @param element description of link
   */
  private void processAddToGroupNode(AnAction action, XmlElement element, @NotNull IdeaPluginDescriptor module, boolean secondary) {
    String name = action instanceof ActionStub ? ((ActionStub)action).getClassName() : action.getClass().getName();
    String id = action instanceof ActionStub ? ((ActionStub)action).getId() : actionToId.get(action);
    String actionName = name + " (" + id + ")";

    // parent group
    final AnAction parentGroup = getParentGroup(element.attributes.get(GROUP_ID_ATTR_NAME), actionName, module);
    if (parentGroup == null) {
      return;
    }

    // anchor attribute
    final Anchor anchor = parseAnchor(element.attributes.get("anchor"), actionName, module);
    if (anchor == null) {
      return;
    }

    final String relativeToActionId = element.attributes.get("relative-to-action");
    if ((Anchor.BEFORE == anchor || Anchor.AFTER == anchor) && relativeToActionId == null) {
      reportActionError(module, actionName + ": \"relative-to-action\" cannot be null if anchor is \"after\" or \"before\"");
      return;
    }
    addToGroupInner(parentGroup, action, new Constraints(anchor, relativeToActionId), module, secondary);
  }

  private void addToGroupInner(@NotNull AnAction group, @NotNull AnAction action, @NotNull Constraints constraints,
                               @Nullable IdeaPluginDescriptor module, boolean secondary) {
    try {
      String actionId = action instanceof ActionStub ? ((ActionStub)action).getId() : actionToId.get(action);
      DefaultActionGroup actionGroup = (DefaultActionGroup)group;
      if (module != null && actionGroup.containsAction(action)) {
        reportActionError(module, "Cannot add an action twice: " + actionId + " (" +
                                  (action instanceof ActionStub ? ((ActionStub)action).getClassName() : action.getClass().getName()) + ")");
        return;
      }
      actionGroup.addAction(action, constraints, this).setAsSecondary(secondary);
      idToGroupId.putValue(actionId, actionToId.get(group));
    }
    catch (IllegalArgumentException e) {
      if (module != null) reportActionError(module, e.getMessage(), e);
      else throw e;
    }
  }

  public void addToGroup(@NotNull DefaultActionGroup group, @NotNull AnAction action, @NotNull Constraints constraints) {
    addToGroupInner(group, action, constraints, null, false);
  }

  public @Nullable DefaultActionGroup getParentGroup(String groupId,
                                                     @Nullable String actionName,
                                                     @NotNull IdeaPluginDescriptor module) {
    if (groupId == null || groupId.isEmpty()) {
      reportActionError(module, actionName + ": attribute \"group-id\" should be defined");
      return null;
    }
    AnAction parentGroup = getActionImpl(groupId, true);
    if (parentGroup == null) {
      reportActionError(module, actionName + ": group with id \"" + groupId + "\" isn't registered; action will be added to the \"Other\" group", null);
      parentGroup = getActionImpl(IdeActions.GROUP_OTHER_MENU, true);
    }
    if (!(parentGroup instanceof DefaultActionGroup)) {
      reportActionError(module, actionName + ": group with id \"" + groupId + "\" should be instance of " + DefaultActionGroup.class.getName() +
                                  " but was " + (parentGroup != null ? parentGroup.getClass() : "[null]"));
      return null;
    }
    return (DefaultActionGroup)parentGroup;
  }

  private static void processOverrideTextNode(AnAction action,
                                              String id,
                                              XmlElement element,
                                              @NotNull IdeaPluginDescriptor module,
                                              @Nullable ResourceBundle bundle) {
    String place = element.attributes.get("place");
    if (place == null) {
      reportActionError(module, id + ": override-text specified without place");
      return;
    }
    String useTextOfPlace = element.attributes.get("use-text-of-place");
    if (useTextOfPlace != null) {
      action.copyActionTextOverride(useTextOfPlace, place, id);
    }
    else {
      String text = element.attributes.get(TEXT_ATTR_NAME);
      if ((text == null || text.isEmpty()) && bundle != null) {
        String prefix = action instanceof ActionGroup ? "group" : "action";
        String key = prefix + "." + id + "." + place + ".text";
        action.addTextOverride(place, () -> BundleBase.message(bundle, key));
      }
      else {
        action.addTextOverride(place, () -> text);
      }
    }
  }

  private static void processSynonymNode(AnAction action,
                                         XmlElement element,
                                         @NotNull IdeaPluginDescriptor module,
                                         @Nullable ResourceBundle bundle) {
    @SuppressWarnings("HardCodedStringLiteral")
    String text = element.attributes.get(TEXT_ATTR_NAME);
    if (text != null && !text.isEmpty()) {
      action.addSynonym(() -> text);
    }
    else {
      String key = element.attributes.get(KEY_ATTR_NAME);
      if (key != null && bundle != null) {
        action.addSynonym(() -> BundleBase.message(bundle, key));
      }
      else {
        reportActionError(module, "Can't process synonym: neither text nor resource bundle key is specified");
      }
    }
  }

  /**
   * @param parentGroup group which is the parent of the separator. It can be {@code null} in that
   *                    case separator will be added to group described in the <add-to-group ....> sub element.
   * @param element     XML element which represent separator.
   */
  private void processSeparatorNode(@Nullable DefaultActionGroup parentGroup,
                                    @NotNull XmlElement element,
                                    @NotNull IdeaPluginDescriptor module,
                                    @Nullable ResourceBundle bundle) {
    //noinspection HardCodedStringLiteral
    String text = element.attributes.get(TEXT_ATTR_NAME);
    String key = element.attributes.get(KEY_ATTR_NAME);
    Separator separator =
      text != null ? new Separator(text) : key != null ? createSeparator(bundle, key) : Separator.getInstance();
    if (parentGroup != null) {
      parentGroup.add(separator, this);
    }
    // try to find inner <add-to-parent...> tag
    for (XmlElement child : element.children) {
      if (ADD_TO_GROUP_ELEMENT_NAME.equals(child.name)) {
        processAddToGroupNode(separator, child, module, isSecondary(child));
      }
    }
  }

  private static @NotNull Separator createSeparator(@Nullable ResourceBundle bundle, @NotNull String key) {
    String text = bundle != null ? AbstractBundle.messageOrNull(bundle, key) : null;
    return text != null ? new Separator(text) : Separator.getInstance();
  }

  private void processProhibitNode(XmlElement element, @NotNull IdeaPluginDescriptor module) {
    String id = element.attributes.get(ID_ATTR_NAME);
    if (id == null) {
      reportActionError(module, "'id' attribute is required for 'unregister' elements");
      return;
    }

    prohibitAction(id);
  }

  private void processUnregisterNode(XmlElement element, @NotNull IdeaPluginDescriptor module) {
    String id = element.attributes.get(ID_ATTR_NAME);
    if (id == null) {
      reportActionError(module, "'id' attribute is required for 'unregister' elements");
      return;
    }
    AnAction action = getAction(id);
    if (action == null) {
      reportActionError(module, "Trying to unregister non-existing action " + id);
      return;
    }

    AbbreviationManager.getInstance().removeAllAbbreviations(id);
    unregisterAction(id);
  }

  private static void processKeyboardShortcutNode(XmlElement element,
                                                  String actionId,
                                                  @NotNull PluginDescriptor module,
                                                  @NotNull KeymapManager keymapManager) {
    String firstStrokeString = element.attributes.get("first-keystroke");
    if (firstStrokeString == null) {
      reportActionError(module, "\"first-keystroke\" attribute must be specified for action with id=" + actionId);
      return;
    }
    KeyStroke firstKeyStroke = getKeyStroke(firstStrokeString);
    if (firstKeyStroke == null) {
      reportActionError(module, "\"first-keystroke\" attribute has invalid value for action with id=" + actionId);
      return;
    }

    KeyStroke secondKeyStroke = null;
    String secondStrokeString = element.attributes.get("second-keystroke");
    if (secondStrokeString != null) {
      secondKeyStroke = getKeyStroke(secondStrokeString);
      if (secondKeyStroke == null) {
        reportActionError(module, "\"second-keystroke\" attribute has invalid value for action with id=" + actionId);
        return;
      }
    }

    String keymapName = element.attributes.get(KEYMAP_ATTR_NAME);
    if (keymapName == null || keymapName.trim().isEmpty()) {
      reportActionError(module, "attribute \"keymap\" should be defined");
      return;
    }
    Keymap keymap = keymapManager.getKeymap(keymapName);
    if (keymap == null) {
      reportKeymapNotFoundWarning(module, keymapName);
      return;
    }
    processRemoveAndReplace(element, actionId, keymap, new KeyboardShortcut(firstKeyStroke, secondKeyStroke));
  }

  private static void processRemoveAndReplace(@NotNull XmlElement element, String actionId, @NotNull Keymap keymap, @NotNull Shortcut shortcut) {
    boolean remove = Boolean.parseBoolean(element.attributes.get("remove"));
    boolean replace = Boolean.parseBoolean(element.attributes.get("replace-all"));
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

  private @Nullable AnAction processReferenceElement(@NotNull XmlElement element, @NotNull IdeaPluginDescriptor module) {
    String ref = getReferenceActionId(element);
    if (ref == null || ref.isEmpty()) {
      reportActionError(module, "ID of reference element should be defined", null);
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
        reportActionError(module, "action specified by reference isn't registered (ID=" + ref + ")", null);
      }
      return null;
    }
    return action;
  }

  private static String getReferenceActionId(@NotNull XmlElement element) {
    String ref = element.attributes.get(REF_ATTR_NAME);
    if (ref == null) {
      // support old style references by id
      ref = element.attributes.get(ID_ATTR_NAME);
    }
    return ref;
  }

  @ApiStatus.Internal
  public static @Nullable String checkUnloadActions(@NotNull IdeaPluginDescriptorImpl module) {
    List<RawPluginDescriptor.ActionDescriptor> descriptors = module.actions;
    for (RawPluginDescriptor.ActionDescriptor descriptor : descriptors) {
      XmlElement element = descriptor.element;
      String elementName = descriptor.name;
      if (!elementName.equals(ACTION_ELEMENT_NAME) &&
          !(elementName.equals(GROUP_ELEMENT_NAME) && canUnloadGroup(element)) &&
          !elementName.equals(REFERENCE_ELEMENT_NAME)) {
        return "Plugin " + module + " is not unload-safe because of action element " + elementName;
      }
    }
    return null;
  }

  private static boolean canUnloadGroup(@NotNull XmlElement element) {
    if (element.attributes.get(ID_ATTR_NAME) == null) {
      return false;
    }
    for (XmlElement child : element.children) {
      if (child.name.equals(GROUP_ELEMENT_NAME) && !canUnloadGroup(child)) {
        return false;
      }
    }
    return true;
  }

  public void unloadActions(@NotNull IdeaPluginDescriptorImpl module) {
    List<RawPluginDescriptor.ActionDescriptor> descriptors = module.actions;
    for (int i = descriptors.size() - 1; i >= 0; i--) {
      RawPluginDescriptor.ActionDescriptor descriptor = descriptors.get(i);
      XmlElement element = descriptor.element;
      switch (descriptor.name) {
        case ACTION_ELEMENT_NAME:
          unloadActionElement(element);
          break;
        case GROUP_ELEMENT_NAME:
          unloadGroupElement(element);
          break;
        case REFERENCE_ELEMENT_NAME:
          AnAction action = processReferenceElement(element, module);
          if (action == null) {
            return;
          }

          String actionId = getReferenceActionId(element);

          for (XmlElement child : element.children) {
            if (!child.name.equals(ADD_TO_GROUP_ELEMENT_NAME)) {
              continue;
            }

            String groupId = child.attributes.get(GROUP_ID_ATTR_NAME);
            DefaultActionGroup parentGroup = getParentGroup(groupId, actionId, module);
            if (parentGroup == null) {
              return;
            }
            parentGroup.remove(action);
            idToGroupId.remove(actionId, groupId);
          }
          break;
      }
    }
  }

  private void unloadGroupElement(XmlElement element) {
    String id = element.attributes.get(ID_ATTR_NAME);
    if (id == null) {
      throw new IllegalStateException("Cannot unload groups with no ID");
    }
    for (XmlElement groupChild : element.children) {
      if (groupChild.name.equals(ACTION_ELEMENT_NAME)) {
        unloadActionElement(groupChild);
      }
      else if (groupChild.name.equals(GROUP_ELEMENT_NAME)) {
        unloadGroupElement(groupChild);
      }
    }
    unregisterAction(id);
  }

  private void unloadActionElement(@NotNull XmlElement element) {
    String className = element.attributes.get(CLASS_ATTR_NAME);
    String id = obtainActionId(element, className);
    unregisterAction(id);
  }

  @Override
  public void registerAction(@NotNull String actionId, @NotNull AnAction action, @Nullable PluginId pluginId) {
    registerAction(actionId, action, pluginId, null);
  }

  private void registerAction(@NotNull String actionId,
                              @NotNull AnAction action,
                              @Nullable PluginId pluginId,
                              @Nullable String projectType) {
    synchronized (myLock) {
      if (myProhibitedActionIds.contains(actionId)) {
        return;
      }

      if (addToMap(actionId, action, ProjectType.create(projectType)) == null) {
        reportActionIdCollision(actionId, action, pluginId);
        return;
      }

      if (actionToId.containsKey(action)) {
        IdeaPluginDescriptorImpl module = pluginId == null ? null : PluginManagerCore.getPluginSet().findEnabledPlugin(pluginId);
        String message = "ID '" + actionToId.get(action) + "' is already taken by action '" + action + "' (" + action.getClass()+"). " +
                         "ID '" + actionId + "' cannot be registered for the same action";
        if (module == null) {
          LOG.error(new PluginException(message + " " + pluginId, null, pluginId));
        }
        else {
          reportActionError(module, message);
        }
        return;
      }

      action.registerCustomShortcutSet(new ProxyShortcutSet(actionId), null);
      idToIndex.put(actionId, myRegisteredActionsCount++);
      actionToId.put(action, actionId);
      if (pluginId != null) {
        pluginToId.putValue(pluginId, actionId);
      }
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

  private @Nullable AnAction addToMap(@NotNull String actionId, @NotNull AnAction action, @Nullable ProjectType projectType) {
    AnAction chameleonAction = idToAction.computeIfPresent(actionId, (__, old) -> old instanceof ChameleonAction ? old : new ChameleonAction(old, projectType));
    if (chameleonAction == null) {
      AnAction result = projectType == null ? action : new ChameleonAction(action, projectType);
      idToAction.put(actionId, result);
      return result;
    }
    else {
      return ((ChameleonAction)chameleonAction).addAction(action, projectType);
    }
  }

  private void reportActionIdCollision(@NotNull String actionId, @NotNull AnAction action, @Nullable PluginId pluginId) {
    String oldPluginInfo = pluginToId.entrySet()
      .stream()
      .filter(entry -> entry.getValue().contains(actionId))
      .map(Map.Entry::getKey)
      .map(ActionManagerImpl::getPluginInfo)
      .collect(Collectors.joining(","));

    AnAction oldAction = idToAction.get(actionId);
    String message = "ID '" + actionId + "' is already taken by action '" + oldAction + "' ("+oldAction.getClass()+") " + oldPluginInfo + ". " +
                     "Action '" + action + "' (" + action.getClass() + ") cannot use the same ID " + pluginId;
    if (pluginId == null) {
      LOG.error(message);
    }
    else {
      LOG.error(new PluginException(message, null, pluginId));
    }
  }

  @Override
  public void registerAction(@NotNull String actionId, @NotNull AnAction action) {
    registerAction(actionId, action, null, null);
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
                if (stub instanceof ActionGroupStub && groupId.equals(((ActionGroupStub)stub).getId())) {
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
   * Unregisters already registered action and prevents the action from being registered in the future.
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

  @Override
  public @NotNull Comparator<String> getRegistrationOrderComparator() {
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
    PluginDescriptor plugin = callerClass == null ? null : PluginManager.getPluginByClass(callerClass);
    replaceAction(actionId, newAction, plugin == null ? null : plugin.getPluginId());
  }

  private AnAction replaceAction(@NotNull String actionId, @NotNull AnAction newAction, @Nullable PluginId pluginId) {
    synchronized (myLock) {
      if (myProhibitedActionIds.contains(actionId)) {
        return null;
      }
    }

    AnAction oldAction = newAction instanceof OverridingAction ? getAction(actionId) : getActionOrStub(actionId);
    int oldIndex = idToIndex.getOrDefault(actionId, -1);  // Valid indices >= 0
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
    if (oldIndex >= 0) {
      idToIndex.put(actionId, oldIndex);
    }
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
  public void fireBeforeActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event) {
    myPrevPerformedActionId = myLastPreformedActionId;
    myLastPreformedActionId = getId(action);
    if (myLastPreformedActionId == null && action instanceof ActionIdProvider) {
      myLastPreformedActionId = ((ActionIdProvider)action).getId();
    }
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    IdeaLogger.ourLastActionId = myLastPreformedActionId;
    try (AccessToken ignore = ProhibitAWTEvents.start("fireBeforeActionPerformed")) {
      for (AnActionListener listener : myActionListeners) {
        listener.beforeActionPerformed(action, event);
      }
      publisher().beforeActionPerformed(action, event);
      ActionsCollectorImpl.onBeforeActionInvoked(action, event);
    }
  }

  @Override
  public void fireAfterActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event, @NotNull AnActionResult result) {
    myPrevPerformedActionId = myLastPreformedActionId;
    myLastPreformedActionId = getId(action);
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    IdeaLogger.ourLastActionId = myLastPreformedActionId;
    try (AccessToken ignore = ProhibitAWTEvents.start("fireAfterActionPerformed")) {
      ActionsCollectorImpl.onAfterActionInvoked(action, event, result);
      for (AnActionListener listener : myActionListeners) {
        listener.afterActionPerformed(action, event, result);
      }
      publisher().afterActionPerformed(action, event, result);
    }
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

  @Override
  public @NotNull ActionCallback tryToExecute(@NotNull AnAction action,
                                              @Nullable InputEvent inputEvent,
                                              @Nullable Component contextComponent,
                                              @Nullable String place,
                                              boolean now) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    ActionCallback result = new ActionCallback();
    Runnable doRunnable = () -> tryToExecuteNow(action, inputEvent, contextComponent, place, result);
    if (now) {
      doRunnable.run();
    }
    else {
      SwingUtilities.invokeLater(doRunnable);
    }

    return result;
  }

  private void tryToExecuteNow(@NotNull AnAction action, @Nullable InputEvent inputEvent, @Nullable Component contextComponent, String place, ActionCallback result) {
    Presentation presentation = action.getTemplatePresentation().clone();
    IdeFocusManager.findInstanceByContext(getContextBy(contextComponent)).doWhenFocusSettlesDown(() -> {
      ((TransactionGuardImpl)TransactionGuard.getInstance()).performUserActivity(() -> {
        DataContext context = getContextBy(contextComponent);

        AnActionEvent event = new AnActionEvent(
          inputEvent, context,
          place != null ? place : ActionPlaces.UNKNOWN,
          presentation, this,
          inputEvent == null ? 0 : inputEvent.getModifiersEx()
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
        UIUtil.addAwtListener(event1 -> {
          if (event1.getID() == WindowEvent.WINDOW_OPENED || event1.getID() == WindowEvent.WINDOW_ACTIVATED) {
            if (!result.isProcessed()) {
              final WindowEvent we = (WindowEvent)event1;
              IdeFocusManager.findInstanceByComponent(we.getWindow()).doWhenFocusSettlesDown(
                result.createSetDoneRunnable(), ModalityState.defaultModalityState());
            }
          }
        }, AWTEvent.WINDOW_EVENT_MASK, result);
        try {
          ActionUtil.performActionDumbAwareWithCallbacks(action, event);
        }
        finally {
          result.setDone();
        }
      });
    }, ModalityState.defaultModalityState());
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