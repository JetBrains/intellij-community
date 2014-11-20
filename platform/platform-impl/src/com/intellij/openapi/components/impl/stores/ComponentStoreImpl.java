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

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.*;
import com.intellij.openapi.components.StateStorage.SaveSession;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.components.impl.stores.StateStorageManager.ExternalizationSession;
import com.intellij.openapi.components.store.ComponentSaveSession;
import com.intellij.openapi.components.store.ReadOnlyModificationException;
import com.intellij.openapi.components.store.StateStorageBase;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
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
  public void initComponent(@NotNull final Object component, boolean service) {
    if (component instanceof SettingsSavingComponent) {
      mySettingsSavingComponents.add((SettingsSavingComponent)component);
    }

    if (!(component instanceof JDOMExternalizable || component instanceof PersistentStateComponent)) {
      return;
    }

    ApplicationManagerEx.getApplicationEx().runReadAction(new Runnable() {
      @Override
      public void run() {
        try {
          if (component instanceof PersistentStateComponent) {
            initPersistentComponent((PersistentStateComponent<?>)component, null, false);
          }
          else {
            initJdomExternalizable((JDOMExternalizable)component);
          }
        }
        catch (StateStorageException e) {
          throw e;
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    });
  }

  @Override
  @Nullable
  public final ComponentSaveSession startSave() {
    ExternalizationSession externalizationSession = myComponents.isEmpty() ? null : getStateStorageManager().startExternalization();
    if (externalizationSession != null) {
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
    }

    for (SettingsSavingComponent settingsSavingComponent : mySettingsSavingComponents) {
      try {
        settingsSavingComponent.save();
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    return createSaveSession(externalizationSession == null ? null : externalizationSession.createSaveSession());
  }

  protected SaveSessionImpl createSaveSession(@Nullable SaveSession storageManagerSaveSession) {
    return storageManagerSaveSession == null ? null : new SaveSessionImpl(storageManagerSaveSession);
  }

  private <T> void commitPersistentComponent(@NotNull PersistentStateComponent<T> persistentStateComponent,
                                             @NotNull ExternalizationSession session) {
    T state = persistentStateComponent.getState();
    if (state != null) {
      Storage[] storageSpecs = getComponentStorageSpecs(persistentStateComponent, StoreUtil.getStateSpec(persistentStateComponent), StateStorageOperation.WRITE);
      session.setState(storageSpecs, persistentStateComponent, getComponentName(persistentStateComponent), state);
    }
  }

  private void initJdomExternalizable(@NotNull JDOMExternalizable component) {
    String componentName = ComponentManagerImpl.getComponentName(component);
    doAddComponent(componentName, component);

    if (optimizeTestLoading()) {
      return;
    }

    loadJdomDefaults(component, componentName);

    StateStorage stateStorage = getStateStorageManager().getOldStorage(component, componentName, StateStorageOperation.READ);
    if (stateStorage == null) {
      return;
    }

    Element element = getJdomState(component, componentName, stateStorage);
    if (element == null) {
      return;
    }

    try {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Loading configuration for " + component.getClass());
      }
      component.readExternal(element);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
      return;
    }

    validateUnusedMacros(componentName, true);
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

  private <T> String initPersistentComponent(@NotNull PersistentStateComponent<T> component, @Nullable Set<StateStorage> changedStorages, boolean reloadData) {
    State stateSpec = StoreUtil.getStateSpec(component);
    String name = stateSpec.name();
    if (changedStorages == null || !reloadData) {
      doAddComponent(name, component);
    }
    if (optimizeTestLoading()) {
      return name;
    }

    Class<T> stateClass = getComponentStateClass(component);
    T state = null;
    //todo: defaults merging
    StateStorage defaultsStorage = getDefaultsStorage();
    if (defaultsStorage != null) {
      state = defaultsStorage.getState(component, name, stateClass, null);
    }

    Storage[] storageSpecs = getComponentStorageSpecs(component, stateSpec, StateStorageOperation.READ);
    for (Storage storageSpec : storageSpecs) {
      StateStorage stateStorage = getStateStorageManager().getStateStorage(storageSpec);
      if (stateStorage != null && (stateStorage.hasState(component, name, stateClass, reloadData) ||
                                   (changedStorages != null && changedStorages.contains(stateStorage)))) {
        state = stateStorage.getState(component, name, stateClass, state);
        if (state instanceof Element) {
          // actually, our DefaultStateSerializer.deserializeState doesn't perform merge states if state is Element,
          // storages are ordered by priority (first has higher priority), so, in this case we must just use first state
          // https://youtrack.jetbrains.com/issue/IDEA-130930. More robust solution must be implemented later.
          break;
        }
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
    return StoreUtil.getStateSpec(persistentStateComponent).name();
  }

  @NotNull
  private <T> Storage[] getComponentStorageSpecs(@NotNull PersistentStateComponent<T> persistentStateComponent,
                                                 @NotNull State stateSpec,
                                                 @NotNull StateStorageOperation operation) {
    Storage[] storages = stateSpec.storages();
    if (storages.length == 1) {
      return storages;
    }
    assert storages.length > 0;

    Class<? extends StateStorageChooser> storageChooserClass = stateSpec.storageChooser();
    if (storageChooserClass == StateStorageChooser.class) {
      StateStorageChooser<PersistentStateComponent<?>> defaultStateStorageChooser = getDefaultStateStorageChooser();
      assert defaultStateStorageChooser != null : "State chooser not specified for: " + persistentStateComponent.getClass();
      return defaultStateStorageChooser.selectStorages(storages, persistentStateComponent, operation);
    }
    else if (storageChooserClass == LastStorageChooserForWrite.class) {
      return LastStorageChooserForWrite.INSTANCE.selectStorages(storages, persistentStateComponent, operation);
    }
    else {
      @SuppressWarnings("unchecked")
      StateStorageChooser<PersistentStateComponent<T>> storageChooser = ReflectionUtil.newInstance(storageChooserClass);
      return storageChooser.selectStorages(storages, persistentStateComponent, operation);
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

  protected static class SaveSessionImpl implements ComponentSaveSession {
    private final SaveSession myStorageManagerSaveSession;

    public SaveSessionImpl(@Nullable SaveSession storageManagerSaveSession) {
      myStorageManagerSaveSession = storageManagerSaveSession;
    }

    @Override
    public void save(@NotNull List<Pair<SaveSession, VirtualFile>> readonlyFiles) {
      if (myStorageManagerSaveSession != null) {
        executeSave(myStorageManagerSaveSession, readonlyFiles);
      }
    }
  }

  @Override
  public boolean isReloadPossible(@NotNull final Set<String> componentNames) {
    for (String componentName : componentNames) {
      final Object component = myComponents.get(componentName);
      if (component != null && (!(component instanceof PersistentStateComponent) || !StoreUtil.getStateSpec((PersistentStateComponent<?>)component).reloadable())) {
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
      if (component != null && (!(component instanceof PersistentStateComponent) || !StoreUtil.getStateSpec((PersistentStateComponent<?>)component).reloadable())) {
        if (notReloadableComponents == null) {
          notReloadableComponents = new LinkedHashSet<String>();
        }
        notReloadableComponents.add(componentName);
      }
    }
    return notReloadableComponents == null ? Collections.<String>emptySet() : notReloadableComponents;
  }

  @Override
  public void reinitComponents(@NotNull Set<String> componentNames, boolean reloadData) {
    reinitComponents(componentNames, Collections.<String>emptySet(), Collections.<StateStorage>emptySet());
  }

  protected boolean reinitComponent(@NotNull String componentName, @NotNull Set<StateStorage> changedStorages) {
    PersistentStateComponent component = (PersistentStateComponent)myComponents.get(componentName);
    if (component == null) {
      return false;
    }
    else {
      boolean changedStoragesEmpty = changedStorages.isEmpty();
      initPersistentComponent(component, changedStoragesEmpty ? null : changedStorages, changedStoragesEmpty);
      return true;
    }
  }

  @NotNull
  protected abstract MessageBus getMessageBus();

  @Override
  @Nullable
  public final Collection<String> reload(@NotNull MultiMap<StateStorage, VirtualFile> changedStorages) {
    if (changedStorages.isEmpty()) {
      return Collections.emptySet();
    }

    Set<String> componentNames = new SmartHashSet<String>();
    for (StateStorage storage : changedStorages.keySet()) {
      try {
        // we must update (reload in-memory storage data) even if non-reloadable component will be detected later
        // not saved -> user does own modification -> new (on disk) state will be overwritten and not applied
        storage.analyzeExternalChangesAndUpdateIfNeed(changedStorages.get(storage), componentNames);
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    if (componentNames.isEmpty()) {
      return Collections.emptySet();
    }

    Collection<String> notReloadableComponents = getNotReloadableComponents(componentNames);
    reinitComponents(componentNames, notReloadableComponents, changedStorages.keySet());
    return notReloadableComponents.isEmpty() ? null : notReloadableComponents;
  }

  // used in settings repository plugin
  public void reinitComponents(@NotNull Set<String> componentNames, @NotNull Collection<String> notReloadableComponents, @NotNull Set<StateStorage> changedStorages) {
    MessageBus messageBus = getMessageBus();
    messageBus.syncPublisher(BatchUpdateListener.TOPIC).onBatchUpdateStarted();
    try {
      for (String componentName : componentNames) {
        if (!notReloadableComponents.contains(componentName)) {
          reinitComponent(componentName, changedStorages);
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
  public static ReloadComponentStoreStatus reloadStore(@NotNull Collection<Pair<VirtualFile, StateStorage>> changedStorages, @NotNull IComponentStore.Reloadable store) {
    MultiMap<StateStorage, VirtualFile> storageToFiles = MultiMap.createLinkedSet();
    for (Pair<VirtualFile, StateStorage> pair : changedStorages) {
      storageToFiles.putValue(pair.second, pair.first);
    }

    Collection<String> notReloadableComponents;
    boolean willBeReloaded = false;
    try {
      AccessToken token = WriteAction.start();
      try {
        notReloadableComponents = store.reload(storageToFiles);
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

      willBeReloaded = askToRestart(store, notReloadableComponents, changedStorages);
      return willBeReloaded ? ReloadComponentStoreStatus.RESTART_AGREED : ReloadComponentStoreStatus.RESTART_CANCELLED;
    }
    finally {
      if (!willBeReloaded) {
        for (StateStorage storage : storageToFiles.keySet()) {
          if (storage instanceof StateStorageBase) {
            ((StateStorageBase)storage).enableSaving();
          }
        }
      }
    }
  }

  // used in settings repository plugin
  public static boolean askToRestart(@NotNull Reloadable store,
                                     @NotNull Collection<String> notReloadableComponents,
                                     @Nullable Collection<Pair<VirtualFile, StateStorage>> changedStorages) {
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
      if (changedStorages != null) {
        for (Pair<VirtualFile, StateStorage> cause : changedStorages) {
          StateStorage storage = cause.getSecond();
          if (storage instanceof StateStorageBase) {
            ((StateStorageBase)storage).disableSaving();
          }
        }
      }
      return true;
    }
    return false;
  }
}
