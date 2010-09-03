/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.CommonBundle;
import com.intellij.diagnostic.PluginException;
import com.intellij.ide.ActivityTracker;
import com.intellij.ide.DataManager;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.idea.IdeaLogger;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectIntHashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.defaults.ConstructorInjectionComponentAdapter;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.List;

public final class ActionManagerImpl extends ActionManagerEx implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.actionSystem.impl.ActionManagerImpl");
  private static final int TIMER_DELAY = 500;
  private static final int UPDATE_DELAY_AFTER_TYPING = 500;

  private final Object myLock = new Object();
  private final THashMap<String,Object> myId2Action;
  private final THashMap<PluginId, THashSet<String>> myPlugin2Id;
  private final TObjectIntHashMap<String> myId2Index;
  private final THashMap<Object,String> myAction2Id;
  private final ArrayList<String> myNotRegisteredInternalActionIds;
  private MyTimer myTimer;

  private int myRegisteredActionsCount;
  private final ArrayList<AnActionListener> myActionListeners;
  private AnActionListener[] myCachedActionListeners;
  private String myLastPreformedActionId;
  private final KeymapManager myKeymapManager;
  private final DataManager myDataManager;
  private String myPrevPerformedActionId;
  private long myLastTimeEditorWasTypedIn = 0;
  @NonNls public static final String ACTION_ELEMENT_NAME = "action";
  @NonNls public static final String GROUP_ELEMENT_NAME = "group";
  @NonNls public static final String ACTIONS_ELEMENT_NAME = "actions";
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
  @NonNls public static final String SEPARATOR_ELEMENT_NAME = "separator";
  @NonNls public static final String REFERENCE_ELEMENT_NAME = "reference";
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
  @NonNls public static final String KEYMAP_ATTR_NAME = "keymap";
  @NonNls public static final String KEYSTROKE_ATTR_NAME = "keystroke";
  @NonNls public static final String REF_ATTR_NAME = "ref";
  @NonNls public static final String ACTIONS_BUNDLE = "messages.ActionsBundle";
  @NonNls public static final String USE_SHORTCUT_OF_ATTR_NAME = "use-shortcut-of";

  private final List<ActionPopupMenuImpl> myPopups = new ArrayList<ActionPopupMenuImpl>();

  private final Map<AnAction, DataContext> myQueuedNotifications = new LinkedHashMap<AnAction, DataContext>();
  private final Map<AnAction, AnActionEvent> myQueuedNotificationsEvents = new LinkedHashMap<AnAction, AnActionEvent>();

  private Runnable myPreloadActionsRunnable;
  private boolean myTransparrentOnlyUpdate;

  ActionManagerImpl(KeymapManager keymapManager, DataManager dataManager) {
    myId2Action = new THashMap<String, Object>();
    myId2Index = new TObjectIntHashMap<String>();
    myAction2Id = new THashMap<Object, String>();
    myPlugin2Id = new THashMap<PluginId, THashSet<String>>();
    myNotRegisteredInternalActionIds = new ArrayList<String>();
    myActionListeners = new ArrayList<AnActionListener>();
    myCachedActionListeners = null;
    myKeymapManager = keymapManager;
    myDataManager = dataManager;

    registerPluginActions();
  }

  public void initComponent() {}

  public void disposeComponent() {
    if (myTimer != null) {
      myTimer.stop();
      myTimer = null;
    }
  }

  public void addTimerListener(int delay, final TimerListener listener) {
    _addTimerListener(delay, listener, false);
  }

  public void removeTimerListener(TimerListener listener) {
    _removeTimerListener(listener, false);
  }

  @Override
  public void addTransparrentTimerListener(int delay, TimerListener listener) {
    _addTimerListener(delay, listener, true);
  }

  @Override
  public void removeTransparrentTimerListener(TimerListener listener) {
    _removeTimerListener(listener, true);
  }


  private void _addTimerListener(int delay, final TimerListener listener, boolean transparrent) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    if (myTimer == null) {
      myTimer = new MyTimer();
      myTimer.start();
    }

    myTimer.addTimerListener(listener, transparrent);
  }

  private void _removeTimerListener(TimerListener listener, boolean transparrent) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    LOG.assertTrue(myTimer != null);

    myTimer.removeTimerListener(listener, transparrent);
  }

  public ActionPopupMenu createActionPopupMenu(String place, @NotNull ActionGroup group, @Nullable PresentationFactory presentationFactory) {
    return new ActionPopupMenuImpl(place, group, this, presentationFactory);
  }

  public ActionPopupMenu createActionPopupMenu(String place, @NotNull ActionGroup group) {
    return new ActionPopupMenuImpl(place, group, this, null);
  }

  public ActionToolbar createActionToolbar(final String place, final ActionGroup group, final boolean horizontal) {
    return new ActionToolbarImpl(place, group, horizontal, myDataManager, this, (KeymapManagerEx)myKeymapManager);
  }


  private void registerPluginActions() {
    final Application app = ApplicationManager.getApplication();
    final IdeaPluginDescriptor[] plugins = app.getPlugins();
    for (IdeaPluginDescriptor plugin : plugins) {
      if (PluginManager.shouldSkipPlugin(plugin)) continue;
      final List<Element> elementList = plugin.getActionsDescriptionElements();
      if (elementList != null) {
        for (Element e : elementList) {
          processActionsChildElement(plugin.getPluginClassLoader(), plugin.getPluginId(), e);
        }
      }
    }
  }

  public AnAction getAction(@NotNull String id) {
    return getActionImpl(id, false);
  }

  private AnAction getActionImpl(String id, boolean canReturnStub) {
    synchronized (myLock) {
      AnAction action = (AnAction)myId2Action.get(id);
      if (!canReturnStub && action instanceof ActionStub) {
        action = convert((ActionStub)action);
      }
      return action;
    }
  }

  /**
   * Converts action's stub to normal action.
   */
  private AnAction convert(ActionStub stub) {
    LOG.assertTrue(myAction2Id.contains(stub));
    myAction2Id.remove(stub);

    LOG.assertTrue(myId2Action.contains(stub.getId()));

    AnAction action = (AnAction)myId2Action.remove(stub.getId());
    LOG.assertTrue(action != null);
    LOG.assertTrue(action.equals(stub));

    Object obj;
    String className = stub.getClassName();
    try {
      Constructor<?> constructor = Class.forName(className, true, stub.getLoader()).getDeclaredConstructor();
      constructor.setAccessible(true);
      obj = constructor.newInstance();
    }
    catch (ClassNotFoundException e) {
      PluginId pluginId = stub.getPluginId();
      if (pluginId != null) {
        throw new PluginException("class with name \"" + className + "\" not found", e, pluginId);
      }
      else {
        throw new IllegalStateException("class with name \"" + className + "\" not found");
      }
    }
    catch(UnsupportedClassVersionError e) {
      PluginId pluginId = stub.getPluginId();
      if (pluginId != null) {
        throw new PluginException(e, pluginId);
      }
      else {
        throw new IllegalStateException(e);
      }
    }
    catch (Exception e) {
      PluginId pluginId = stub.getPluginId();
      if (pluginId != null) {
        throw new PluginException("cannot create class \"" + className + "\"", e, pluginId);
      }
      else {
        throw new IllegalStateException("cannot create class \"" + className + "\"", e);
      }
    }

    if (!(obj instanceof AnAction)) {
      throw new IllegalStateException("class with name \"" + className + "\" should be instance of " + AnAction.class.getName());
    }

    AnAction anAction = (AnAction)obj;
    stub.initAction(anAction);
    if (StringUtil.isNotEmpty(stub.getText())) {
      anAction.getTemplatePresentation().setText(stub.getText());
    }
    String iconPath = stub.getIconPath();
    if (iconPath != null) {
      setIconFromClass(anAction.getClass(), anAction.getClass().getClassLoader(), iconPath, stub.getClassName(), anAction.getTemplatePresentation(), stub.getPluginId());
    }

    myId2Action.put(stub.getId(), obj);
    myAction2Id.put(obj, stub.getId());

    return anAction;
  }

  public String getId(@NotNull AnAction action) {
    LOG.assertTrue(!(action instanceof ActionStub));
    synchronized (myLock) {
      return myAction2Id.get(action);
    }
  }

  public String[] getActionIds(@NotNull String idPrefix) {
    synchronized (myLock) {
      ArrayList<String> idList = new ArrayList<String>();
      for (String id : myId2Action.keySet()) {
        if (id.startsWith(idPrefix)) {
          idList.add(id);
        }
      }
      return ArrayUtil.toStringArray(idList);
    }
  }

  public boolean isGroup(@NotNull String actionId) {
    return getActionImpl(actionId, true) instanceof ActionGroup;
  }

  public JComponent createButtonToolbar(final String actionPlace, final ActionGroup messageActionGroup) {
    return new ButtonToolbarImpl(actionPlace, messageActionGroup, myDataManager, this);
  }

  public AnAction getActionOrStub(String id) {
    return getActionImpl(id, true);
  }

  /**
   * @return instance of ActionGroup or ActionStub. The method never returns real subclasses
   *         of <code>AnAction</code>.
   */
  @Nullable
  private AnAction processActionElement(Element element, final ClassLoader loader, PluginId pluginId) {
    final Application app = ApplicationManager.getApplication();
    final IdeaPluginDescriptor plugin = app.getPlugin(pluginId);
    ResourceBundle bundle = getActionsResourceBundle(loader, plugin);

    if (!ACTION_ELEMENT_NAME.equals(element.getName())) {
      reportActionError(pluginId, "unexpected name of element \"" + element.getName() + "\"");
      return null;
    }
    String className = element.getAttributeValue(CLASS_ATTR_NAME);
    if (className == null || className.length() == 0) {
      reportActionError(pluginId, "action element should have specified \"class\" attribute");
      return null;
    }
    // read ID and register loaded action
    String id = element.getAttributeValue(ID_ATTR_NAME);
    if (id == null || id.length() == 0) {
      reportActionError(pluginId, "ID of the action cannot be an empty string");
      return null;
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

    ActionStub stub = new ActionStub(className, id, text, loader, pluginId, iconPath);
    Presentation presentation = stub.getTemplatePresentation();
    presentation.setText(text);

    // description

    presentation.setDescription(loadDescriptionForElement(element, bundle, id, ACTION_ELEMENT_NAME));

    // process all links and key bindings if any
    for (final Object o : element.getChildren()) {
      Element e = (Element)o;
      if (ADD_TO_GROUP_ELEMENT_NAME.equals(e.getName())) {
        processAddToGroupNode(stub, e, pluginId, isSecondary(e));
      }
      else if (SHORTCUT_ELEMENT_NAME.equals(e.getName())) {
        processKeyboardShortcutNode(e, id, pluginId);
      }
      else if (MOUSE_SHORTCUT_ELEMENT_NAME.equals(e.getName())) {
        processMouseShortcutNode(e, id, pluginId);
      }
      else {
        reportActionError(pluginId, "unexpected name of element \"" + e.getName() + "\"");
        return null;
      }
    }
    if (element.getAttributeValue(USE_SHORTCUT_OF_ATTR_NAME) != null) {
      ((KeymapManagerEx)myKeymapManager).bindShortcuts(element.getAttributeValue(USE_SHORTCUT_OF_ATTR_NAME), id);
    }

    // register action
    registerAction(id, stub, pluginId);
    return stub;
  }

  private static ResourceBundle getActionsResourceBundle(ClassLoader loader, IdeaPluginDescriptor plugin) {
    @NonNls final String resBundleName = plugin != null && !plugin.getPluginId().getIdString().equals("com.intellij") ? plugin.getResourceBundleBaseName() : ACTIONS_BUNDLE;
    ResourceBundle bundle = null;
    if (resBundleName != null) {
      bundle = getBundle(loader, resBundleName);
    }
    return bundle;
  }

  private static boolean isSecondary(Element element) {
    return "true".equalsIgnoreCase(element.getAttributeValue(SECONDARY));
  }

  private static void setIcon(@Nullable final String iconPath, final String className, final ClassLoader loader, final Presentation presentation,
                              final PluginId pluginId) {
    if (iconPath == null) return;

    try {
      final Class actionClass = Class.forName(className, true, loader);
      setIconFromClass(actionClass, loader, iconPath, className, presentation, pluginId);
    }
    catch (ClassNotFoundException e) {
      LOG.error(e);
      reportActionError(pluginId, "class with name \"" + className + "\" not found");
    }
    catch (NoClassDefFoundError e) {
      LOG.error(e);
      reportActionError(pluginId, "class with name \"" + className + "\" not found");
    }
  }

  private static void setIconFromClass(@NotNull final Class actionClass, @NotNull ClassLoader classLoader, @NotNull final String iconPath, final String className,
                                       final Presentation presentation, final PluginId pluginId) {
    //try to find icon in idea class path
    Icon icon = IconLoader.findIcon(iconPath, actionClass);
    if (icon == null) icon = IconLoader.findIcon(iconPath, classLoader);
    if (icon == null) {
     reportActionError(pluginId, "Icon cannot be found in '" + iconPath + "', action class='" + className + "'");
    }
    else {
      presentation.setIcon(icon);
    }
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

  private AnAction processGroupElement(Element element, final ClassLoader loader, PluginId pluginId) {
    final Application app = ApplicationManager.getApplication();
    final IdeaPluginDescriptor plugin = app.getPlugin(pluginId);
    ResourceBundle bundle = getActionsResourceBundle(loader, plugin);

    if (!GROUP_ELEMENT_NAME.equals(element.getName())) {
      reportActionError(pluginId, "unexpected name of element \"" + element.getName() + "\"");
      return null;
    }
    String className = element.getAttributeValue(CLASS_ATTR_NAME);
    if (className == null) { // use default group if class isn't specified
      className = DefaultActionGroup.class.getName();
    }
    try {
      Class aClass = Class.forName(className, true, loader);
      Object obj = new ConstructorInjectionComponentAdapter(className, aClass).getComponentInstance(ApplicationManager.getApplication().getPicoContainer());

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
      ActionGroup group = (ActionGroup)obj;
      // read ID and register loaded group
      String id = element.getAttributeValue(ID_ATTR_NAME);
      if (id != null && id.length() == 0) {
        reportActionError(pluginId, "ID of the group cannot be an empty string");
        return null;
      }
      if (Boolean.valueOf(element.getAttributeValue(INTERNAL_ATTR_NAME)).booleanValue() && !ApplicationManagerEx.getApplicationEx().isInternal()) {
        myNotRegisteredInternalActionIds.add(id);
        return null;
      }

      if (id != null) {
        registerAction(id, group);
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
      for (final Object o : element.getChildren()) {
        Element child = (Element)o;
        String name = child.getName();
        if (ACTION_ELEMENT_NAME.equals(name)) {
          AnAction action = processActionElement(child, loader, pluginId);
          if (action != null) {
            assertActionIsGroupOrStub(action);
            ((DefaultActionGroup)group).addAction(action, Constraints.LAST, this).setAsSecondary(isSecondary(child));
          }
        }
        else if (SEPARATOR_ELEMENT_NAME.equals(name)) {
          processSeparatorNode((DefaultActionGroup)group, child, pluginId);
        }
        else if (GROUP_ELEMENT_NAME.equals(name)) {
          AnAction action = processGroupElement(child, loader, pluginId);
          if (action != null) {
            ((DefaultActionGroup)group).add(action, this);
          }
        }
        else if (ADD_TO_GROUP_ELEMENT_NAME.equals(name)) {
          processAddToGroupNode(group, child, pluginId, isSecondary(child));
        }
        else if (REFERENCE_ELEMENT_NAME.equals(name)) {
          AnAction action = processReferenceElement(child, pluginId);
          if (action != null) {
            ((DefaultActionGroup)group).addAction(action, Constraints.LAST, this).setAsSecondary(isSecondary(child));
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

    for (final Object o : element.getChildren()) {
      Element child = (Element)o;
      if (ADD_TO_GROUP_ELEMENT_NAME.equals(child.getName())) {
        processAddToGroupNode(action, child, pluginId, isSecondary(child));
      }
    }
  }

  private static final Map<String, ResourceBundle> ourBundlesCache = new HashMap<String, ResourceBundle>();

  private static ResourceBundle getBundle(final ClassLoader loader, final String resBundleName) {

    if (ourBundlesCache.containsKey(resBundleName)) {
      return ourBundlesCache.get(resBundleName);
    }

    final ResourceBundle bundle = ResourceBundle.getBundle(resBundleName, Locale.getDefault(), loader);

    ourBundlesCache.put(resBundleName, bundle);

    return bundle;
  }

  /**\
   * @param element description of link
   * @param pluginId
   * @param secondary
   */
  private void processAddToGroupNode(AnAction action, Element element, final PluginId pluginId, boolean secondary) {
    // Real subclasses of AnAction should not be here
    if (!(action instanceof Separator)) {
      assertActionIsGroupOrStub(action);
    }

    String actionName = action instanceof ActionStub ? ((ActionStub)action).getClassName() : action.getClass().getName();

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
    final Anchor anchor = parseAnchor(element.getAttributeValue(ANCHOR_ELEMENT_NAME),
                                      actionName, pluginId);
    if (anchor == null) {
      return;
    }

    final String relativeToActionId = element.getAttributeValue(RELATIVE_TO_ACTION_ATTR_NAME);
    if (!checkRelativeToAction(relativeToActionId, anchor, actionName, pluginId)) {
      return;
    }
    final DefaultActionGroup group = (DefaultActionGroup)parentGroup;
    group.addAction(action, new Constraints(anchor, relativeToActionId), this).setAsSecondary(secondary);
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

  @Nullable
  public AnAction getParentGroup(final String groupId,
                                 @Nullable final String actionName,
                                 @Nullable final PluginId pluginId) {
    if (groupId == null || groupId.length() == 0) {
      reportActionError(pluginId, actionName + ": attribute \"group-id\" should be defined");
      return null;
    }
    AnAction parentGroup = getActionImpl(groupId, true);
    if (parentGroup == null) {
      reportActionError(pluginId, actionName + ": action with id \"" + groupId + "\" isn't registered; action will be added to the \"Other\" group");
      parentGroup = getActionImpl(IdeActions.GROUP_OTHER_MENU, true);
    }
    if (!(parentGroup instanceof DefaultActionGroup)) {
      reportActionError(pluginId, actionName + ": action with id \"" + groupId + "\" should be instance of " + DefaultActionGroup.class.getName() +
                                  " but was " + parentGroup.getClass());
      return null;
    }
    return parentGroup;
  }

  /**
   * @param parentGroup group wich is the parent of the separator. It can be <code>null</code> in that
   *                    case separator will be added to group described in the <add-to-group ....> subelement.
   * @param element     XML element which represent separator.
   */
  private void processSeparatorNode(DefaultActionGroup parentGroup, Element element, PluginId pluginId) {
    if (!SEPARATOR_ELEMENT_NAME.equals(element.getName())) {
      reportActionError(pluginId, "unexpected name of element \"" + element.getName() + "\"");
      return;
    }
    Separator separator = Separator.getInstance();
    if (parentGroup != null) {
      parentGroup.add(separator, this);
    }
    // try to find inner <add-to-parent...> tag
    for (final Object o : element.getChildren()) {
      Element child = (Element)o;
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
    if (keymapName == null || keymapName.trim().length() == 0) {
      reportActionError(pluginId, "attribute \"keymap\" should be defined");
      return;
    }
    Keymap keymap = myKeymapManager.getKeymap(keymapName);
    if (keymap == null) {
      reportActionError(pluginId, "keymap \"" + keymapName + "\" not found");
      return;
    }
    final String removeOption = element.getAttributeValue(REMOVE_SHORTCUT_ATTR_NAME);
    final KeyboardShortcut shortcut = new KeyboardShortcut(firstKeyStroke, secondKeyStroke);
    if (Boolean.valueOf(removeOption)) {
      keymap.removeShortcut(actionId, shortcut);
    } else {
      keymap.addShortcut(actionId, shortcut);
    }
  }

  private static void processMouseShortcutNode(Element element, String actionId, PluginId pluginId) {
    String keystrokeString = element.getAttributeValue(KEYSTROKE_ATTR_NAME);
    if (keystrokeString == null || keystrokeString.trim().length() == 0) {
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
    if (keymapName == null || keymapName.length() == 0) {
      reportActionError(pluginId, "attribute \"keymap\" should be defined");
      return;
    }
    Keymap keymap = KeymapManager.getInstance().getKeymap(keymapName);
    if (keymap == null) {
      reportActionError(pluginId, "keymap \"" + keymapName + "\" not found");
      return;
    }

    final String removeOption = element.getAttributeValue(REMOVE_SHORTCUT_ATTR_NAME);
    if (Boolean.valueOf(removeOption)) {
      keymap.removeShortcut(actionId, shortcut);
    } else {
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

    if (ref == null || ref.length() == 0) {
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

  private void processActionsElement(Element element, ClassLoader loader, PluginId pluginId) {
    if (!ACTIONS_ELEMENT_NAME.equals(element.getName())) {
      reportActionError(pluginId, "unexpected name of element \"" + element.getName() + "\"");
      return;
    }
    synchronized (myLock) {
      for (final Object o : element.getChildren()) {
        Element child = (Element)o;
        processActionsChildElement(loader, pluginId, child);
      }
    }
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

  private static void assertActionIsGroupOrStub(final AnAction action) {
    if (!(action instanceof ActionGroup || action instanceof ActionStub)) {
      LOG.error("Action : " + action + "; class: " + action.getClass());
    }
  }

  public void registerAction(@NotNull String actionId, @NotNull AnAction action, @Nullable PluginId pluginId) {
    synchronized (myLock) {
      if (myId2Action.containsKey(actionId)) {
        reportActionError(pluginId, "action with the ID \"" + actionId + "\" was already registered. Action being registered is " + action.toString() + 
                                    "; Registered action is " +
                                       myId2Action.get(actionId) + getPluginInfo(pluginId));
        return;
      }
      if (myAction2Id.containsKey(action)) {
        reportActionError(pluginId, "action was already registered for another ID. ID is " + myAction2Id.get(action) +
                                    getPluginInfo(pluginId));
        return;
      }
      myId2Action.put(actionId, action);
      myId2Index.put(actionId, myRegisteredActionsCount++);
      myAction2Id.put(action, actionId);
      if (pluginId != null && !(action instanceof ActionGroup)){
        THashSet<String> pluginActionIds = myPlugin2Id.get(pluginId);
        if (pluginActionIds == null){
          pluginActionIds = new THashSet<String>();
          myPlugin2Id.put(pluginId, pluginActionIds);
        }
        pluginActionIds.add(actionId);
      }
      action.registerCustomShortcutSet(new ProxyShortcutSet(actionId, myKeymapManager), null);
    }
  }

  private static void reportActionError(final PluginId pluginId, @NonNls final String message) {
    if (pluginId == null) {
      LOG.error(message);
    }
    else {
      LOG.error(new PluginException(message, null, pluginId));
    }
  }

  @NonNls
  private static String getPluginInfo(@Nullable PluginId id) {
    if (id != null) {
      final IdeaPluginDescriptor plugin = ApplicationManager.getApplication().getPlugin(id);
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

  public void registerAction(@NotNull String actionId, @NotNull AnAction action) {
    registerAction(actionId, action, null);
  }

  public void unregisterAction(@NotNull String actionId) {
    synchronized (myLock) {
      if (!myId2Action.containsKey(actionId)) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("action with ID " + actionId + " wasn't registered");
          return;
        }
      }
      AnAction oldValue = (AnAction)myId2Action.remove(actionId);
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
  public String getComponentName() {
    return "ActionManager";
  }

  public Comparator<String> getRegistrationOrderComparator() {
    return new Comparator<String>() {
      public int compare(String id1, String id2) {
        return myId2Index.get(id1) - myId2Index.get(id2);
      }
    };
  }

  public String[] getPluginActions(PluginId pluginName) {
    if (myPlugin2Id.containsKey(pluginName)){
      final THashSet<String> pluginActions = myPlugin2Id.get(pluginName);
      return ArrayUtil.toStringArray(pluginActions);
    }
    return ArrayUtil.EMPTY_STRING_ARRAY;
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

  public void queueActionPerformedEvent(final AnAction action, DataContext context, AnActionEvent event) {
    if (!myPopups.isEmpty()) {
      myQueuedNotifications.put(action, context);
    } else {
      fireAfterActionPerformed(action, context, event);
    }
  }


  public boolean isActionPopupStackEmpty() {
    return myPopups.isEmpty();
  }

  @Override
  public boolean isTransparrentOnlyActionsUpdateNow() {
    return myTransparrentOnlyUpdate;
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

  private AnActionListener[] getActionListeners() {
    if (myCachedActionListeners == null) {
      myCachedActionListeners = myActionListeners.toArray(new AnActionListener[myActionListeners.size()]);
    }

    return myCachedActionListeners;
  }

  public void addAnActionListener(AnActionListener listener) {
    myActionListeners.add(listener);
    myCachedActionListeners = null;
  }

  public void addAnActionListener(final AnActionListener listener, final Disposable parentDisposable) {
    addAnActionListener(listener);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        removeAnActionListener(listener);
      }
    });
  }

  public void removeAnActionListener(AnActionListener listener) {
    myActionListeners.remove(listener);
    myCachedActionListeners = null;
  }

  public void fireBeforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
    if (action != null) {
      myPrevPerformedActionId = myLastPreformedActionId;
      myLastPreformedActionId = getId(action);
      IdeaLogger.ourLastActionId = myLastPreformedActionId;
    }
    AnActionListener[] listeners = getActionListeners();
    for (AnActionListener listener : listeners) {
      listener.beforeActionPerformed(action, dataContext, event);
    }
  }

  public void fireAfterActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
    if (action != null) {
      myPrevPerformedActionId = myLastPreformedActionId;
      myLastPreformedActionId = getId(action);
      IdeaLogger.ourLastActionId = myLastPreformedActionId;
    }
    AnActionListener[] listeners = getActionListeners();
    for (AnActionListener listener : listeners) {
      try {
        listener.afterActionPerformed(action, dataContext, event);
      }
      catch(AbstractMethodError e) {
        // ignore
      }
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

  public void fireBeforeEditorTyping(char c, DataContext dataContext) {
    myLastTimeEditorWasTypedIn = System.currentTimeMillis();
    AnActionListener[] listeners = getActionListeners();
    for (AnActionListener listener : listeners) {
      listener.beforeEditorTyping(c, dataContext);
    }
  }

  public String getLastPreformedActionId() {
    return myLastPreformedActionId;
  }

  public String getPrevPreformedActionId() {
    return myPrevPerformedActionId;
  }

  public Set<String> getActionIds(){
    return new HashSet<String>(myId2Action.keySet());
  }

  private int myActionsPreloaded = 0;

  public void preloadActions() {
    if (myPreloadActionsRunnable == null) {
      myPreloadActionsRunnable = new Runnable() {
        public void run() {
          doPreloadActions();
        }
      };
      ApplicationManager.getApplication().executeOnPooledThread(myPreloadActionsRunnable);
    }
  }

  private void doPreloadActions() {
    try {
      Thread.sleep(5000); // wait for project initialization to complete
    }
    catch (InterruptedException e) {
      // ignore
    }
    preloadActionGroup(IdeActions.GROUP_EDITOR_POPUP);
    preloadActionGroup(IdeActions.GROUP_EDITOR_TAB_POPUP);
    preloadActionGroup(IdeActions.GROUP_PROJECT_VIEW_POPUP);
    preloadActionGroup(IdeActions.GROUP_MAIN_MENU);
    // TODO anything else?
    LOG.debug("Actions preloading completed");
  }

  public void preloadActionGroup(final String groupId) {
    final AnAction action = getAction(groupId);
    if (action instanceof ActionGroup) {
      preloadActionGroup((ActionGroup) action);
    }
  }

  private void preloadActionGroup(final ActionGroup group) {
    final AnAction[] children = ApplicationManager.getApplication().runReadAction(new Computable<AnAction[]>() {
      public AnAction[] compute() {
        if (ApplicationManager.getApplication().isDisposed()) {
          return AnAction.EMPTY_ARRAY;
        }

        return group.getChildren(null);
      }
    });
    for (AnAction action : children) {
      if (action instanceof PreloadableAction) {
        ((PreloadableAction)action).preload();
      }
      else if (action instanceof ActionGroup) {
        preloadActionGroup((ActionGroup)action);
      }
      
      myActionsPreloaded++;
      if (myActionsPreloaded % 10 == 0) {
        try {
          Thread.sleep(300);
        }
        catch (InterruptedException e) {
          // ignore
        }
      }
    }
  }

  private class MyTimer extends Timer implements ActionListener {
    private final List<TimerListener> myTimerListeners = Collections.synchronizedList(new ArrayList<TimerListener>());
    private final List<TimerListener> myTransparrentTimerListeners = Collections.synchronizedList(new ArrayList<TimerListener>());
    private int myLastTimePerformed;

    MyTimer() {
      super(TIMER_DELAY, null);
      addActionListener(this);
      setRepeats(true);
     }

    public void addTimerListener(TimerListener listener, boolean transparent){
      if (transparent) {
        myTransparrentTimerListeners.add(listener);
      } else {
        myTimerListeners.add(listener);
      }
    }

    public void removeTimerListener(TimerListener listener, boolean transparent){
      if (transparent) {
       myTransparrentTimerListeners.remove(listener);
      } else {
        myTimerListeners.remove(listener);
      }
    }

    public void actionPerformed(ActionEvent e) {
      if (myLastTimeEditorWasTypedIn + UPDATE_DELAY_AFTER_TYPING > System.currentTimeMillis()) {
        return;
      }

      final int lastEventCount = myLastTimePerformed;
      myLastTimePerformed = ActivityTracker.getInstance().getCount();

      boolean transparentOnly = myLastTimePerformed == lastEventCount;

      try {
        HashSet<TimerListener> notified = new HashSet<TimerListener>();
        myTransparrentOnlyUpdate = transparentOnly;
        notifyListeners(myTransparrentTimerListeners, notified);

        if (transparentOnly) {
          return;
        }

        notifyListeners(myTimerListeners, notified);
      }
      finally {
        myTransparrentOnlyUpdate = false;
      }
    }

    private void notifyListeners(final List<TimerListener> timerListeners, final Set<TimerListener> notified) {
      final TimerListener[] listeners = timerListeners.toArray(new TimerListener[timerListeners.size()]);
      IdeFocusManager.getInstance(null).doWhenFocusSettlesDown(new Runnable() {
        public void run() {
          for (TimerListener listener : listeners) {
            if (timerListeners.contains(listener)) {
              if (!notified.contains(listener)) {
                notified.add(listener);
                runListenerAction(listener);
              }
            }
          }
        }
      });
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

  public ActionCallback tryToExecute(@NotNull final AnAction action, @NotNull final InputEvent inputEvent, @Nullable final Component contextComponent, @Nullable final String place,
                                     boolean now) {

    final Application app = ApplicationManager.getApplication();
    assert app.isDispatchThread();

    final ActionCallback result = new ActionCallback();
    final Runnable doRunnable = new Runnable() {
      public void run() {
        tryToExecuteNow(action, inputEvent, contextComponent, place, result);
      }
    };

    if (now) {
      doRunnable.run();
    } else {
      SwingUtilities.invokeLater(doRunnable);
    }
    
    return result;

  }

  private void tryToExecuteNow(final AnAction action, final InputEvent inputEvent, final Component contextComponent, final String place, final ActionCallback result) {
    final Presentation presenation = (Presentation)action.getTemplatePresentation().clone();

    IdeFocusManager.findInstanceByContext(getContextBy(contextComponent)).doWhenFocusSettlesDown(new Runnable() {
      public void run() {
        final DataContext context = getContextBy(contextComponent);

        AnActionEvent event = new AnActionEvent(
          inputEvent, context,
          place != null ? place : ActionPlaces.UNKNOWN,
          presenation, ActionManagerImpl.this,
          inputEvent.getModifiersEx()
        );

        ActionUtil.performDumbAwareUpdate(action, event, false);
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
          public void eventDispatched(AWTEvent event) {
            if (event.getID() == WindowEvent.WINDOW_OPENED ||event.getID() == WindowEvent.WINDOW_ACTIVATED) {
              if (!result.isProcessed()) {
                final WindowEvent we = (WindowEvent)event;
                IdeFocusManager.findInstanceByComponent(we.getWindow()).doWhenFocusSettlesDown(new Runnable() {
                  public void run() {
                    result.setDone();
                  }
                });
              }
            }
          }
        }, WindowEvent.WINDOW_EVENT_MASK, result);

        action.actionPerformed(event);
        result.setDone();
        queueActionPerformedEvent(action, context, event);
      }
    });
  }

  private static DataContext getContextBy(Component contextComponent) {
    final DataManager dataManager = DataManager.getInstance();
    return contextComponent != null ? dataManager.getDataContext(contextComponent) : dataManager.getDataContext();
  }
}
