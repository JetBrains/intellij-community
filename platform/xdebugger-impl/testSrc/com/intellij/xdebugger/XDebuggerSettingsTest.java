/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.xdebugger;

import com.intellij.configurationStore.XmlSerializer;
import com.intellij.testFramework.PlatformLiteFixture;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl;
import com.intellij.xdebugger.settings.XDebuggerSettings;
import com.intellij.xdebugger.settings.XDebuggerSettingsManager;
import org.jdom.Element;

/**
 * @author nik
 */
public class XDebuggerSettingsTest extends PlatformLiteFixture {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initApplication();
    registerExtensionPoint(XDebuggerSettings.EXTENSION_POINT, XDebuggerSettings.class);
    registerExtension(XDebuggerSettings.EXTENSION_POINT, new MyDebuggerSettings());
    getApplication().registerService(XDebuggerUtil.class, XDebuggerUtilImpl.class);
    getApplication().registerService(XDebuggerSettingsManager.class, XDebuggerSettingManagerImpl.class);
  }

  public void testSerialize() {
    XDebuggerSettingManagerImpl settingsManager = XDebuggerSettingManagerImpl.getInstanceImpl();

    MyDebuggerSettings settings = MyDebuggerSettings.getInstance();
    assertNotNull(settings);
    settings.myOption = "239";

    Element element = XmlSerializer.serialize(settingsManager.getState());
    //System.out.println(JDOMUtil.writeElement(element, SystemProperties.getLineSeparator()));

    settings.myOption = "42";
    assertSame(settings, MyDebuggerSettings.getInstance());

    settingsManager.loadState(com.intellij.configurationStore.XmlSerializer.deserialize(element, XDebuggerSettingManagerImpl.SettingsState.class));
    assertSame(settings, MyDebuggerSettings.getInstance());
    assertEquals("239", settings.myOption);
  }

  public static class MyDebuggerSettings extends XDebuggerSettings<MyDebuggerSettings> {
    @Attribute("option")
    public String myOption;

    public MyDebuggerSettings() {
      super("test");
    }

    public static MyDebuggerSettings getInstance() {
      return getInstance(MyDebuggerSettings.class);
    }

    @Override
    public MyDebuggerSettings getState() {
      return this;
    }

    @Override
    public void loadState(final MyDebuggerSettings state) {
      myOption = state.myOption;
    }
  }
}
