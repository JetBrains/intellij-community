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
package com.intellij.openapi.components.impl.stores;

import com.intellij.diagnostic.IdeErrorsDialog;
import com.intellij.diagnostic.PluginException;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.notification.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ReflectionCache;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.fs.IFile;
import net.sf.cglib.core.CollectionUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

@SuppressWarnings({"deprecation"})
abstract class ComponentStoreImpl implements IComponentStore {

  private static final Logger LOG = Logger.getInstance("#com.intellij.components.ComponentStoreImpl");
  private final Map<String, Object> myComponents = Collections.synchronizedMap(new TreeMap<String, Object>());
  private final List<SettingsSavingComponent> mySettingsSavingComponents = Collections.synchronizedList(new ArrayList<SettingsSavingComponent>());
  @Nullable private SaveSessionImpl mySession;

  @Deprecated
  @Nullable
  private StateStorage getStateStorage(@NotNull final Storage storageSpec) throws StateStorage.StateStorageException {
    return getStateStorageManager().getStateStorage(storageSpec);
  }

  @Deprecated
  @Nullable
  private StateStorage getOldStorage(final Object component, final String componentName, final StateStorageOperation operation)
      throws StateStorage.StateStorageException {
    return getStateStorageManager().getOldStorage(component, componentName, operation);
  }

  protected StateStorage getDefaultsStorage() {
    throw new UnsupportedOperationException("Method getDefaultsStorage is not supported in " + getClass());
  }

  public String initComponent(@NotNull final Object component, final boolean service) {
    boolean isSerializable = component instanceof JDOMExternalizable ||
                             component instanceof PersistentStateComponent ||
                             component instanceof SettingsSavingComponent;

    if (!isSerializable) return null;

    if (component instanceof SettingsSavingComponent) {
      SettingsSavingComponent settingsSavingComponent = (SettingsSavingComponent)component;
      mySettingsSavingComponents.add(settingsSavingComponent);
    }

    final String[] componentName = {null};
    final Runnable r = new Runnable() {
      public void run() {
        if (component instanceof PersistentStateComponent) {
          componentName[0] = initPersistentComponent((PersistentStateComponent<?>)component, false);
        }
        else if (component instanceof JDOMExternalizable) {
          componentName[0] = initJdomExternalizable((JDOMExternalizable)component);
        }
      }
    };

    final ApplicationEx applicationEx = ApplicationManagerEx.getApplicationEx();
    if (applicationEx.isUnitTestMode()) {
      r.run();
    }
    else {
      applicationEx.runReadAction(r);
    }

    return componentName[0];
  }

  public boolean isSaving() {
    return mySession != null;
  }


  @NotNull
  public SaveSession startSave() throws IOException {
    try {
      final SaveSessionImpl session = createSaveSession();
      try {
        session.commit();
      }
      catch (Throwable e) {
        try {
          session.reset();
        }
        catch (Exception e1_ignored) {
          LOG.info(e1_ignored);
        }

        PluginId pluginId = IdeErrorsDialog.findPluginId(e);
        if (pluginId != null) {
          throw new PluginException(e, pluginId);
        }

        LOG.info(e);
        IOException ioException = new IOException(e.getMessage());
        ioException.initCause(e);
        throw ioException;
      }
      mySession = session;
      return mySession;
    }
    catch (StateStorage.StateStorageException e) {
      LOG.info(e);
      throw new IOException(e.getMessage());
    }
  }

  protected SaveSessionImpl createSaveSession() throws StateStorage.StateStorageException {
    return new SaveSessionImpl();
  }

  public void finishSave(@NotNull final SaveSession saveSession) {
    assert mySession == saveSession;
    mySession.finishSave();
    mySession = null;
  }

  private <T> void commitPersistentComponent(@NotNull final PersistentStateComponent<T> persistentStateComponent,
                                             @NotNull StateStorageManager.ExternalizationSession session) {
    Storage[] storageSpecs = getComponentStorageSpecs(persistentStateComponent, StateStorageOperation.WRITE);

    T state = persistentStateComponent.getState();
    if (state != null) {
      session
        .setState(storageSpecs, persistentStateComponent, getComponentName(persistentStateComponent), state);
    }
  }

  private static void commitJdomExternalizable(@NotNull final JDOMExternalizable component,
                                               @NotNull StateStorageManager.ExternalizationSession session) {
    final String componentName = getComponentName(component);

    session.setStateInOldStorage(component, componentName, component);
  }

  @Nullable
  String initJdomExternalizable(@NotNull JDOMExternalizable component) {
    final String componentName = getComponentName(component);

    myComponents.put(componentName, component);

    if (optimizeTestLoading()) return componentName;

    loadJdomDefaults(component, componentName);

    Element element = null;
    StateStorage stateStorage = getOldStorage(component, componentName, StateStorageOperation.READ);

    if (stateStorage == null) return null;
    element = getJdomState(component, componentName, stateStorage);

    if (element == null) return null;

    try {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Loading configuration for " + component.getClass());
      }
      component.readExternal(element);
    }
    catch (InvalidDataException e) {
      throw new InvalidComponentDataException(e);
    }

    validateUnusedMacros(componentName, true);

    return componentName;
  }

  private static String getComponentName(@NotNull final JDOMExternalizable component) {
    if ((component instanceof BaseComponent)) {
      return ((BaseComponent)component).getComponentName();
    }
    else {
      return component.getClass().getName();
    }
  }

  private void loadJdomDefaults(@NotNull final Object component, final String componentName) {
    try {
      StateStorage defaultsStorage = getDefaultsStorage();
      if (defaultsStorage == null) return;

      Element defaultState = getJdomState(component, componentName, defaultsStorage);
      if (defaultState == null) return;

      ((JDOMExternalizable)component).readExternal(defaultState);
    }
    catch (Exception e) {
      LOG.error("Cannot load defaults for " + component.getClass(), e);
    }
  }

  @Nullable
  private static Element getJdomState(final Object component, final String componentName, @NotNull final StateStorage defaultsStorage)
      throws StateStorage.StateStorageException {
    ComponentRoamingManager roamingManager = ComponentRoamingManager.getInstance();
    if (!roamingManager.typeSpecified(componentName)) {
      if (component instanceof RoamingTypeDisabled) {
         roamingManager.setRoamingType(componentName, RoamingType.DISABLED);
      }
      else if (component instanceof RoamingTypePerPlatform) {
        roamingManager.setRoamingType(componentName, RoamingType.PER_PLATFORM);
      }
      /*else {
        roamingManager.setRoamingType(componentName, RoamingType.PER_USER);
      }*/
    }
    return defaultsStorage.getState(component, componentName, Element.class, null);
  }

  @Nullable
  protected Project getProject() {
    return null;
  }

  private void validateUnusedMacros(@Nullable final String componentName, final boolean service) {
    final Project project = getProject();
    if (project == null) return;

    if (!ApplicationManager.getApplication().isHeadlessEnvironment() && !ApplicationManager.getApplication().isUnitTestMode()) {
      if (service && componentName != null && project.isInitialized()) {
        final TrackingPathMacroSubstitutor substitutor = getStateStorageManager().getMacroSubstitutor();
        if (substitutor != null) {
          StorageUtil.notifyUnknownMacros(substitutor, project, componentName);
        }
      }
    }

  }

  private <T> String initPersistentComponent(@NotNull final PersistentStateComponent<T> component, final boolean reloadData) {
    final String name = getComponentName(component);

    RoamingType roamingTypeFromComponent = getRoamingType(component);
    ComponentRoamingManager roamingManager = ComponentRoamingManager.getInstance();
    if (!roamingManager.typeSpecified(name)) {
      roamingManager.setRoamingType(name, roamingTypeFromComponent);
    }

    myComponents.put(name, component);
    if (optimizeTestLoading()) return name;

    Class<T> stateClass = getComponentStateClass(component);

    T state = null;
    //todo: defaults merging

    final StateStorage defaultsStorage = getDefaultsStorage();
    if (defaultsStorage != null) {
      state = defaultsStorage.getState(component, name, stateClass, null);
    }

    Storage[] storageSpecs = getComponentStorageSpecs(component, StateStorageOperation.READ);

    for (Storage storageSpec : storageSpecs) {
      StateStorage stateStorage = getStateStorage(storageSpec);
      if (stateStorage == null || !stateStorage.hasState(component, name, stateClass, reloadData)) continue;
      state = stateStorage.getState(component, name, stateClass, state);
    }

    if (state != null) {
      component.loadState(state);
    }

    validateUnusedMacros(name, true);

    return name;
  }

  private static RoamingType getRoamingType(final PersistentStateComponent component) {
    if (component instanceof RoamingTypeDisabled) {
       return RoamingType.DISABLED;
    }
    else if (component instanceof RoamingTypePerPlatform) {
      return RoamingType.PER_PLATFORM;
    }

    final State stateSpec = getStateSpec(component);
    assert stateSpec != null;

    return stateSpec.roamingType();

  }

  @NotNull
  private static <T> Class<T> getComponentStateClass(@NotNull final PersistentStateComponent<T> persistentStateComponent) {
    final Class persistentStateComponentClass = PersistentStateComponent.class;
    Class componentClass = persistentStateComponent.getClass();

    nextSuperClass:
    while (true) {
      final Class[] interfaces = ReflectionCache.getInterfaces(componentClass);

      for (Class anInterface : interfaces) {
        if (anInterface.equals(persistentStateComponentClass)) {
          break nextSuperClass;
        }
      }

      componentClass = componentClass.getSuperclass();
    }

    final Type type = ReflectionUtil.resolveVariable(persistentStateComponentClass.getTypeParameters()[0], componentClass);

    //noinspection unchecked
    return (Class<T>)ReflectionUtil.getRawType(type);
  }

  private static String getComponentName(@NotNull final PersistentStateComponent<?> persistentStateComponent) {
    final State stateSpec = getStateSpec(persistentStateComponent);
    if (stateSpec == null) {
      LOG.error("Null state spec for " + persistentStateComponent);
    }
    return stateSpec.name();
  }

  private static <T> State getStateSpec(@NotNull final PersistentStateComponent<T> persistentStateComponent) {
    final Class<? extends PersistentStateComponent> aClass = persistentStateComponent.getClass();
    final State stateSpec = aClass.getAnnotation(State.class);
    if (stateSpec == null) {
      final PluginId pluginId = PluginManager.getPluginByClassName(aClass.getName());
      if (pluginId != null) {
        throw new PluginException("No @State annotation found in " + aClass, pluginId);
      }
      throw new RuntimeException("No @State annotation found in " + aClass);
    }
    return stateSpec;
  }


  @NotNull
  protected <T> Storage[] getComponentStorageSpecs(@NotNull final PersistentStateComponent<T> persistentStateComponent,
                                                   final StateStorageOperation operation) throws StateStorage.StateStorageException {
    final State stateSpec = getStateSpec(persistentStateComponent);

    final Storage[] storages = stateSpec.storages();

    if (storages.length == 1) return storages;

    assert storages.length > 0;


    final Class<StorageAnnotationsDefaultValues.NullStateStorageChooser> defaultClass =
        StorageAnnotationsDefaultValues.NullStateStorageChooser.class;

    final Class<? extends StateStorageChooser> storageChooserClass = stateSpec.storageChooser();
    final StateStorageChooser defaultStateStorageChooser = getDefaultStateStorageChooser();
    assert storageChooserClass != defaultClass || defaultStateStorageChooser != null : "State chooser not specified for: " +
                                                                                       persistentStateComponent.getClass();

    if (storageChooserClass == defaultClass) {
      return defaultStateStorageChooser.selectStorages(storages, persistentStateComponent, operation);
    }
    else {
      try {
        //noinspection unchecked
        final StateStorageChooser<PersistentStateComponent<T>> storageChooser = storageChooserClass.newInstance();
        return storageChooser.selectStorages(storages, persistentStateComponent, operation);
      }
      catch (InstantiationException e) {
        throw new StateStorage.StateStorageException(e);
      }
      catch (IllegalAccessException e) {
        throw new StateStorage.StateStorageException(e);
      }
    }
  }

  protected boolean optimizeTestLoading() {
    return false;
  }

  @Nullable
  protected StateStorageChooser getDefaultStateStorageChooser() {
    return null;
  }

  protected class SaveSessionImpl implements SaveSession {
    protected StateStorageManager.SaveSession myStorageManagerSaveSession;

    public SaveSessionImpl() {
      ShutDownTracker.getInstance().registerStopperThread(Thread.currentThread());
    }

    public List<IFile> getAllStorageFilesToSave(final boolean includingSubStructures) throws IOException {
      try {
        return myStorageManagerSaveSession.getAllStorageFilesToSave();
      }
      catch (StateStorage.StateStorageException e) {
        throw new IOException(e.getMessage());
      }
    }

    public SaveSession save() throws IOException {
      try {
        final SettingsSavingComponent[] settingsComponents =
            mySettingsSavingComponents.toArray(new SettingsSavingComponent[mySettingsSavingComponents.size()]);

        for (SettingsSavingComponent settingsSavingComponent : settingsComponents) {
          try {
            settingsSavingComponent.save();
          }
          catch (StateStorage.StateStorageException e) {
            LOG.info(e);
            throw new IOException(e.getMessage());
          }
          catch (Exception e) {
            LOG.error(e);
          }
        }

        myStorageManagerSaveSession.save();
      }
      catch (StateStorage.StateStorageException e) {
        LOG.info(e);
        throw new IOException(e.getMessage());
      }

      return this;
    }

    public void finishSave() {
      try {
        getStateStorageManager().finishSave(myStorageManagerSaveSession);
        myStorageManagerSaveSession = null;
      }
      finally {
        ShutDownTracker.getInstance().unregisterStopperThread(Thread.currentThread());
        mySession = null;
      }
    }

    public void reset() {
      try {
        getStateStorageManager().reset();
        myStorageManagerSaveSession = null;
      }
      finally {
        ShutDownTracker.getInstance().unregisterStopperThread(Thread.currentThread());
        mySession = null;
      }
    }

    protected void commit() throws StateStorage.StateStorageException {
      final StateStorageManager storageManager = getStateStorageManager();

      final StateStorageManager.ExternalizationSession session = storageManager.startExternalization();

      final String[] names = ArrayUtil.toStringArray(myComponents.keySet());

      for (String name : names) {
        Object component = myComponents.get(name);
        if (component instanceof PersistentStateComponent) {
          commitPersistentComponent((PersistentStateComponent<?>)component, session);
        }
        else if (component instanceof JDOMExternalizable) {
          commitJdomExternalizable((JDOMExternalizable)component, session);
        }
      }
      myStorageManagerSaveSession = storageManager.startSave(session);
    }

    @Nullable
    public Set<String> analyzeExternalChanges(final Set<Pair<VirtualFile, StateStorage>> changedFiles) {
      return myStorageManagerSaveSession.analyzeExternalChanges(changedFiles);
    }

    public List<IFile> getAllStorageFiles(final boolean includingSubStructures) {
      return myStorageManagerSaveSession.getAllStorageFiles();
    }

  }

  public boolean isReloadPossible(final Set<String> componentNames) {
    for (String componentName : componentNames) {
      final Object component = myComponents.get(componentName);

      if (component != null) {
        if (!(component instanceof PersistentStateComponent)) return false;

        final State stateSpec = getStateSpec((PersistentStateComponent<? extends Object>)component);
        if (stateSpec == null || !stateSpec.reloadable()) return false;
      }
    }

    return true;
  }

  public void reinitComponents(final Set<String> componentNames, final boolean reloadData) {
    for (String componentName : componentNames) {
      final PersistentStateComponent component = (PersistentStateComponent)myComponents.get(componentName);
      if (component != null) {
        initPersistentComponent(component, reloadData);
      }
    }
  }

  protected void doReload(final Set<Pair<VirtualFile, StateStorage>> changedFiles, @NotNull final Set<String> componentNames)
      throws StateStorage.StateStorageException {
    for (Pair<VirtualFile, StateStorage> pair : changedFiles) {
      assert pair != null;
      final StateStorage storage = pair.second;
      assert storage != null : "Null storage for: " + pair.first;
      storage.reload(componentNames);
    }
  }
}
