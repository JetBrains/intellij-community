/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.*;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.components.store.ComponentSaveSession;
import com.intellij.openapi.components.store.ReadOnlyModificationException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.messages.MessageBus;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.*;

@SuppressWarnings({"deprecation"})
public abstract class ComponentStoreImpl implements IComponentStore {
  private static final Logger LOG = Logger.getInstance(ComponentStoreImpl.class);
  private final Map<String, Object> myComponents = Collections.synchronizedMap(new THashMap<String, Object>());
  private final List<SettingsSavingComponent> mySettingsSavingComponents = Collections.synchronizedList(new ArrayList<SettingsSavingComponent>());
  @Nullable private SaveSessionImpl mySession;

  @Nullable
  protected abstract StateStorage getDefaultsStorage();

  @Override
  public void initComponent(@NotNull final Object component, final boolean service) {
    if (component instanceof SettingsSavingComponent) {
      SettingsSavingComponent settingsSavingComponent = (SettingsSavingComponent)component;
      mySettingsSavingComponents.add(settingsSavingComponent);
    }

    boolean isSerializable = component instanceof JDOMExternalizable ||
                             component instanceof PersistentStateComponent;

    if (!isSerializable) return;

    try {
      ApplicationManagerEx.getApplicationEx().runReadAction(new Runnable() {
        @Override
        public void run() {
          if (component instanceof PersistentStateComponent) {
            initPersistentComponent((PersistentStateComponent<?>)component, false);
          }
          else {
            initJdomExternalizable((JDOMExternalizable)component);
          }
        }
      });
    }
    catch (StateStorageException e) {
      throw e;
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  @Override
  public boolean isSaving() {
    return mySession != null;
  }


  @Override
  @NotNull
  public ComponentSaveSession startSave() {
    SaveSessionImpl session = createSaveSession();
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

      throw new StateStorageException(e);
    }
    mySession = session;
    return mySession;
  }

  protected SaveSessionImpl createSaveSession() throws StateStorageException {
    return new SaveSessionImpl();
  }

  public void finishSave(@NotNull final ComponentSaveSession saveSession) {
    assert mySession == saveSession;
    mySession.finishSave();
    mySession = null;
  }

  private <T> void commitPersistentComponent(@NotNull final PersistentStateComponent<T> persistentStateComponent,
                                             @NotNull StateStorageManager.ExternalizationSession session) {
    T state = persistentStateComponent.getState();
    if (state != null) {
      Storage[] storageSpecs = getComponentStorageSpecs(persistentStateComponent, StateStorageOperation.WRITE);
      session.setState(storageSpecs, persistentStateComponent, getComponentName(persistentStateComponent), state);
    }
  }

  @Nullable
  private String initJdomExternalizable(@NotNull JDOMExternalizable component) {
    final String componentName = ComponentManagerImpl.getComponentName(component);

    doAddComponent(componentName, component);

    if (optimizeTestLoading()) {
      return componentName;
    }

    loadJdomDefaults(component, componentName);

    StateStorage stateStorage = getStateStorageManager().getOldStorage(component, componentName, StateStorageOperation.READ);

    if (stateStorage == null) return null;
    Element element = getJdomState(component, componentName, stateStorage);

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

  private void doAddComponent(String componentName, Object component) {
    Object existing = myComponents.get(componentName);
    if (existing != null && existing != component) {
      LOG.error("Conflicting component name '" + componentName + "': " + existing.getClass() + " and " + component.getClass());
    }
    myComponents.put(componentName, component);
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
      throws StateStorageException {
    ComponentRoamingManager roamingManager = ComponentRoamingManager.getInstance();
    if (component instanceof RoamingTypeDisabled) {
      roamingManager.setRoamingType(componentName, RoamingType.DISABLED);
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
    State spec = getStateSpec(component);
    final String name = spec.name();
    ComponentRoamingManager.getInstance().setRoamingType(name, spec.roamingType());

    doAddComponent(name, component);
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
      StateStorage stateStorage = getStateStorageManager().getStateStorage(storageSpec);
      if (stateStorage != null && stateStorage.hasState(component, name, stateClass, reloadData)) {
        state = stateStorage.getState(component, name, stateClass, state);
      }
    }

    if (state != null) {
      component.loadState(state);
    }

    validateUnusedMacros(name, true);

    return name;
  }

  @NotNull
  private static <T> Class<T> getComponentStateClass(@NotNull final PersistentStateComponent<T> persistentStateComponent) {
    final Class persistentStateComponentClass = PersistentStateComponent.class;

    Class componentClass = persistentStateComponent.getClass();

    nextSuperClass:
    while (true) {
      for (Class anInterface : componentClass.getInterfaces()) {
        if (anInterface.equals(persistentStateComponentClass)) {
          break nextSuperClass;
        }
      }

      componentClass = componentClass.getSuperclass();
    }

    final Type type = ReflectionUtil.resolveVariable(persistentStateComponentClass.getTypeParameters()[0], componentClass);
    assert type != null;
    //noinspection unchecked
    return (Class<T>)ReflectionUtil.getRawType(type);
  }

  @NotNull
  public static String getComponentName(@NotNull PersistentStateComponent<?> persistentStateComponent) {
    return getStateSpec(persistentStateComponent).name();
  }

  @NotNull
  private static <T> State getStateSpec(@NotNull final PersistentStateComponent<T> persistentStateComponent) {
    final Class<? extends PersistentStateComponent> aClass = persistentStateComponent.getClass();
    final State stateSpec = aClass.getAnnotation(State.class);
    if (stateSpec == null) {
      final PluginId pluginId = PluginManagerCore.getPluginByClassName(aClass.getName());
      if (pluginId != null) {
        throw new PluginException("No @State annotation found in " + aClass, pluginId);
      }
      throw new RuntimeException("No @State annotation found in " + aClass);
    }
    return stateSpec;
  }

  @NotNull
  protected <T> Storage[] getComponentStorageSpecs(@NotNull final PersistentStateComponent<T> persistentStateComponent,
                                                   final StateStorageOperation operation) throws StateStorageException {
    final State stateSpec = getStateSpec(persistentStateComponent);
    final Storage[] storages = stateSpec.storages();
    if (storages.length == 1) {
      return storages;
    }
    assert storages.length > 0;

    final Class<? extends StateStorageChooser> storageChooserClass = stateSpec.storageChooser();
    if (storageChooserClass == StateStorageChooser.class) {
      StateStorageChooser<PersistentStateComponent<?>> defaultStateStorageChooser = getDefaultStateStorageChooser();
      assert defaultStateStorageChooser != null : "State chooser not specified for: " + persistentStateComponent.getClass();
      return defaultStateStorageChooser.selectStorages(storages, persistentStateComponent, operation);
    }
    else if (storageChooserClass == LastStorageChooserForWrite.class) {
      return LastStorageChooserForWrite.INSTANCE.selectStorages(storages, persistentStateComponent, operation);
    }
    else {
      try {
        @SuppressWarnings("unchecked")
        StateStorageChooser<PersistentStateComponent<T>> storageChooser = ReflectionUtil.newInstance(storageChooserClass);
        return storageChooser.selectStorages(storages, persistentStateComponent, operation);
      }
      catch (RuntimeException e) {
        throw new StateStorageException(e);
      }
    }
  }

  protected boolean optimizeTestLoading() {
    return false;
  }

  @Nullable
  protected StateStorageChooser<PersistentStateComponent<?>> getDefaultStateStorageChooser() {
    return null;
  }

  protected static void executeSave(@NotNull StateStorageManager.SaveSession saveSession, @NotNull List<Pair<StateStorageManager.SaveSession, VirtualFile>> readonlyFiles) {
    try {
      saveSession.save();
    }
    catch (ReadOnlyModificationException e) {
      readonlyFiles.add(Pair.create(saveSession, e.getFile()));
    }
  }

  protected class SaveSessionImpl implements ComponentSaveSession {
    protected StateStorageManager.SaveSession myStorageManagerSaveSession;

    public SaveSessionImpl() {
      ShutDownTracker.getInstance().registerStopperThread(Thread.currentThread());
    }

    @NotNull
    @Override
    public ComponentSaveSession save(@NotNull List<Pair<StateStorageManager.SaveSession, VirtualFile>> readonlyFiles) {
      SettingsSavingComponent[] settingsComponents =
        mySettingsSavingComponents.toArray(new SettingsSavingComponent[mySettingsSavingComponents.size()]);
      for (SettingsSavingComponent settingsSavingComponent : settingsComponents) {
        try {
          settingsSavingComponent.save();
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }

      executeSave(myStorageManagerSaveSession, readonlyFiles);
      return this;
    }

    @Override
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

    @Override
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

    protected void commit() {
      final StateStorageManager storageManager = getStateStorageManager();
      final StateStorageManager.ExternalizationSession session = storageManager.startExternalization();

      String[] names = ArrayUtil.toStringArray(myComponents.keySet());
      Arrays.sort(names);
      for (String name : names) {
        Object component = myComponents.get(name);
        if (component instanceof PersistentStateComponent) {
          commitPersistentComponent((PersistentStateComponent<?>)component, session);
        }
        else if (component instanceof JDOMExternalizable) {
          session.setStateInOldStorage(component, ComponentManagerImpl.getComponentName(component), component);
        }
      }
      myStorageManagerSaveSession = storageManager.startSave(session);
    }

    @Override
    @Nullable
    public Set<String> analyzeExternalChanges(@NotNull final Set<Pair<VirtualFile, StateStorage>> changedFiles) {
      return myStorageManagerSaveSession.analyzeExternalChanges(changedFiles);
    }

    @Override
    public void collectAllStorageFiles(boolean includingSubStructures, @NotNull List<VirtualFile> files) {
      myStorageManagerSaveSession.collectAllStorageFiles(files);
    }
  }

  @Override
  public boolean isReloadPossible(@NotNull final Set<String> componentNames) {
    for (String componentName : componentNames) {
      final Object component = myComponents.get(componentName);
      if (component != null && (!(component instanceof PersistentStateComponent) || !getStateSpec((PersistentStateComponent<?>)component).reloadable())) {
        return false;
      }
    }

    return true;
  }

  @Override
  @NotNull
  public final Collection<String> getNotReloadableComponents(@NotNull Collection<String> componentNames) {
    Set<String> notReloadableComponents = null;
    for (String componentName : componentNames) {
      Object component = myComponents.get(componentName);
      if (component != null && (!(component instanceof PersistentStateComponent) || !getStateSpec((PersistentStateComponent<?>)component).reloadable())) {
        if (notReloadableComponents == null) {
          notReloadableComponents = new LinkedHashSet<String>();
        }
        notReloadableComponents.add(componentName);
      }
    }
    return notReloadableComponents == null ? Collections.<String>emptySet() : notReloadableComponents;
  }

  @Override
  public void reinitComponents(@NotNull final Set<String> componentNames, final boolean reloadData) {
    for (String componentName : componentNames) {
      final PersistentStateComponent component = (PersistentStateComponent)myComponents.get(componentName);
      if (component != null) {
        initPersistentComponent(component, reloadData);
      }
    }
  }

  protected void doReload(@NotNull Set<Pair<VirtualFile, StateStorage>> changedFiles, @NotNull Set<String> componentNames) {
    for (Pair<VirtualFile, StateStorage> pair : changedFiles) {
      assert pair != null;
      StateStorage storage = pair.second;
      assert storage != null : "Null storage for: " + pair.first;
      storage.reload(componentNames);
    }
  }

  @Nullable
  protected final Collection<String> reload(@NotNull Set<Pair<VirtualFile, StateStorage>> changedFiles, @NotNull MessageBus messageBus) {
    ComponentSaveSession saveSession = startSave();
    Set<String> componentNames;
    try {
      componentNames = saveSession.analyzeExternalChanges(changedFiles);
      if (componentNames == null) {
        return Collections.emptyList();
      }

      for (Pair<VirtualFile, StateStorage> pair : changedFiles) {
        if (pair.second == null) {
          return Collections.emptyList();
        }
      }

      Collection<String> currentNotReloadableComponents = getNotReloadableComponents(componentNames);

      StorageUtil.logStateDiffInfo(changedFiles, componentNames);

      if (!currentNotReloadableComponents.isEmpty()) {
        return currentNotReloadableComponents;
      }
    }
    finally {
      finishSave(saveSession);
    }

    if (!componentNames.isEmpty()) {
      messageBus.syncPublisher(BatchUpdateListener.TOPIC).onBatchUpdateStarted();

      try {
        doReload(changedFiles, componentNames);
        reinitComponents(componentNames, false);
      }
      finally {
        messageBus.syncPublisher(BatchUpdateListener.TOPIC).onBatchUpdateFinished();
      }
    }

    return null;
  }
}
