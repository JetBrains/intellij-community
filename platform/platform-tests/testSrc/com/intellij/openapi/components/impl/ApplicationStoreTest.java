package com.intellij.openapi.components.impl;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.components.*;
import com.intellij.openapi.components.impl.stores.IApplicationStore;
import com.intellij.openapi.components.impl.stores.StoreUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.TestLoggerFactory;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class ApplicationStoreTest {
  private static File testAppConfig;

  static {
    Logger.setFactory(TestLoggerFactory.class);
    PlatformTestCase.initPlatformLangPrefix();
    System.setProperty("idea.filewatcher.disabled", "true");
  }

  @BeforeClass
  public static void createApplication() throws IOException {
    ApplicationManagerEx.createApplication(true, true, true, true, ApplicationManagerEx.IDEA_APPLICATION, null);

    final String testAppConfigPath = System.getProperty("test.app.config.path");
    if (testAppConfigPath == null) {
      testAppConfig = FileUtil.createTempDirectory("testAppSettings", null);
    }
    else {
      testAppConfig = new File(FileUtil.expandUserHome(testAppConfigPath));
    }
    FileUtil.delete(testAppConfig);

    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        PluginManagerCore.getPlugins();
        final ApplicationImpl app = (ApplicationImpl)ApplicationManagerEx.getApplicationEx();
        new WriteAction() {
          @Override
          protected void run(@NotNull Result result) throws Throwable {
            app.load(testAppConfig.getAbsolutePath(), testAppConfig.getAbsolutePath() + "/options");
          }
        }.execute();
      }
    });
  }

  @AfterClass
  public static void disposeApplication() {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        new WriteAction() {
          @Override
          protected void run(@NotNull Result result) throws Throwable {
            Application application = ApplicationManager.getApplication();
            if (application != null) {
              Disposer.dispose(application);
            }
          }
        }.execute();
      }
    });
  }

  @After
  public void tearDown() throws Exception {
    FileUtil.delete(testAppConfig);
  }

  @Test
  public void testStreamProviderSaveIfSeveralStoragesConfigured() throws Exception {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          IApplicationStore applicationStore = getStore();
          StoreUtil.doSave(applicationStore);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  @NotNull
  private static IApplicationStore getStore() {
    return ((ApplicationImpl)ApplicationManager.getApplication()).getStateStore();
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
