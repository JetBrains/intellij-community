/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.vfs.*;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

class ApplicationStoreImpl extends ComponentStoreImpl implements IApplicationStore {
  private static final Logger LOG = Logger.getInstance(ApplicationStoreImpl.class);

  private static final String XML_EXTENSION = ".xml";
  private static final String DEFAULT_STORAGE_SPEC = StoragePathMacros.APP_CONFIG + "/" + PathManager.DEFAULT_OPTIONS_FILE_NAME + XML_EXTENSION;
  private static final String ROOT_ELEMENT_NAME = "application";

  private final ApplicationImpl myApplication;
  private final StateStorageManager myStateStorageManager;
  private final DefaultsStateStorage myDefaultsStateStorage;

  private String myConfigPath;

  // created from PicoContainer
  @SuppressWarnings({"UnusedDeclaration"})
  public ApplicationStoreImpl(final ApplicationImpl application, PathMacroManager pathMacroManager) {
    myApplication = application;
    myStateStorageManager = new StateStorageManagerImpl(pathMacroManager.createTrackingSubstitutor(), ROOT_ELEMENT_NAME, application, application.getPicoContainer()) {
      private boolean myConfigDirectoryRefreshed;

      @Override
      protected StorageData createStorageData(@NotNull String storageSpec) {
        return new FileBasedStorage.FileStorageData(ROOT_ELEMENT_NAME);
      }

      @Nullable
      @Override
      protected String getOldStorageSpec(@NotNull Object component, @NotNull String componentName, @NotNull StateStorageOperation operation) {
        if (component instanceof NamedJDOMExternalizable) {
          return StoragePathMacros.APP_CONFIG + "/" + ((NamedJDOMExternalizable)component).getExternalFileName() + XML_EXTENSION;
        }
        else {
          return DEFAULT_STORAGE_SPEC;
        }
      }

      @Override
      protected String getVersionsFilePath() {
        return getConfigPath() + "/options/appComponentVersions.xml";
      }

      @Override
      protected TrackingPathMacroSubstitutor getMacroSubstitutor(@NotNull final String fileSpec) {
        if (fileSpec.equals(StoragePathMacros.APP_CONFIG + "/" + PathMacrosImpl.EXT_FILE_NAME + XML_EXTENSION)) return null;
        return super.getMacroSubstitutor(fileSpec);
      }

      @Override
      protected boolean isUseXmlProlog() {
        return false;
      }

      @Override
      protected void beforeFileBasedStorageCreate() {
        if (!myConfigDirectoryRefreshed && (application.isUnitTestMode() || application.isDispatchThread())) {
          try {
            VirtualFile configDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(getConfigPath());
            if (configDir != null) {
              VfsUtilCore.visitChildrenRecursively(configDir, new VirtualFileVisitor() {
                @Override
                public boolean visitFile(@NotNull VirtualFile file) {
                  return !"componentVersions".equals(file.getName());
                }
              });
              VfsUtil.markDirtyAndRefresh(false, true, false, configDir);
            }
          }
          finally {
            myConfigDirectoryRefreshed = true;
          }
        }
      }
    };
    myDefaultsStateStorage = new DefaultsStateStorage(null);
  }

  @Override
  public void load() throws IOException {
    long t = System.currentTimeMillis();
    myApplication.init();
    t = System.currentTimeMillis() - t;
    LOG.info(myApplication.getComponentConfigurations().length + " application components initialized in " + t + " ms");
  }

  @Override
  public void setOptionsPath(@NotNull String path) {
    myStateStorageManager.addMacro(StoragePathMacros.APP_CONFIG, path);
  }

  @Override
  public void setConfigPath(@NotNull final String configPath) {
    myStateStorageManager.addMacro(StoragePathMacros.ROOT_CONFIG, configPath);
    myConfigPath = configPath;
  }

  @Override
  @NotNull
  public String getConfigPath() {
    String configPath = myConfigPath;
    if (configPath == null) {
      // unrealistic case, but we keep backward compatibility
      configPath = PathManager.getConfigPath();
    }
    return configPath;
  }

  @Override
  @NotNull
  protected MessageBus getMessageBus() {
    return myApplication.getMessageBus();
  }

  @NotNull
  @Override
  public StateStorageManager getStateStorageManager() {
    return myStateStorageManager;
  }

  @Nullable
  @Override
  protected StateStorage getDefaultsStorage() {
    return myDefaultsStateStorage;
  }
}
