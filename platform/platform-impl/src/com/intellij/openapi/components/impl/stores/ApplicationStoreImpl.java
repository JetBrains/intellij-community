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

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.StateStorageOperation;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

class ApplicationStoreImpl extends ComponentStoreImpl implements IApplicationStore {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.ApplicationStoreImpl");

  @NonNls private static final String XML_EXTENSION = ".xml";

  private final ApplicationImpl myApplication;
  private final StateStorageManager myStateStorageManager;
  @NonNls private static final String APP_CONFIG_STORAGE_MACRO = "APP_CONFIG";
  @NonNls private static final String OPTIONS_MACRO = "OPTIONS";
  @NonNls private static final String CONFIG_MACRO = "ROOT_CONFIG";
  private final DefaultsStateStorage myDefaultsStateStorage;
  @NonNls private static final String ROOT_ELEMENT_NAME = "application";


  @SuppressWarnings({"UnusedDeclaration"}) //picocontainer
  public ApplicationStoreImpl(final ApplicationImpl application, PathMacroManager pathMacroManager) {
    myApplication = application;
    myStateStorageManager = new StateStorageManagerImpl(pathMacroManager.createTrackingSubstitutor(), ROOT_ELEMENT_NAME, application, application.getPicoContainer()) {
      protected XmlElementStorage.StorageData createStorageData(String storageSpec) {
        return new FileBasedStorage.FileStorageData(ROOT_ELEMENT_NAME);
      }

      protected String getOldStorageSpec(Object component, final String componentName, final StateStorageOperation operation) {
        final String fileName;

        if (component instanceof NamedJDOMExternalizable) {
          fileName = "$" + APP_CONFIG_STORAGE_MACRO + "$/" + ((NamedJDOMExternalizable)component).getExternalFileName() + XML_EXTENSION;
        }
        else {
          fileName = DEFAULT_STORAGE_SPEC;
        }

        return fileName;
      }

      protected String getVersionsFilePath() {
        return PathManager.getConfigPath() + "/options/" + "appComponentVersions.xml";
      }

      protected TrackingPathMacroSubstitutor getMacroSubstitutor(@NotNull final String fileSpec) {
        if (fileSpec.equals("$" + APP_CONFIG_STORAGE_MACRO + "$/" + PathMacrosImpl.EXT_FILE_NAME + XML_EXTENSION)) return null;
        return super.getMacroSubstitutor(fileSpec);
      }
    };
    myDefaultsStateStorage = new DefaultsStateStorage(null);
  }

  public void load() throws IOException {
    long start = System.currentTimeMillis();
//    ProfilingUtil.startCPUProfiling();
    myApplication.initComponents();
//    ProfilingUtil.captureCPUSnapshot();
    LOG.info(myApplication.getComponentConfigurations().length + " application components initialized in " + (System.currentTimeMillis() - start) + " ms");
  }

  public void setOptionsPath(final String path) {
    myStateStorageManager.addMacro(APP_CONFIG_STORAGE_MACRO, path);
    myStateStorageManager.addMacro(OPTIONS_MACRO, path);
  }

  public void setConfigPath(final String configPath) {
    myStateStorageManager.addMacro(CONFIG_MACRO, configPath);
  }

  public boolean reload(final Set<Pair<VirtualFile, StateStorage>> changedFiles, final Collection<String> notReloadableComponents) throws StateStorage.StateStorageException, IOException {

    final SaveSession saveSession = startSave();
    final Set<String> componentNames = saveSession.analyzeExternalChanges(changedFiles);

    try {
      if (componentNames == null) return false;

      for (Pair<VirtualFile, StateStorage> pair : changedFiles) {
        if (pair.second == null) return false;
      }

      for (String name : componentNames) {
        if (!isReloadPossible(Collections.singleton(name))) {
          notReloadableComponents.add(name);

        }
      }


      if (!componentNames.isEmpty()) {
        StorageUtil.logStateDiffInfo(changedFiles, componentNames);
      }
      
      if (!isReloadPossible(componentNames)) {
        return false;
      }
    }
    finally {
      finishSave(saveSession);
    }

    if (!componentNames.isEmpty()) {
      myApplication.getMessageBus().syncPublisher(BatchUpdateListener.TOPIC).onBatchUpdateStarted();

      try {
        doReload(changedFiles, componentNames);
        reinitComponents(componentNames, false);
      }
      finally {
        myApplication.getMessageBus().syncPublisher(BatchUpdateListener.TOPIC).onBatchUpdateFinished();
      }
    }


    return true;

  }

  public static final String DEFAULT_STORAGE_SPEC = "$" + APP_CONFIG_STORAGE_MACRO + "$/" + PathManager.DEFAULT_OPTIONS_FILE_NAME + XML_EXTENSION;

  public StateStorageManager getStateStorageManager() {
    return myStateStorageManager;
  }

  @Override
  protected StateStorage getDefaultsStorage() {
    return myDefaultsStateStorage;
  }

}
