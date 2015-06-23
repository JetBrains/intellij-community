/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.components.impl;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.components.impl.stores.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.xmlb.XmlSerializerUtil;
import gnu.trove.THashMap;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class ApplicationStoreTest extends LightPlatformTestCase {
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

  public void testStreamProviderSaveIfSeveralStoragesConfigured() throws Throwable {
    SeveralStoragesConfigured component = new SeveralStoragesConfigured();

    MyStreamProvider streamProvider = new MyStreamProvider();
    componentStore.getStateStorageManager().setStreamProvider(streamProvider);

    componentStore.initComponent(component, false);
    component.foo = "newValue";
    StoreUtil.save(componentStore, null);

    assertThat(
      streamProvider.data.get(RoamingType.PER_USER).get(StoragePathMacros.APP_CONFIG + "/proxy.settings.xml"),
      equalTo("<application>\n" +
              "  <component name=\"HttpConfigurable\">\n" +
              "    <option name=\"foo\" value=\"newValue\" />\n" +
              "  </component>\n" +
              "</application>"));
  }

  public void testLoadFromStreamProvider() throws Exception {
    SeveralStoragesConfigured component = new SeveralStoragesConfigured();

    MyStreamProvider streamProvider = new MyStreamProvider();
    THashMap<String, String> map = new THashMap<String, String>();
    map.put(StoragePathMacros.APP_CONFIG + "/proxy.settings.xml", "<application>\n" +
                                                                  "  <component name=\"HttpConfigurable\">\n" +
                                                                  "    <option name=\"foo\" value=\"newValue\" />\n" +
                                                                  "  </component>\n" +
                                                                  "</application>");
    streamProvider.data.put(RoamingType.PER_USER, map);

    componentStore.getStateStorageManager().setStreamProvider(streamProvider);
    componentStore.initComponent(component, false);
    assertThat(component.foo, equalTo("newValue"));
  }

  public void testRemoveDeprecatedStorageOnWrite() throws Exception {
    doRemoveDeprecatedStorageOnWrite(new SeveralStoragesConfigured());
  }

  public void testRemoveDeprecatedStorageOnWrite2() throws Exception {
    doRemoveDeprecatedStorageOnWrite(new ActualStorageLast());
  }

  private void doRemoveDeprecatedStorageOnWrite(@NotNull Foo component) throws IOException {
    File oldFile = saveConfig("other.xml", "<application>" +
                                           "  <component name=\"HttpConfigurable\">\n" +
                                           "    <option name=\"foo\" value=\"old\" />\n" +
                                           "  </component>\n" +
                                           "</application>");

    saveConfig("proxy.settings.xml", "<application>\n" +
                                     "  <component name=\"HttpConfigurable\">\n" +
                                     "    <option name=\"foo\" value=\"new\" />\n" +
                                     "  </component>\n" +
                                     "</application>");

    componentStore.initComponent(component, false);
    assertThat(component.foo, equalTo("new"));

    component.foo = "new2";
    StoreUtil.save(componentStore, null);

    assertThat(oldFile.exists(), equalTo(false));
  }

  @NotNull
  private File saveConfig(@NotNull String fileName, @Language("XML") String data) throws IOException {
    File file = new File(testAppConfig, fileName);
    FileUtil.writeToFile(file, data);
    return file;
  }

  private static class MyStreamProvider extends StreamProvider {
    public final Map<RoamingType, Map<String, String>> data = new THashMap<RoamingType, Map<String, String>>();

    @Override
    public void saveContent(@NotNull String fileSpec,
                            @NotNull byte[] content,
                            int size,
                            @NotNull RoamingType roamingType) {
      getMap(roamingType).put(fileSpec, new String(content, 0, size, CharsetToolkit.UTF8_CHARSET));
    }

    private Map<String, String> getMap(@NotNull RoamingType roamingType) {
      Map<String, String> map = data.get(roamingType);
      if (map == null) {
        map = new THashMap<String, String>();
        data.put(roamingType, map);
      }
      return map;
    }

    @Nullable
    @Override
    public InputStream loadContent(@NotNull String fileSpec, @NotNull RoamingType roamingType) throws IOException {
      String data = getMap(roamingType).get(fileSpec);
      return data == null ? null : new ByteArrayInputStream(data.getBytes(CharsetToolkit.UTF8_CHARSET));
    }

    @Override
    public void delete(@NotNull String fileSpec, @NotNull RoamingType roamingType) {
      Map<String, String> map = data.get(roamingType);
      if (map != null) {
        map.remove(fileSpec);
      }
    }
  }

  static class MyComponentStore extends ComponentStoreImpl implements Disposable {
    private final StateStorageManager stateStorageManager;

    MyComponentStore(@NotNull final String testAppConfigPath) {
      TrackingPathMacroSubstitutor macroSubstitutor = new ApplicationPathMacroManager().createTrackingSubstitutor();
      stateStorageManager = new StateStorageManagerImpl(macroSubstitutor, "application", this, ApplicationManager.getApplication().getPicoContainer()) {
        @NotNull
        @Override
        protected StorageData createStorageData(@NotNull String fileSpec, @NotNull String filePath) {
          return new StorageData("application");
        }

        @Nullable
        @Override
        protected String getOldStorageSpec(@NotNull Object component, @NotNull String componentName, @NotNull StateStorageOperation operation) {
          return null;
        }

        @Override
        protected TrackingPathMacroSubstitutor getMacroSubstitutor(@NotNull final String fileSpec) {
          if (fileSpec.equals(StoragePathMacros.APP_CONFIG + "/" + PathMacrosImpl.EXT_FILE_NAME + ".xml")) {
            return null;
          }
          return super.getMacroSubstitutor(fileSpec);
        }
      };

      stateStorageManager.addMacro(StoragePathMacros.APP_CONFIG, testAppConfigPath);
    }

    @Override
    public void load() {
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
    protected PathMacroManager getPathMacroManagerForDefaults() {
      return null;
    }

    @NotNull
    @Override
    protected MessageBus getMessageBus() {
      return ApplicationManager.getApplication().getMessageBus();
    }
  }

  abstract static class Foo {
    public String foo = "defaultValue";
  }

  @State(
    name = "HttpConfigurable",
    storages = {
      @Storage(file = StoragePathMacros.APP_CONFIG + "/proxy.settings.xml"),
      @Storage(file = StoragePathMacros.APP_CONFIG + "/other.xml", deprecated = true)
    }
  )
  static class SeveralStoragesConfigured extends Foo implements PersistentStateComponent<SeveralStoragesConfigured> {
    @Nullable
    @Override
    public SeveralStoragesConfigured getState() {
      return this;
    }

    @Override
    public void loadState(SeveralStoragesConfigured state) {
      XmlSerializerUtil.copyBean(state, this);
    }
  }

  @State(
    name = "HttpConfigurable",
    storages = {
      @Storage(file = StoragePathMacros.APP_CONFIG + "/other.xml", deprecated = true),
      @Storage(file = StoragePathMacros.APP_CONFIG + "/proxy.settings.xml")
    }
  )
  static class ActualStorageLast extends Foo implements PersistentStateComponent<ActualStorageLast> {
    @Nullable
    @Override
    public ActualStorageLast getState() {
      return this;
    }

    @Override
    public void loadState(ActualStorageLast state) {
      XmlSerializerUtil.copyBean(state, this);
    }
  }
}
