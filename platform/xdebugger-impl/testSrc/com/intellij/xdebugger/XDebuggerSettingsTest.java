// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger;

import com.intellij.configurationStore.XmlSerializer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.TestDisposable;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl;
import com.intellij.xdebugger.settings.XDebuggerSettings;
import com.intellij.xdebugger.settings.XDebuggerSettingsManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.ServiceContainerUtil.registerOrReplaceServiceInstance;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@TestApplication
public class XDebuggerSettingsTest {

  @TestDisposable
  private Disposable testDisposable;

  @BeforeEach
  public void setUp() {
    var application = ApplicationManager.getApplication();
    ExtensionTestUtil.addExtensions(XDebuggerSettingManagerImpl.getSettingsEP(), List.of(new MyDebuggerSettings()), testDisposable);
    registerOrReplaceServiceInstance(application, XDebuggerUtil.class, new XDebuggerUtilImpl(), testDisposable);
    registerOrReplaceServiceInstance(application, XDebuggerSettingsManager.class, new XDebuggerSettingManagerImpl(), testDisposable);
  }

  @Test
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
