package com.intellij.openapi.components.impl;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.components.impl.stores.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.LightPlatformLangTestCase;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public class ApplicationStoreTest extends LightPlatformLangTestCase {
  private File testAppConfig;
  private MyComponentStore componentStore;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    String testAppConfigPath = System.getProperty("test.app.config.path");
    if (testAppConfigPath == null) {
      testAppConfig = FileUtil.createTempDirectory("testAppSettings", null);
    }
    else {
      testAppConfig = new File(FileUtil.expandUserHome(testAppConfigPath));
    }
    FileUtil.delete(testAppConfig);

    componentStore = new MyComponentStore(testAppConfig.getAbsolutePath());
  }

  @Override
  public void tearDown() throws Exception {
    try {
      Disposer.dispose(componentStore);
      componentStore = null;
    }
    finally {
      try {
        super.tearDown();
      }
      finally {
        FileUtil.delete(testAppConfig);
      }
    }
  }

  public void testStreamProviderSaveIfSeveralStoragesConfigured() throws Exception {
    SeveralStoragesConfigured component = new SeveralStoragesConfigured();
    componentStore.initComponent(component, false);
    StoreUtil.doSave(componentStore);
  }

  class MyComponentStore extends ComponentStoreImpl implements Disposable {
    private final StateStorageManager stateStorageManager;

    MyComponentStore(@NotNull final String testAppConfigPath) {
      TrackingPathMacroSubstitutor macroSubstitutor = new ApplicationPathMacroManager().createTrackingSubstitutor();
      stateStorageManager = new StateStorageManagerImpl(macroSubstitutor, "application", this, ApplicationManager.getApplication().getPicoContainer()) {
        @Override
        protected StorageData createStorageData(String storageSpec) {
          return new FileBasedStorage.FileStorageData("application");
        }

        @Nullable
        @Override
        protected String getOldStorageSpec(Object component, final String componentName, final StateStorageOperation operation) {
          return null;
        }

        @Override
        protected String getVersionsFilePath() {
          return testAppConfigPath + "/options/appComponentVersions.xml";
        }

        @Override
        protected TrackingPathMacroSubstitutor getMacroSubstitutor(@NotNull final String fileSpec) {
          if (fileSpec.equals(StoragePathMacros.APP_CONFIG + "/" + PathMacrosImpl.EXT_FILE_NAME + ".xml")) {
            return null;
          }
          return super.getMacroSubstitutor(fileSpec);
        }
      };

      stateStorageManager.addMacro(StoragePathMacros.getMacroName(StoragePathMacros.APP_CONFIG), testAppConfigPath);
    }

    @Override
    public void load() throws IOException, StateStorageException {
    }

    @NotNull
    @Override
    public StateStorageManager getStateStorageManager() {
      return stateStorageManager;
    }

    @Override
    public void dispose() {
    }

    @Nullable
    @Override
    protected StateStorage getDefaultsStorage() {
      return null;
    }
  }

  static class SeveralStoragesConfiguredStorageChooser implements StateStorageChooser<SeveralStoragesConfigured> {
    @Override
    public Storage[] selectStorages(Storage[] storages, SeveralStoragesConfigured component, StateStorageOperation operation) {
      if (operation == StateStorageOperation.WRITE) {
        for (Storage storage : storages) {
          if (storage.file().equals(StoragePathMacros.APP_CONFIG + "/proxy.settings.xml")) {
            return new Storage[]{storage};
          }
        }
      }
      return storages;
    }
  }

  @State(
    name = "HttpConfigurable",
    storages = {
      // we use two storages due to backward compatibility, see http://crucible.labs.intellij.net/cru/CR-IC-5142
      @Storage(file = StoragePathMacros.APP_CONFIG + "/other.xml"),
      @Storage(file = StoragePathMacros.APP_CONFIG + "/proxy.settings.xml")
    },
    storageChooser = SeveralStoragesConfiguredStorageChooser.class
  )
  static class SeveralStoragesConfigured implements PersistentStateComponent<SeveralStoragesConfigured> {
    public String foo = "defaultValue";

    @Nullable
    @Override
    public SeveralStoragesConfigured getState() {
      foo = "newValue";
      return this;
    }

    @Override
    public void loadState(SeveralStoragesConfigured state) {
      XmlSerializerUtil.copyBean(state, this);
    }
  }
}
