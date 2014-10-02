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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.CurrentUserHolder;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.vfs.VirtualFile;
import gnu.trove.THashMap;
import gnu.trove.TObjectLongHashMap;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.filter.ElementFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public abstract class XmlElementStorage implements StateStorage, Disposable {
  private static final Logger LOG = Logger.getInstance(XmlElementStorage.class);

  private final static RoamingElementFilter DISABLED_ROAMING_ELEMENT_FILTER = new RoamingElementFilter(RoamingType.DISABLED);

  private static final String ATTR_NAME = "name";
  private static final String VERSION_FILE_SUFFIX = ".ver";

  protected TrackingPathMacroSubstitutor myPathMacroSubstitutor;
  @NotNull protected final String myRootElementName;
  protected StorageData myLoadedData;
  protected final StreamProvider myStreamProvider;
  protected final String myFileSpec;
  protected boolean myBlockSavingTheContent = false;
  private boolean mySavingDisabled = false;

  private final ComponentVersionProvider myLocalVersionProvider;
  protected final RemoteComponentVersionProvider myRemoteVersionProvider;

  private final RoamingType myRoamingType;

  private boolean myDisposed;

  protected XmlElementStorage(@NotNull String fileSpec,
                              @Nullable RoamingType roamingType,
                              @Nullable TrackingPathMacroSubstitutor pathMacroSubstitutor,
                              @NotNull Disposable parentDisposable,
                              @NotNull String rootElementName,
                              @Nullable StreamProvider streamProvider,
                              ComponentVersionProvider componentVersionProvider) {
    myFileSpec = fileSpec;
    myRoamingType = roamingType == null ? RoamingType.PER_USER : roamingType;
    myPathMacroSubstitutor = pathMacroSubstitutor;
    myRootElementName = rootElementName;
    myStreamProvider = myRoamingType == RoamingType.DISABLED ? null : streamProvider;
    Disposer.register(parentDisposable, this);

    myLocalVersionProvider = componentVersionProvider;
    myRemoteVersionProvider = streamProvider == null || !streamProvider.isVersioningRequired() ? null : new RemoteComponentVersionProvider();
  }

  protected boolean isDisposed() {
    return myDisposed;
  }

  @Nullable
  protected abstract Element loadLocalData();

  @Override
  public boolean hasState(@Nullable Object component, @NotNull String componentName, Class<?> aClass, boolean reloadData) throws StateStorageException {
    return getStorageData(reloadData).hasState(componentName);
  }

  @Override
  @Nullable
  public <T> T getState(Object component, @NotNull String componentName, @NotNull Class<T> stateClass, @Nullable T mergeInto) throws StateStorageException {
    Element state = getStorageData(false).getStateAndArchive(componentName);
    return DefaultStateSerializer.deserializeState(state, stateClass, mergeInto);
  }

  @NotNull
  protected StorageData getStorageData() {
    return getStorageData(false);
  }

  @NotNull
  private StorageData getStorageData(boolean reloadData) {
    if (myLoadedData != null && !reloadData) {
      return myLoadedData;
    }

    myLoadedData = loadData(true);
    return myLoadedData;
  }

  @NotNull
  protected StorageData loadData(boolean useProvidersData) {
    StorageData result = createStorageData();

    if (useProvidersData && myStreamProvider != null && myStreamProvider.isEnabled()) {
      try {
        Element element = loadDataFromStreamProvider();
        if (element != null) {
          loadState(result, element);
        }

        //noinspection deprecation
        if (!myStreamProvider.isVersioningRequired() && !(myStreamProvider instanceof OldStreamProviderAdapter || myStreamProvider instanceof CurrentUserHolder)) {
          // we don't use local data if has stream provider (we don't use this logic for old stream providers)
          return result;
        }
      }
      catch (Exception e) {
        LOG.warn(e);
      }
    }

    Element element = loadLocalData();
    if (element != null) {
      loadState(result, element);
    }

    return result;
  }

  @Nullable
  protected final Element loadDataFromStreamProvider() throws IOException, JDOMException {
    assert myStreamProvider != null;
    InputStream inputStream = myStreamProvider.loadContent(myFileSpec, myRoamingType);
    if (inputStream == null) {
      return null;
    }

    Element element = JDOMUtil.loadDocument(inputStream).getRootElement();
    filterOutOfDate(element);
    return element;
  }

  protected final void loadState(@NotNull StorageData result, @NotNull Element element) {
    result.load(element, myPathMacroSubstitutor, true);
  }

  @NotNull
  protected StorageData createStorageData() {
    return new StorageData(myRootElementName);
  }

  public void setDefaultState(final Element element) {
    myLoadedData = createStorageData();
    loadState(myLoadedData, element);
  }

  @Override
  @Nullable
  public final ExternalizationSession startExternalization() {
    return mySavingDisabled ? null : createSaveSession(getStorageData());
  }

  @Nullable
  @Override
  public SaveSession startSave(@NotNull ExternalizationSession externalizationSession) {
    if (mySavingDisabled) {
      return null;
    }
    else {
      MySaveSession session = (MySaveSession)externalizationSession;
      return session.myCopiedStorageData == null ? null : session;
    }
  }

  protected abstract MySaveSession createSaveSession(@NotNull StorageData storageData);

  public void disableSaving() {
    mySavingDisabled = true;
  }

  public void enableSaving() {
    mySavingDisabled = false;
  }

  @Nullable
  protected final Element getElement(@NotNull StorageData data, boolean collapsePaths, @NotNull Map<String, Element> newLiveStates) {
    Element element = data.save(newLiveStates);
    if (element == null || JDOMUtil.isEmpty(element)) {
      return null;
    }

    if (collapsePaths && myPathMacroSubstitutor != null) {
      try {
        myPathMacroSubstitutor.collapsePaths(element);
      }
      finally {
        myPathMacroSubstitutor.reset();
      }
    }

    return element;
  }

  @Override
  public void analyzeExternalChangesAndUpdateIfNeed(@NotNull Collection<Pair<VirtualFile, StateStorage>> changedFiles, @NotNull Set<String> result) {
    StorageData oldData = myLoadedData;
    StorageData newData = getStorageData(true);
    if (oldData == null) {
      result.addAll(newData.getComponentNames());
    }
    else {
      Set<String> changedComponentNames = oldData.getChangedComponentNames(newData, myPathMacroSubstitutor);
      if (changedComponentNames != null) {
        result.addAll(changedComponentNames);
      }
    }
  }

  protected abstract class MySaveSession implements SaveSession, ExternalizationSession {
    private final StorageData myOriginalStorageData;
    private StorageData myCopiedStorageData;

    private final Map<String, Element> myNewLiveStates = new THashMap<String, Element>();

    public MySaveSession(@NotNull StorageData storageData) {
      myOriginalStorageData = storageData;
    }

    @Override
    public final void setState(@NotNull Object component, @NotNull String componentName, @NotNull Object state, @Nullable Storage storageSpec) {
      Element element;
      try {
        element = DefaultStateSerializer.serializeState(state, storageSpec);
      }
      catch (WriteExternalException e) {
        LOG.debug(e);
        return;
      }
      catch (Throwable e) {
        LOG.info("Unable to serialize component state!", e);
        return;
      }

      if (myCopiedStorageData == null) {
        myCopiedStorageData = StorageData.setStateAndCloneIfNeed(componentName, element, myOriginalStorageData, myNewLiveStates);
        if (myCopiedStorageData != null) {
          myLocalVersionProvider.changeVersion(componentName, System.currentTimeMillis());
        }
      }
      else if (myCopiedStorageData.setState(componentName, element, myNewLiveStates) != null) {
        myLocalVersionProvider.changeVersion(componentName, System.currentTimeMillis());
      }
    }

    @Override
    public final void save() {
      if (myBlockSavingTheContent) {
        return;
      }

      try {
        doSave(getElement(myCopiedStorageData, isCollapsePathsOnSave(), myNewLiveStates));
      }
      catch (IOException e) {
        throw new StateStorageException(e);
      }
    }

    // only because default project store hack
    protected boolean isCollapsePathsOnSave() {
      return true;
    }

    protected abstract void doSave(@Nullable Element element) throws IOException;

    protected void saveForProvider(@Nullable BufferExposingByteArrayOutputStream content, @Nullable Element element) throws IOException {
      if (!myStreamProvider.isApplicable(myFileSpec, myRoamingType)) {
        return;
      }

      if (element == null) {
        myStreamProvider.delete(myFileSpec, myRoamingType);
        return;
      }

      // skip the whole document if some component has disabled roaming type
      // you must not store components with different roaming types in one document
      // one exclusion: workspace file (you don't have choice in this case)
      // for example, it is important for ICS ProjectId - we cannot keep project in another place,
      // but this project id must not be shared
      if (myFileSpec.equals(StoragePathMacros.WORKSPACE_FILE)) {
        Element copiedElement = JDOMUtil.cloneElement(element, DISABLED_ROAMING_ELEMENT_FILTER);
        if (copiedElement != null) {
          doSaveForProvider(copiedElement, DISABLED_ROAMING_ELEMENT_FILTER.myRoamingType, content);
        }
      }
      else {
        doSaveForProvider(element, myRoamingType, content);
      }
    }

    private void doSaveForProvider(@NotNull Element element, @NotNull RoamingType roamingType, @Nullable BufferExposingByteArrayOutputStream content) throws IOException {
      if (content == null) {
        StorageUtil.doSendContent(myStreamProvider, myFileSpec, element, roamingType, true);
      }
      else {
        myStreamProvider.saveContent(myFileSpec, content.getInternalBuffer(), content.size(), myRoamingType, true);
      }

      if (myStreamProvider.isVersioningRequired()) {
        TObjectLongHashMap<String> versions = loadVersions(element.getChildren(StorageData.COMPONENT));
        if (!versions.isEmpty()) {
          Element versionDoc = StateStorageManagerImpl.createComponentVersionsXml(versions);
          StorageUtil.doSendContent(myStreamProvider, myFileSpec + VERSION_FILE_SUFFIX, versionDoc, roamingType, true);
        }
      }
    }
  }

  private TObjectLongHashMap<String> loadVersions(List<Element> elements) {
    TObjectLongHashMap<String> result = new TObjectLongHashMap<String>();
    for (Element component : elements) {
      String name = component.getAttributeValue(ATTR_NAME);
      if (name != null) {
        long version = myLocalVersionProvider.getVersion(name);
        if (version > 0) {
          result.put(name, version);
        }
      }
    }
    return result;
  }

  @Override
  public void dispose() {
    myDisposed = true;
  }

  @TestOnly
  public void resetData() {
    myLoadedData = null;
  }

  private void filterOutOfDate(@NotNull Element element) {
    if (myRemoteVersionProvider == null) {
      return;
    }

    Iterator<Element> iterator = element.getContent(new ElementFilter(StorageData.COMPONENT)).iterator();
    while (iterator.hasNext()) {
      String name = iterator.next().getAttributeValue(StorageData.NAME);
      long remoteVersion = myRemoteVersionProvider.getVersion(name);
      if (remoteVersion <= myLocalVersionProvider.getVersion(name)) {
        iterator.remove();
      }
      else {
        myLocalVersionProvider.changeVersion(name, remoteVersion);
      }
    }
  }

  public void resetProviderCache() {
    if (myRemoteVersionProvider != null) {
      myRemoteVersionProvider.myProviderVersions = null;
    }
  }

  private final class RemoteComponentVersionProvider implements ComponentVersionProvider {
    private TObjectLongHashMap<String> myProviderVersions;

    @Override
    public long getVersion(String name) {
      if (myProviderVersions == null) {
        loadProviderVersions();
      }
      return myProviderVersions == null ? -1 : myProviderVersions.get(name);
    }

    @Override
    public void changeVersion(String name, long version) {
      if (myProviderVersions == null) {
        loadProviderVersions();
      }
      if (myProviderVersions != null) {
        myProviderVersions.put(name, version);
      }
    }

    private void loadProviderVersions() {
      assert myStreamProvider != null;
      if (!myStreamProvider.isEnabled()) {
        return;
      }

      myProviderVersions = new TObjectLongHashMap<String>();
      for (RoamingType type : RoamingType.values()) {
        try {
          Document doc = StorageUtil.loadDocument(myStreamProvider.loadContent(myFileSpec + VERSION_FILE_SUFFIX, type));
          if (doc != null) {
            StateStorageManagerImpl.loadComponentVersions(myProviderVersions, doc);
          }
        }
        catch (IOException e) {
          LOG.debug(e);
        }
      }
    }
  }

  private static class RoamingElementFilter extends ElementFilter {
    final RoamingType myRoamingType;

    public RoamingElementFilter(RoamingType roamingType) {
      super(StorageData.COMPONENT);

      myRoamingType = roamingType;
    }

    @Override
    public boolean matches(Object obj) {
      return super.matches(obj) && ComponentRoamingManager.getInstance().getRoamingType(((Element)obj).getAttributeValue(StorageData.NAME)) == myRoamingType;
    }
  }
}
