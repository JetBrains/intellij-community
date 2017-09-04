/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.actionSystem.impl;

import com.intellij.AbstractBundle;
import com.intellij.CommonBundle;
import com.intellij.diagnostic.PluginException;
import com.intellij.ide.ActivityTracker;
import com.intellij.ide.DataManager;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.idea.IdeaLogger;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.ProjectType;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.pico.CachingConstructorInjectionComponentAdapter;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectIntHashMap;
import org.jdom.Element;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;

public final class ActionManagerImpl extends ActionManagerEx implements Disposable {
  @NonNls public static final String ACTION_ELEMENT_NAME = "action";
  @NonNls public static final String GROUP_ELEMENT_NAME = "group";
  @NonNls public static final String CLASS_ATTR_NAME = "class";
  @NonNls public static final String ID_ATTR_NAME = "id";
  @NonNls public static final String INTERNAL_ATTR_NAME = "internal";
  @NonNls public static final String ICON_ATTR_NAME = "icon";
  @NonNls public static final String ADD_TO_GROUP_ELEMENT_NAME = "add-to-group";
  @NonNls public static final String SHORTCUT_ELEMENT_NAME = "keyboard-shortcut";
  @NonNls public static final String MOUSE_SHORTCUT_ELEMENT_NAME = "mouse-shortcut";
  @NonNls public static final String DESCRIPTION = "description";
  @NonNls public static final String TEXT_ATTR_NAME = "text";
  @NonNls public static final String POPUP_ATTR_NAME = "popup";
  @NonNls public static final String COMPACT_ATTR_NAME = "compact";
  @NonNls public static final String SEPARATOR_ELEMENT_NAME = "separator";
  @NonNls public static final String REFERENCE_ELEMENT_NAME = "reference";
  @NonNls public static final String ABBREVIATION_ELEMENT_NAME = "abbreviation";
  @NonNls public static final String GROUPID_ATTR_NAME = "group-id";
  @NonNls public static final String ANCHOR_ELEMENT_NAME = "anchor";
  @NonNls public static final String FIRST = "first";
  @NonNls public static final String LAST = "last";
  @NonNls public static final String BEFORE = "before";
  @NonNls public static final String AFTER = "after";
  @NonNls public static final String SECONDARY = "secondary";
  @NonNls public static final String RELATIVE_TO_ACTION_ATTR_NAME = "relative-to-action";
  @NonNls public static final String FIRST_KEYSTROKE_ATTR_NAME = "first-keystroke";
  @NonNls public static final String SECOND_KEYSTROKE_ATTR_NAME = "second-keystroke";
  @NonNls public static final String REMOVE_SHORTCUT_ATTR_NAME = "remove";
  @NonNls public static final String REPLACE_SHORTCUT_ATTR_NAME = "replace-all";
  @NonNls public static final String KEYMAP_ATTR_NAME = "keymap";
  @NonNls public static final String KEYSTROKE_ATTR_NAME = "keystroke";
  @NonNls public static final String REF_ATTR_NAME = "ref";
  @NonNls public static final String VALUE_ATTR_NAME = "value";
  @NonNls public static final String ACTIONS_BUNDLE = "messages.ActionsBundle";
  @NonNls public static final String USE_SHORTCUT_OF_ATTR_NAME = "use-shortcut-of";
  @NonNls public static final String OVERRIDES_ATTR_NAME = "overrides";
  @NonNls public static final String KEEP_CONTENT_ATTR_NAME = "keep-content";
  @NonNls public static final String PROJECT_TYPE = "project-type";
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.actionSystem.impl.ActionManagerImpl");
  private static final int DEACTIVATED_TIMER_DELAY = 5000;
  private static final int TIMER_DELAY = 500;
  private static final int UPDATE_DELAY_AFTER_TYPING = 500;
  private final Object myLock = new Object();
  private final Map<String,AnAction> myId2Action = new THashMap<>();
  private final Map<PluginId, THashSet<String>> myPlugin2Id = new THashMap<>();
  private final TObjectIntHashMap<String> myId2Index = new TObjectIntHashMap<>();
  private final Map<Object,String> myAction2Id = new THashMap<>();
  private final MultiMap<String,String> myId2GroupId = new MultiMap<>();
  private final List<String> myNotRegisteredInternalActionIds = new ArrayList<>();
  private final List<AnActionListener> myActionListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final KeymapManagerEx myKeymapManager;
  private final DataManager myDataManager;
  private final List<ActionPopupMenuImpl> myPopups = new ArrayList<>();
  private final Map<AnAction, DataContext> myQueuedNotifications = new LinkedHashMap<>();
  private final Map<AnAction, AnActionEvent> myQueuedNotificationsEvents = new LinkedHashMap<>();
  private MyTimer myTimer;
  private int myRegisteredActionsCount;
  private String myLastPreformedActionId;
  private String myPrevPerformedActionId;
  private long myLastTimeEditorWasTypedIn;
  private boolean myTransparentOnlyUpdate;

  ActionManagerImpl(@NotNull KeymapManager keymapManager, DataManager dataManager) {
    myKeymapManager = (KeymapManagerEx)keymapManager;
    myDataManager = dataManager;

    registerPluginActions();
  }

  @Nullable
  static AnAction convertStub(ActionStub stub) {
    Object obj;
    String className = stub.getClassName();
    try {
      Class<?> aClass = Class.forName(className, true, stub.getLoader());
      obj = ReflectionUtil.newInstance(aClass);
    }
    catch (ClassNotFoundException e) {
      throw error(stub, e, "class with name ''{0}'' not found", className);
    }
    catch (NoClassDefFoundError e) {
      throw error(stub, e, "class with name ''{0}'' cannot be loaded", className);
    }
    catch(UnsupportedClassVersionError e) {
      throw error(stub, e, "error loading class ''{0}''", className);
    }
    catch (Exception e) {
      throw error(stub, e, "cannot create class ''{0}''", className);
    }

    if (!(obj instanceof AnAction)) {
      LOG.error("class with name '" + className + "' must be an instance of '" + AnAction.class.getName()+"'; got "+obj);
      return null;
    }

    AnAction anAction = (AnAction)obj;
    stub.initAction(anAction);
    if (StringUtil.isNotEmpty(stub.getText())) {
      anAction.getTemplatePresentation().setText(stub.getText());
    }
    String iconPath = stub.getIconPath();
    if (iconPath != null) {
      Class<? extends AnAction> actionClass = anAction.getClass();
      setIconFromClass(actionClass, actionClass.getClassLoader(), iconPath, anAction.getTemplatePresentation(), stub.getPluginId());
    }
    return anAction;
  }

  @NotNull
  @Contract(pure = true)
  private static RuntimeException error(@NotNull ActionStub stub, @NotNull Throwable original, @NotNull String template, @NotNull String className) {
    PluginId pluginId = stub.getPluginId();
    String text = MessageFormat.format(template, className);
    if (pluginId == null) {
      return new IllegalStateException(text);
    }
    return new PluginException(text, original, pluginId);
  }

  private static void processAbbreviationNode(Element e, String id) {
    final String abbr = e.getAttributeValue(VALUE_ATTR_NAME);
    if (!StringUtil.isEmpty(abbr)) {
      final AbbreviationManagerImpl abbreviationManager = (AbbreviationManagerImpl)AbbreviationManager.getInstance();
      abbreviationManager.register(abbr, id, true);
    }
  }

  @Nullable
  private static ResourceBundle getActionsResourceBundle(ClassLoader loader, IdeaPluginDescriptor plugin) {
    @NonNls final String resBundleName = plugin != null && !"com.intellij".equals(plugin.getPluginId().getIdString())
                                         ? plugin.getResourceBundleBaseName() : ACTIONS_BUNDLE;
    ResourceBundle bundle = null;
    if (resBundleName != null) {
      bundle = AbstractBundle.getResourceBundle(resBundleName, loader);
    }
    return bundle;
  }

  private static boolean isSecondary(Element element) {
    return "true".equalsIgnoreCase(element.getAttributeValue(SECONDARY));
  }

  private static void setIcon(@Nullable final String iconPath,
                              @NotNull String className,
                              @NotNull ClassLoader loader,
                              @NotNull Presentation presentation,
                              final PluginId pluginId) {
    if (iconPath == null) return;

    try {
      final Class actionClass = Class.forName(className, true, loader);
      setIconFromClass(actionClass, loader, iconPath, presentation, pluginId);
    }
    catch (ClassNotFoundException | NoClassDefFoundError e) {
      LOG.error(e);
      reportActionError(pluginId, "class with name \"" + className + "\" not found");
    }
  }

  private static void setIconFromClass(@NotNull final Class actionClass,
                                       @NotNull final ClassLoader classLoader,
                                       @NotNull final String iconPath,
                                       @NotNull Presentation presentation,
                                       final PluginId pluginId) {
    final IconLoader.LazyIcon lazyIcon = new IconLoader.LazyIcon() {
      @Override
      protected Icon compute() {
        //try to find icon in idea class path
        Icon icon = IconLoader.findIcon(iconPath, actionClass, true, false);
        if (icon == null) {
          icon = IconLoader.findIcon(iconPath, classLoader);
        }

        if (icon == null) {
          reportActionError(pluginId, "Icon cannot be found in '" + iconPath + "', action '" + actionClass + "'");
        }

        return icon;
      }

      @Override
      public String toString() {
        return "LazyIcon@ActionManagerImpl (path: " + iconPath + ", action class: " + actionClass + ")";
      }
    };

    if (!Registry.is("ide.lazyIconLoading")) {
      lazyIcon.load();
    }

    presentation.setIcon(lazyIcon);
  }

  private static String loadDescriptionForElement(final Element element, final ResourceBundle bundle, final String id, String elementType) {
    final String value = element.getAttributeValue(DESCRIPTION);
    if (bundle != null) {
      @NonNls final String key = elementType + "." + id + ".description";
      return CommonBundle.messageOrDefault(bundle, key, value == null ? "" : value);
    } else {
      return value;
    }
  }

  private static String loadTextForElement(final Element element, final ResourceBundle bundle, final String id, String elementType) {
    final String value = element.getAttributeValue(TEXT_ATTR_NAME);
    return CommonBundle.messageOrDefault(bundle, elementType + "." + id + "." + TEXT_ATTR_NAME, value == null ? "" : value);
  }

  public static boolean checkRelativeToAction(final String relativeToActionId,
                                       @NotNull final Anchor anchor,
                                       @NotNull final String actionName,
                                       @Nullable final PluginId pluginId) {
    if ((Anchor.BEFORE == anchor || Anchor.AFTER == anchor) && relativeToActionId == null) {
      reportActionError(pluginId, actionName + ": \"relative-to-action\" cannot be null if anchor is \"after\" or \"before\"");
      return false;
    }
    return true;
  }

  @Nullable
  public static Anchor parseAnchor(final String anchorStr,
                            @Nullable final String actionName,
                            @Nullable final PluginId pluginId) {
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

  private void processMouseShortcutNode(Element element, String actionId, PluginId pluginId) {
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
    Keymap keymap = myKeymapManager.getKeymap(keymapName);
    if (keymap == null) {
      reportActionError(pluginId, "keymap \"" + keymapName + "\" not found");
      return;
    }
    processRemoveAndReplace(element, actionId, keymap, shortcut);
  }

  private static void assertActionIsGroupOrStub(final AnAction action) {
    if (!(action instanceof ActionGroup || action instanceof ActionStub || action instanceof ChameleonAction)) {
      LOG.error("Action : " + action + "; class: " + action.getClass());
    }
  }

  private static void reportActionError(final PluginId pluginId, @NonNls @NotNull String message) {
    if (pluginId == null) {
      LOG.error(message);
    }
    else {
      LOG.error(new PluginException(message, null, pluginId));
    }
  }
  private static void reportActionWarning(final PluginId pluginId, @NonNls @NotNull String message) {
    if (pluginId == null) {
      LOG.warn(message);
    }
    else {
      LOG.warn(new PluginException(message, null, pluginId).getMessage());
    }
  }

  @NonNls
  private static String getPluginInfo(@Nullable PluginId id) {
    if (id != null) {
      final IdeaPluginDescriptor plugin = PluginManager.getPlugin(id);
      if (plugin != null) {
        String name = plugin.getName();
        if (name == null) {
          name = id.getIdString();
        }
        return " Plugin: " + name;
      }
    }
    return "";
  }

  private static DataContext getContextBy(Component contextComponent) {
    final DataManager dataManager = DataManager.getInstance();
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
  public void addTimerListener(int delay, final TimerListener listener) {
    _addTimerListener(listener, false);
  }

  @Override
  public void removeTimerListener(TimerListener listener) {
    _removeTimerListener(listener, false);
  }

  @Override
  public void addTransparentTimerListener(int delay, TimerListener listener) {
    _addTimerListener(listener, true);
  }

  @Override
  public void removeTransparentTimerListener(TimerListener listener) {
    _removeTimerListener(listener, true);
  }

  private void _addTimerListener(final TimerListener listener, boolean transparent) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    if (myTimer == null) {
      myTimer = new MyTimer();
      myTimer.start();
    }

    myTimer.addTimerListener(listener, transparent);
  }

  private void _removeTimerListener(TimerListener listener, boolean transparent) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    if (LOG.assertTrue(myTimer != null)) {
      myTimer.removeTimerListener(listener, transparent);
    }
  }

  public ActionPopupMenu createActionPopupMenu(String place, @NotNull ActionGroup group, @Nullable PresentationFactory presentationFactory) {
    return new ActionPopupMenuImpl(place, group, this, presentationFactory);
  }

  @Override
  public ActionPopupMenu createActionPopupMenu(String place, @NotNull ActionGroup group) {
    return new ActionPopupMenuImpl(place, group, this, null);
  }

  @Override
  public ActionToolbar createActionToolbar(final String place, @NotNull final ActionGroup group, final boolean horizontal) {
    return createActionToolbar(place, group, horizontal, false);
  }

  @Override
  public ActionToolbar createActionToolbar(final String place, @NotNull final ActionGroup group, final boolean horizontal, final boolean decorateButtons) {
    return new ActionToolbarImpl(place, group, horizontal, decorateButtons, myDataManager, this, myKeymapManager);
  }

  private void registerPluginActions() {
    final IdeaPluginDescriptor[] plugins = PluginManagerCore.getPlugins();
    for (IdeaPluginDescriptor plugin : plugins) {
      if (PluginManagerCore.shouldSkipPlugin(plugin)) continue;
      final List<Element> elementList = plugin.getActionsDescriptionElements();
      if (elementList != null) {
        for (Element e : elementList) {
          processActionsChildElement(plugin.getPluginClassLoader(), plugin.getPluginId(), e);
        }
      }
    }
  }

  @Override
  @Nullable
  public AnAction getAction(@NotNull String id) {
    return getActionImpl(id, false);
  }

  @Nullable
  private AnAction getActionImpl(String id, boolean canReturnStub) {
    AnAction action;
    synchronized (myLock) {
      action = myId2Action.get(id);
      if (canReturnStub || !(action instanceof ActionStub)) {
        return action;
      }
    }
    AnAction converted = convertStub((ActionStub)action);
    if (converted == null) return null;

    synchronized (myLock) {
      action = myId2Action.get(id);
      if (action instanceof ActionStub) {
        action = replaceStub((ActionStub)action, converted);
      }
      return action;
    }
  }

  @NotNull
  private AnAction replaceStub(@NotNull ActionStub stub, AnAction anAction) {
    LOG.assertTrue(myAction2Id.containsKey(stub));
    myAction2Id.remove(stub);

    LOG.assertTrue(myId2Action.containsKey(stub.getId()));

    AnAction action = myId2Action.remove(stub.getId());
    LOG.assertTrue(action != null);
    LOG.assertTrue(action.equals(stub));

    myAction2Id.put(anAction, stub.getId());

    return addToMap(stub.getId(), anAction, stub.getPluginId(), stub.getProjectType());
  }

  @Override
  public String getId(@NotNull AnAction action) {
    LOG.assertTrue(!(action instanceof ActionStub));
    synchronized (myLock) {
      return myAction2Id.get(action);
    }
  }

  @Override
  public String[] getActionIds(@NotNull String idPrefix) {
    synchronized (myLock) {
      ArrayList<String> idList = new ArrayList<>();
      for (String id : myId2Action.keySet()) {
        if (id.startsWith(idPrefix)) {
          idList.add(id);
        }
      }
      return ArrayUtil.toStringArray(idList);
    }
  }

  @Override
  public boolean isGroup(@NotNull String actionId) {
    return getActionImpl(actionId, true) instanceof ActionGroup;
  }

  @Override
  public JComponent createButtonToolbar(final String actionPlace, @NotNull final ActionGroup messageActionGroup) {
    return new ButtonToolbarImpl(actionPlace, messageActionGroup, myDataManager, this);
  }

  @Override
  public AnAction getActionOrStub(String id) {
    return getActionImpl(id, true);
  }

  /**
   * @return instance of ActionGroup or ActionStub. The method never returns real subclasses of {@code AnAction}.
   */
  @Nullable
  private AnAction processActionElement(Element element, final ClassLoader loader, PluginId pluginId) {
    final IdeaPluginDescriptor plugin = PluginManager.getPlugin(pluginId);
    ResourceBundle bundle = getActionsResourceBundle(loader, plugin);

    if (!ACTION_ELEMENT_NAME.equals(element.getName())) {
      reportActionError(pluginId, "unexpected name of element \"" + element.getName() + "\"");
      return null;
    }
    String className = element.getAttributeValue(CLASS_ATTR_NAME);
    if (className == null || className.isEmpty()) {
      reportActionError(pluginId, "action element should have specified \"class\" attribute");
      return null;
    }
    // read ID and register loaded action
    String id = element.getAttributeValue(ID_ATTR_NAME);
    if (id == null || id.isEmpty()) {
      id = StringUtil.getShortName(className);
    }
    if (Boolean.valueOf(element.getAttributeValue(INTERNAL_ATTR_NAME)).booleanValue() && !ApplicationManagerEx.getApplicationEx().isInternal()) {
      myNotRegisteredInternalActionIds.add(id);
      return null;
    }

    String text = loadTextForElement(element, bundle, id, ACTION_ELEMENT_NAME);

    String iconPath = element.getAttributeValue(ICON_ATTR_NAME);

    if (text == null) {
      @NonNls String message = "'text' attribute is mandatory (action ID=" + id + ";" +
                               (plugin == null ? "" : " plugin path: "+plugin.getPath()) + ")";
      reportActionError(pluginId, message);
      return null;
    }

    String projectType = element.getAttributeValue(PROJECT_TYPE);
    ActionStub stub = new ActionStub(className, id, text, loader, pluginId, iconPath, projectType);
    Presentation presentation = stub.getTemplatePresentation();
    presentation.setText(text);

    // description

    presentation.setDescription(loadDescriptionForElement(element, bundle, id, ACTION_ELEMENT_NAME));

    // process all links and key bindings if any
    for (Element e : element.getChildren()) {
      if (ADD_TO_GROUP_ELEMENT_NAME.equals(e.getName())) {
        processAddToGroupNode(stub, e, pluginId, isSecondary(e));
      }
      else if (SHORTCUT_ELEMENT_NAME.equals(e.getName())) {
        processKeyboardShortcutNode(e, id, pluginId);
      }
      else if (MOUSE_SHORTCUT_ELEMENT_NAME.equals(e.getName())) {
        processMouseShortcutNode(e, id, pluginId);
      }
      else if (ABBREVIATION_ELEMENT_NAME.equals(e.getName())) {
        processAbbreviationNode(e, id);
      }
      else {
        reportActionError(pluginId, "unexpected name of element \"" + e.getName() + "\"");
        return null;
      }
    }
    if (element.getAttributeValue(USE_SHORTCUT_OF_ATTR_NAME) != null) {
      myKeymapManager.bindShortcuts(element.getAttributeValue(USE_SHORTCUT_OF_ATTR_NAME), id);
    }

    registerOrReplaceActionInner(element, id, stub, pluginId);
    return stub;
  }

  private void registerOrReplaceActionInner(@NotNull Element element, @NotNull String id, @NotNull AnAction action, @Nullable PluginId pluginId) {
    synchronized (myLock) {
      if (Boolean.valueOf(element.getAttributeValue(OVERRIDES_ATTR_NAME))) {
        if (getActionOrStub(id) == null) {
          throw new RuntimeException(element.getName() + " '" + id + "' doesn't override anything");
        }
        AnAction prev = replaceAction(id, action, pluginId);
        if (action instanceof DefaultActionGroup && prev instanceof DefaultActionGroup) {
          if (Boolean.valueOf(element.getAttributeValue(KEEP_CONTENT_ATTR_NAME))) {
            ((DefaultActionGroup)action).copyFromGroup((DefaultActionGroup)prev);
          }
        }
      }
      else {
        registerAction(id, action, pluginId, element.getAttributeValue(PROJECT_TYPE));
      }
    }
  }

  private AnAction processGroupElement(Element element, final ClassLoader loader, PluginId pluginId) {
    final IdeaPluginDescriptor plugin = PluginManager.getPlugin(pluginId);
    ResourceBundle bundle = getActionsResourceBundle(loader, plugin);

    if (!GROUP_ELEMENT_NAME.equals(element.getName())) {
      reportActionError(pluginId, "unexpected name of element \"" + element.getName() + "\"");
      return null;
    }
    String className = element.getAttributeValue(CLASS_ATTR_NAME);
    if (className == null) { // use default group if class isn't specified
      if ("true".equals(element.getAttributeValue(COMPACT_ATTR_NAME))) {
        className = DefaultCompactActionGroup.class.getName();
      } else {
        className = DefaultActionGroup.class.getName();
      }
    }
    try {
      ActionGroup group;
      if (DefaultActionGroup.class.getName().equals(className)) {
        group = new DefaultActionGroup();
      } else if (DefaultCompactActionGroup.class.getName().equals(className)) {
        group = new DefaultCompactActionGroup();
      } else {
        Class aClass = Class.forName(className, true, loader);
        Object obj = new CachingConstructorInjectionComponentAdapter(className, aClass).getComponentInstance(ApplicationManager.getApplication().getPicoContainer());

        if (!(obj instanceof ActionGroup)) {
          reportActionError(pluginId, "class with name \"" + className + "\" should be instance of " + ActionGroup.class.getName());
          return null;
        }
        if (element.getChildren().size() != element.getChildren(ADD_TO_GROUP_ELEMENT_NAME).size() ) {  //
          if (!(obj instanceof DefaultActionGroup)) {
            reportActionError(pluginId, "class with name \"" + className + "\" should be instance of " + DefaultActionGroup.class.getName() +
                                        " because there are children specified");
            return null;
          }
        }
        group = (ActionGroup)obj;
      }
      // read ID and register loaded group
      String id = element.getAttributeValue(ID_ATTR_NAME);
      if (id != null && id.isEmpty()) {
        reportActionError(pluginId, "ID of the group cannot be an empty string");
        return null;
      }
      if (Boolean.valueOf(element.getAttributeValue(INTERNAL_ATTR_NAME)).booleanValue() && !ApplicationManagerEx.getApplicationEx().isInternal()) {
        myNotRegisteredInternalActionIds.add(id);
        return null;
      }

      if (id != null) {
        registerOrReplaceActionInner(element, id, group, pluginId);
      }
      Presentation presentation = group.getTemplatePresentation();

      // text
      String text = loadTextForElement(element, bundle, id, GROUP_ELEMENT_NAME);
      // don't override value which was set in API with empty value from xml descriptor
      if (!StringUtil.isEmpty(text) || presentation.getText() == null) {
        presentation.setText(text);
      }

      // description
      String description = loadDescriptionForElement(element, bundle, id, GROUP_ELEMENT_NAME);
      // don't override value which was set in API with empty value from xml descriptor
      if (!StringUtil.isEmpty(description) || presentation.getDescription() == null) {
        presentation.setDescription(description);
      }

      // icon
      setIcon(element.getAttributeValue(ICON_ATTR_NAME), className, loader, presentation, pluginId);
      // popup
      String popup = element.getAttributeValue(POPUP_ATTR_NAME);
      if (popup != null) {
        group.setPopup(Boolean.valueOf(popup).booleanValue());
      }
      // process all group's children. There are other groups, actions, references and links
      for (Element child : element.getChildren()) {
        String name = child.getName();
        if (ACTION_ELEMENT_NAME.equals(name)) {
          AnAction action = processActionElement(child, loader, pluginId);
          if (action != null) {
            assertActionIsGroupOrStub(action);
            addToGroupInner(group, action, Constraints.LAST, isSecondary(child));
          }
        }
        else if (SEPARATOR_ELEMENT_NAME.equals(name)) {
          processSeparatorNode((DefaultActionGroup)group, child, pluginId);
        }
        else if (GROUP_ELEMENT_NAME.equals(name)) {
          AnAction action = processGroupElement(child, loader, pluginId);
          if (action != null) {
            addToGroupInner(group, action, Constraints.LAST, false);
          }
        }
        else if (ADD_TO_GROUP_ELEMENT_NAME.equals(name)) {
          processAddToGroupNode(group, child, pluginId, isSecondary(child));
        }
        else if (REFERENCE_ELEMENT_NAME.equals(name)) {
          AnAction action = processReferenceElement(child, pluginId);
          if (action != null) {
            addToGroupInner(group, action, Constraints.LAST, isSecondary(child));
          }
        }
        else {
          reportActionError(pluginId, "unexpected name of element \"" + name + "\n");
          return null;
        }
      }
      return group;
    }
    catch (ClassNotFoundException e) {
      reportActionError(pluginId, "class with name \"" + className + "\" not found");
      return null;
    }
    catch (NoClassDefFoundError e) {
      reportActionError(pluginId, "class with name \"" + e.getMessage() + "\" not found");
      return null;
    }
    catch(UnsupportedClassVersionError e) {
      reportActionError(pluginId, "unsupported class version for " + className);
      return null;
    }
    catch (Exception e) {
      final String message = "cannot create class \"" + className + "\"";
      if (pluginId == null) {
        LOG.error(message, e);
      }
      else {
        LOG.error(new PluginException(message, e, pluginId));
      }
      return null;
    }
  }

  private void processReferenceNode(final Element element, final PluginId pluginId) {
    final AnAction action = processReferenceElement(element, pluginId);
    if (action == null) return;

    for (Element child : element.getChildren()) {
      if (ADD_TO_GROUP_ELEMENT_NAME.equals(child.getName())) {
        processAddToGroupNode(action, child, pluginId, isSecondary(child));
      }
    }
  }

  /**\
   * @param element description of link
   */
  private void processAddToGroupNode(AnAction action, Element element, final PluginId pluginId, boolean secondary) {
    // Real subclasses of AnAction should not be here
    if (!(action instanceof Separator)) {
      assertActionIsGroupOrStub(action);
    }

    String actionName = String.format(
      "%s (%s)", action instanceof ActionStub? ((ActionStub)action).getClassName() : action.getClass().getName(),
      action instanceof ActionStub ? ((ActionStub)action).getId() : myAction2Id.get(action));

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
    String actionId = action instanceof ActionStub ? ((ActionStub)action).getId() : myAction2Id.get(action);
    ((DefaultActionGroup)group).addAction(action, constraints, this).setAsSecondary(secondary);
    myId2GroupId.putValue(actionId, myAction2Id.get(group));
  }

  @Nullable
  public AnAction getParentGroup(final String groupId,
                                 @Nullable final String actionName,
                                 @Nullable final PluginId pluginId) {
    if (groupId == null || groupId.isEmpty()) {
      reportActionError(pluginId, actionName + ": attribute \"group-id\" should be defined");
      return null;
    }
    AnAction parentGroup = getActionImpl(groupId, true);
    if (parentGroup == null) {
      reportActionError(pluginId, actionName + ": group with id \"" + groupId + "\" isn't registered; action will be added to the \"Other\" group");
      parentGroup = getActionImpl(IdeActions.GROUP_OTHER_MENU, true);
    }
    if (!(parentGroup instanceof DefaultActionGroup)) {
      reportActionError(pluginId, actionName + ": group with id \"" + groupId + "\" should be instance of " + DefaultActionGroup.class.getName() +
                                  " but was " + parentGroup.getClass());
      return null;
    }
    return parentGroup;
  }

  /**
   * @param parentGroup group which is the parent of the separator. It can be {@code null} in that
   *                    case separator will be added to group described in the <add-to-group ....> subelement.
   * @param element     XML element which represent separator.
   */
  private void processSeparatorNode(@Nullable DefaultActionGroup parentGroup, Element element, PluginId pluginId) {
    if (!SEPARATOR_ELEMENT_NAME.equals(element.getName())) {
      reportActionError(pluginId, "unexpected name of element \"" + element.getName() + "\"");
      return;
    }
    String text = element.getAttributeValue(TEXT_ATTR_NAME);
    Separator separator = text != null ? new Separator(text) : Separator.getInstance();
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

  private void processKeyboardShortcutNode(Element element, String actionId, PluginId pluginId) {
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
    Keymap keymap = myKeymapManager.getKeymap(keymapName);
    if (keymap == null) {
      reportActionWarning(pluginId, "keymap \"" + keymapName + "\" not found");
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

  @Nullable
  private AnAction processReferenceElement(Element element, PluginId pluginId) {
    if (!REFERENCE_ELEMENT_NAME.equals(element.getName())) {
      reportActionError(pluginId, "unexpected name of element \"" + element.getName() + "\"");
      return null;
    }
    String ref = element.getAttributeValue(REF_ATTR_NAME);

    if (ref==null) {
      // support old style references by id
      ref = element.getAttributeValue(ID_ATTR_NAME);
    }

    if (ref == null || ref.isEmpty()) {
      reportActionError(pluginId, "ID of reference element should be defined");
      return null;
    }

    AnAction action = getActionImpl(ref, true);

    if (action == null) {
      if (!myNotRegisteredInternalActionIds.contains(ref)) {
        reportActionError(pluginId, "action specified by reference isn't registered (ID=" + ref + ")");
      }
      return null;
    }
    assertActionIsGroupOrStub(action);
    return action;
  }

  private void processActionsChildElement(final ClassLoader loader, final PluginId pluginId, final Element child) {
    String name = child.getName();
    if (ACTION_ELEMENT_NAME.equals(name)) {
      AnAction action = processActionElement(child, loader, pluginId);
      if (action != null) {
        assertActionIsGroupOrStub(action);
      }
    }
    else if (GROUP_ELEMENT_NAME.equals(name)) {
      processGroupElement(child, loader, pluginId);
    }
    else if (SEPARATOR_ELEMENT_NAME.equals(name)) {
      processSeparatorNode(null, child, pluginId);
    }
    else if (REFERENCE_ELEMENT_NAME.equals(name)) {
      processReferenceNode(child, pluginId);
    }
    else {
      reportActionError(pluginId, "unexpected name of element \"" + name + "\n");
    }
  }

  @Override
  public void registerAction(@NotNull String actionId, @NotNull AnAction action, @Nullable PluginId pluginId) {
    registerAction(actionId, action, pluginId, null);
  }

  public void registerAction(@NotNull String actionId, @NotNull AnAction action, @Nullable PluginId pluginId, @Nullable String projectType) {
    synchronized (myLock) {
      if (addToMap(actionId, action, pluginId, projectType) == null) return;
      if (myAction2Id.containsKey(action)) {
        reportActionError(pluginId, "action was already registered for another ID. ID is " + myAction2Id.get(action) +
                                    getPluginInfo(pluginId));
        return;
      }
      myId2Index.put(actionId, myRegisteredActionsCount++);
      myAction2Id.put(action, actionId);
      if (pluginId != null && !(action instanceof ActionGroup)){
        myPlugin2Id.computeIfAbsent(pluginId, k -> new THashSet<>()).add(actionId);
      }
      action.registerCustomShortcutSet(new ProxyShortcutSet(actionId, myKeymapManager), null);
    }
  }

  private AnAction addToMap(String actionId, AnAction action, PluginId pluginId, String projectType) {
    if (projectType != null || myId2Action.containsKey(actionId)) {
      return registerChameleon(actionId, action, pluginId, projectType);
    }
    else {
      myId2Action.put(actionId, action);
      return action;
    }
  }

  private AnAction registerChameleon(String actionId, AnAction action, PluginId pluginId, String projectType) {
    ProjectType type = projectType == null ? null : new ProjectType(projectType);
    // make sure id+projectType is unique
    AnAction o = myId2Action.get(actionId);
    ChameleonAction chameleonAction;
    if (o == null) {
      chameleonAction = new ChameleonAction(action, type);
      myId2Action.put(actionId, chameleonAction);
      return chameleonAction;
    }
    if (o instanceof ChameleonAction) {
      chameleonAction = (ChameleonAction)o;
    }
    else {
      chameleonAction = new ChameleonAction(o, type);
      myId2Action.put(actionId, chameleonAction);
    }
    AnAction old = chameleonAction.addAction(action, type);
    if (old != null) {
      reportActionError(pluginId,
                        "action with the ID \"" + actionId + "\" was already registered. Action being registered is " + action +
                        "; Registered action is " +
                        myId2Action.get(actionId) + getPluginInfo(pluginId));
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
    synchronized (myLock) {
      if (!myId2Action.containsKey(actionId)) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("action with ID " + actionId + " wasn't registered");
          return;
        }
      }
      AnAction oldValue = myId2Action.remove(actionId);
      myAction2Id.remove(oldValue);
      myId2Index.remove(actionId);
      for (PluginId pluginName : myPlugin2Id.keySet()) {
        final THashSet<String> pluginActions = myPlugin2Id.get(pluginName);
        if (pluginActions != null) {
          pluginActions.remove(actionId);
        }
      }
    }
  }

  @NotNull
  @Override
  public Comparator<String> getRegistrationOrderComparator() {
    return Comparator.comparingInt(myId2Index::get);
  }

  @NotNull
  @Override
  public String[] getPluginActions(@NotNull PluginId pluginName) {
    return ArrayUtilRt.toStringArray(myPlugin2Id.get(pluginName));
  }

  public void addActionPopup(final ActionPopupMenuImpl menu) {
    myPopups.add(menu);
  }

  public void removeActionPopup(final ActionPopupMenuImpl menu) {
    final boolean removed = myPopups.remove(menu);
    if (removed && myPopups.isEmpty()) {
      flushActionPerformed();
    }
  }

  @Override
  public void queueActionPerformedEvent(final AnAction action, DataContext context, AnActionEvent event) {
    if (!myPopups.isEmpty()) {
      myQueuedNotifications.put(action, context);
    } else {
      fireAfterActionPerformed(action, context, event);
    }
  }

  //@Override
  //public AnAction replaceAction(String actionId, @NotNull AnAction newAction) {
  //  synchronized (myLock) {
  //    return replaceAction(actionId, newAction, null);
  //  }
  //}

  @Override
  public boolean isActionPopupStackEmpty() {
    return myPopups.isEmpty();
  }

  @Override
  public boolean isTransparentOnlyActionsUpdateNow() {
    return myTransparentOnlyUpdate;
  }

  private AnAction replaceAction(@NotNull String actionId, @NotNull AnAction newAction, @Nullable PluginId pluginId) {
    AnAction oldAction = getActionOrStub(actionId);
    if (oldAction != null) {
      boolean isGroup = oldAction instanceof ActionGroup;
      if (isGroup != newAction instanceof ActionGroup) {
        throw new IllegalStateException("cannot replace a group with an action and vice versa: " + actionId);
      }
      for (String groupId : myId2GroupId.get(actionId)) {
        DefaultActionGroup group = ObjectUtils.assertNotNull((DefaultActionGroup)getActionOrStub(groupId));
        group.replaceAction(oldAction, newAction);
      }
      unregisterAction(actionId);
      if (isGroup) {
        myId2GroupId.values().remove(actionId);
      }
    }
    registerAction(actionId, newAction, pluginId);
    return oldAction;
  }

  private void flushActionPerformed() {
    final Set<AnAction> actions = myQueuedNotifications.keySet();
    for (final AnAction eachAction : actions) {
      final DataContext eachContext = myQueuedNotifications.get(eachAction);
      fireAfterActionPerformed(eachAction, eachContext, myQueuedNotificationsEvents.get(eachAction));
    }
    myQueuedNotifications.clear();
    myQueuedNotificationsEvents.clear();
  }

  @Override
  public void addAnActionListener(AnActionListener listener) {
    myActionListeners.add(listener);
  }

  @Override
  public void addAnActionListener(final AnActionListener listener, final Disposable parentDisposable) {
    addAnActionListener(listener);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        removeAnActionListener(listener);
      }
    });
  }

  @Override
  public void removeAnActionListener(AnActionListener listener) {
    myActionListeners.remove(listener);
  }

  @Override
  public void fireBeforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
    if (action != null) {
      myPrevPerformedActionId = myLastPreformedActionId;
      myLastPreformedActionId = getId(action);
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      IdeaLogger.ourLastActionId = myLastPreformedActionId;
    }
    for (AnActionListener listener : myActionListeners) {
      listener.beforeActionPerformed(action, dataContext, event);
    }
  }

  @Override
  public void fireAfterActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
    if (action != null) {
      myPrevPerformedActionId = myLastPreformedActionId;
      myLastPreformedActionId = getId(action);
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      IdeaLogger.ourLastActionId = myLastPreformedActionId;
    }
    for (AnActionListener listener : myActionListeners) {
      try {
        listener.afterActionPerformed(action, dataContext, event);
      }
      catch(AbstractMethodError ignored) { }
    }
  }

  @Override
  public KeyboardShortcut getKeyboardShortcut(@NotNull String actionId) {
    AnAction action = ActionManager.getInstance().getAction(actionId);
    final ShortcutSet shortcutSet = action.getShortcutSet();
    final Shortcut[] shortcuts = shortcutSet.getShortcuts();
    for (final Shortcut shortcut : shortcuts) {
      // Shortcut can be MouseShortcut here.
      // For example IdeaVIM often assigns them
      if (shortcut instanceof KeyboardShortcut){
        final KeyboardShortcut kb = (KeyboardShortcut)shortcut;
        if (kb.getSecondKeyStroke() == null) {
          return (KeyboardShortcut)shortcut;
        }
      }
    }

    return null;
  }

  @Override
  public void fireBeforeEditorTyping(char c, DataContext dataContext) {
    myLastTimeEditorWasTypedIn = System.currentTimeMillis();
    for (AnActionListener listener : myActionListeners) {
      listener.beforeEditorTyping(c, dataContext);
    }
  }

  @Override
  public String getLastPreformedActionId() {
    return myLastPreformedActionId;
  }

  @Override
  public String getPrevPreformedActionId() {
    return myPrevPerformedActionId;
  }

  public Set<String> getActionIds(){
    synchronized (myLock) {
      return new HashSet<>(myId2Action.keySet());
    }
  }

  public void preloadActions(ProgressIndicator indicator) {
    final Application application = ApplicationManager.getApplication();

    for (String id : getActionIds()) {
      indicator.checkCanceled();
      if (application.isDisposed()) return;

      final AnAction action = getAction(id);
      if (action instanceof PreloadableAction) {
        ((PreloadableAction)action).preload();
      }
      // don't preload ActionGroup.getChildren() because that would unstub child actions
      // and make it impossible to replace the corresponding actions later
      // (via unregisterAction+registerAction, as some app components do)
    }
  }

  @Override
  public ActionCallback tryToExecute(@NotNull final AnAction action, @NotNull final InputEvent inputEvent, @Nullable final Component contextComponent, @Nullable final String place,
                                     boolean now) {

    final Application app = ApplicationManager.getApplication();
    assert app.isDispatchThread();

    final ActionCallback result = new ActionCallback();
    final Runnable doRunnable = () -> tryToExecuteNow(action, inputEvent, contextComponent, place, result);

    if (now) {
      doRunnable.run();
    } else {
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(doRunnable);
    }

    return result;
  }

  private void tryToExecuteNow(final AnAction action, final InputEvent inputEvent, final Component contextComponent, final String place, final ActionCallback result) {
    final Presentation presentation = action.getTemplatePresentation().clone();

    IdeFocusManager.findInstanceByContext(getContextBy(contextComponent)).doWhenFocusSettlesDown(
      () -> ((TransactionGuardImpl)TransactionGuard.getInstance()).performUserActivity(() -> {
        final DataContext context = getContextBy(contextComponent);

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
        if (component != null && !component.isShowing()) {
          result.setRejected();
          return;
        }

        fireBeforeActionPerformed(action, context, event);

        UIUtil.addAwtListener(new AWTEventListener() {
          @Override
          public void eventDispatched(AWTEvent event) {
            if (event.getID() == WindowEvent.WINDOW_OPENED ||event.getID() == WindowEvent.WINDOW_ACTIVATED) {
              if (!result.isProcessed()) {
                final WindowEvent we = (WindowEvent)event;
                IdeFocusManager.findInstanceByComponent(we.getWindow()).doWhenFocusSettlesDown(result.createSetDoneRunnable());
              }
            }
          }
        }, AWTEvent.WINDOW_EVENT_MASK, result);

        ActionUtil.performActionDumbAware(action, event);
        result.setDone();
        queueActionPerformedEvent(action, context, event);
      }
    ));
  }

  private class MyTimer extends Timer implements ActionListener {
    private final List<TimerListener> myTimerListeners = ContainerUtil.createLockFreeCopyOnWriteList();
    private final List<TimerListener> myTransparentTimerListeners = ContainerUtil.createLockFreeCopyOnWriteList();
    private int myLastTimePerformed;

    private MyTimer() {
      super(TIMER_DELAY, null);
      addActionListener(this);
      setRepeats(true);
      final MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
      connection.subscribe(ApplicationActivationListener.TOPIC, new ApplicationActivationListener() {
        @Override
        public void applicationActivated(IdeFrame ideFrame) {
          setDelay(TIMER_DELAY);
          restart();
        }

        @Override
        public void applicationDeactivated(IdeFrame ideFrame) {
          setDelay(DEACTIVATED_TIMER_DELAY);
        }
      });
    }

    @Override
    public String toString() {
      return "Action manager timer";
    }

    public void addTimerListener(TimerListener listener, boolean transparent){
      (transparent ? myTransparentTimerListeners : myTimerListeners).add(listener);
    }

    public void removeTimerListener(TimerListener listener, boolean transparent){
      (transparent ? myTransparentTimerListeners : myTimerListeners).remove(listener);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      if (myLastTimeEditorWasTypedIn + UPDATE_DELAY_AFTER_TYPING > System.currentTimeMillis()) {
        return;
      }

      if (IdeFocusManager.getInstance(null).isFocusBeingTransferred()) return;

      final int lastEventCount = myLastTimePerformed;
      myLastTimePerformed = ActivityTracker.getInstance().getCount();

      boolean transparentOnly = myLastTimePerformed == lastEventCount;

      try {
        Set<TimerListener> notified = new HashSet<>();
        myTransparentOnlyUpdate = transparentOnly;
        notifyListeners(myTransparentTimerListeners, notified);

        if (transparentOnly) {
          return;
        }

        notifyListeners(myTimerListeners, notified);
      }
      finally {
        myTransparentOnlyUpdate = false;
      }
    }

    private void notifyListeners(final List<TimerListener> timerListeners, final Set<TimerListener> notified) {
      for (TimerListener listener : timerListeners) {
        if (notified.add(listener)) {
          runListenerAction(listener);
        }
      }
    }

    private void runListenerAction(final TimerListener listener) {
      ModalityState modalityState = listener.getModalityState();
      if (modalityState == null) return;
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
