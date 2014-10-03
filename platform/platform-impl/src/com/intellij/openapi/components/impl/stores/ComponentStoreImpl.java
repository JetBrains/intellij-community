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

import com.intellij.diagnostic.PluginException;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.*;
import com.intellij.openapi.components.StateStorage.SaveSession;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.components.store.ComponentSaveSession;
import com.intellij.openapi.components.store.ReadOnlyModificationException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.RoamingTypeDisabled;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SmartHashSet;
import com.intellij.util.messages.MessageBus;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@SuppressWarnings({"deprecation"})
public abstract class ComponentStoreImpl implements IComponentStore.Reloadable {
  private static final Logger LOG = Logger.getInstance(ComponentStoreImpl.class);
  private final Map<String, Object> myComponents = Collections.synchronizedMap(new THashMap<String, Object>());
  private final List<SettingsSavingComponent> mySettingsSavingComponents = new CopyOnWriteArrayList<SettingsSavingComponent>();

  @Nullable
  protected abstract StateStorage getDefaultsStorage();

  @Override
  public void initComponent(@NotNull final Object component, final boolean service) {
    if (component instanceof SettingsSavingComponent) {
      mySettingsSavingComponents.add((SettingsSavingComponent)component);
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
  @Nullable
  public final ComponentSaveSession startSave() {
    if (myComponents.isEmpty()) {
      return null;
    }

    StateStorageManager storageManager = getStateStorageManager();
    StateStorageManager.ExternalizationSession externalizationSession = storageManager.startExternalization();
    if (externalizationSession == null) {
      return null;
    }

    String[] names = ArrayUtilRt.toStringArray(myComponents.keySet());
    Arrays.sort(names);
    for (String name : names) {
      Object component = myComponents.get(name);
      if (component instanceof PersistentStateComponent) {
        commitPersistentComponent((PersistentStateComponent<?>)component, externalizationSession);
      }
      else if (component instanceof JDOMExternalizable) {
        externalizationSession.setStateInOldStorage(component, ComponentManagerImpl.getComponentName(component), component);
      }
    }

    SaveSession storageManagerSaveSession = storageManager.startSave(externalizationSession);
    if (storageManagerSaveSession == null) {
      return null;
    }

    SaveSessionImpl session = createSaveSession();
    session.myStorageManagerSaveSession = storageManagerSaveSession;
    return session;
  }

  protected SaveSessionImpl createSaveSession() {
    return new SaveSessionImpl();
  }

  private <T> void commitPersistentComponent(@NotNull PersistentStateComponent<T> persistentStateComponent,
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
    if (stateStorage == null) {
      return null;
    }

    Element element = getJdomState(component, componentName, stateStorage);
    if (element == null) {
      return null;
    }

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

  private void loadJdomDefaults(@NotNull Object component, @NotNull String componentName) {
    try {
      StateStorage defaultsStorage = getDefaultsStorage();
      if (defaultsStorage == null) {
        return;
      }

      Element defaultState = getJdomState(component, componentName, defaultsStorage);
      if (defaultState == null) {
        return;
      }

      ((JDOMExternalizable)component).readExternal(defaultState);
    }
    catch (Exception e) {
      LOG.error("Cannot load defaults for " + component.getClass(), e);
    }
  }

  @Nullable
  private static Element getJdomState(final Object component, @NotNull String componentName, @NotNull StateStorage defaultsStorage) {
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

  protected static void executeSave(@NotNull SaveSession saveSession, @NotNull List<Pair<SaveSession, VirtualFile>> readonlyFiles) {
    try {
      saveSession.save();
    }
    catch (ReadOnlyModificationException e) {
      readonlyFiles.add(Pair.create(saveSession, e.getFile()));
    }
  }

  protected class SaveSessionImpl implements ComponentSaveSession {
    protected SaveSession myStorageManagerSaveSession;

    @NotNull
    @Override
    public ComponentSaveSession save(@NotNull List<Pair<SaveSession, VirtualFile>> readonlyFiles) {
      for (SettingsSavingComponent settingsSavingComponent : mySettingsSavingComponents) {
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
      getStateStorageManager().finishSave(myStorageManagerSaveSession);
      myStorageManagerSaveSession = null;
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
  public final void reinitComponents(@NotNull Set<String> componentNames, boolean reloadData) {
    reinitComponents(componentNames, Collections.<String>emptySet(), reloadData);
  }

  protected boolean reinitComponent(@NotNull String componentName, boolean reloadData) {
    PersistentStateComponent component = (PersistentStateComponent)myComponents.get(componentName);
    if (component == null) {
      return false;
    }
    else {
      initPersistentComponent(component, reloadData);
      return true;
    }
  }

  @NotNull
  protected abstract MessageBus getMessageBus();

  @Override
  @Nullable
  public final Collection<String> reload(@NotNull Collection<Pair<VirtualFile, StateStorage>> changedFiles) {
    Set<String> componentNames = new SmartHashSet<String>();
    for (Pair<VirtualFile, StateStorage> pair : changedFiles) {
      StateStorage storage = pair.second;
      try {
        // we must update (reload in-memory storage data) even if non-reloadable component will be detected later
        // not saved -> user does own modification -> new (on disk) state will be overwritten and not applied
        storage.analyzeExternalChangesAndUpdateIfNeed(changedFiles, componentNames);
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    if (componentNames.isEmpty()) {
      return Collections.emptySet();
    }

    Collection<String> notReloadableComponents = getNotReloadableComponents(componentNames);
    reinitComponents(componentNames, notReloadableComponents, false);
    return notReloadableComponents.isEmpty() ? null : notReloadableComponents;
  }

  @Override
  public final void reinitComponents(@NotNull Set<String> componentNames, @NotNull Collection<String> notReloadableComponents, boolean reloadData) {
    MessageBus messageBus = getMessageBus();
    messageBus.syncPublisher(BatchUpdateListener.TOPIC).onBatchUpdateStarted();
    try {
      for (String componentName : componentNames) {
        if (!notReloadableComponents.contains(componentName)) {
          reinitComponent(componentName, reloadData);
        }
      }
    }
    finally {
      messageBus.syncPublisher(BatchUpdateListener.TOPIC).onBatchUpdateFinished();
    }
  }

  public enum ReloadComponentStoreStatus {
    RESTART_AGREED,
    RESTART_CANCELLED,
    ERROR,
    SUCCESS,
  }

  @NotNull
  public static ReloadComponentStoreStatus reloadStore(@NotNull Collection<Pair<VirtualFile, StateStorage>> causes, @NotNull IComponentStore.Reloadable store) {
    Collection<String> notReloadableComponents;
    boolean willBeReloaded = false;
    try {
      AccessToken token = WriteAction.start();
      try {
        notReloadableComponents = store.reload(causes);
      }
      catch (Throwable e) {
        Messages.showWarningDialog(ProjectBundle.message("project.reload.failed", e.getMessage()),
                                   ProjectBundle.message("project.reload.failed.title"));
        return ReloadComponentStoreStatus.ERROR;
      }
      finally {
        token.finish();
      }

      if (ContainerUtil.isEmpty(notReloadableComponents)) {
        return ReloadComponentStoreStatus.SUCCESS;
      }

      willBeReloaded = askToRestart(store, notReloadableComponents, causes);
      return willBeReloaded ? ReloadComponentStoreStatus.RESTART_AGREED : ReloadComponentStoreStatus.RESTART_CANCELLED;
    }
    finally {
      if (!willBeReloaded) {
        for (Pair<VirtualFile, StateStorage> cause : causes) {
          if (cause.second instanceof XmlElementStorage) {
            ((XmlElementStorage)cause.second).enableSaving();
          }
        }
      }
    }
  }

  // used in settings repository plugin
  public static boolean askToRestart(@NotNull Reloadable store,
                                     @NotNull Collection<String> notReloadableComponents,
                                     @Nullable Collection<Pair<VirtualFile, StateStorage>> causes) {
    StringBuilder message = new StringBuilder();
    String storeName = store instanceof IApplicationStore ? "Application" : "Project";
    message.append(storeName).append(' ');
    message.append("components were changed externally and cannot be reloaded:\n\n");
    int count = 0;
    for (String component : notReloadableComponents) {
      if (count == 10) {
        message.append('\n').append("and ").append(notReloadableComponents.size() - count).append(" more").append('\n');
      }
      else {
        message.append(component).append('\n');
        count++;
      }
    }

    message.append("\nWould you like to ");
    if (store instanceof IApplicationStore) {
      message.append(ApplicationManager.getApplication().isRestartCapable() ? "restart" : "shutdown").append(' ');
      message.append(ApplicationNamesInfo.getInstance().getProductName()).append('?');
    }
    else {
      message.append("reload project?");
    }

    if (Messages.showYesNoDialog(message.toString(),
                                 storeName + " Files Changed", Messages.getQuestionIcon()) == Messages.YES) {
      if (causes != null) {
        for (Pair<VirtualFile, StateStorage> cause : causes) {
          StateStorage stateStorage = cause.getSecond();
          if (stateStorage instanceof XmlElementStorage) {
            ((XmlElementStorage)stateStorage).disableSaving();
          }
        }
      }
      return true;
    }
    return false;
  }
}
