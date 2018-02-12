// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger;

import com.intellij.configurationStore.XmlSerializer;
import com.intellij.testFramework.PlatformLiteFixture;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl;
import com.intellij.xdebugger.settings.XDebuggerSettings;
import com.intellij.xdebugger.settings.XDebuggerSettingsManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

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
    public void loadState(@NotNull final MyDebuggerSettings state) {
      myOption = state.myOption;
    }
  }
}
