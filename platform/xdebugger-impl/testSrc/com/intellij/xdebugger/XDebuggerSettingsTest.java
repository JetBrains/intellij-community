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
package com.intellij.xdebugger;

import com.intellij.openapi.options.Configurable;
import com.intellij.testFramework.PlatformLiteFixture;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.settings.XDebuggerSettingsManager;
import com.intellij.xdebugger.settings.XDebuggerSettings;
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
    getApplication().registerService(XDebuggerSettingsManager.class, XDebuggerSettingsManager.class);
  }

  public void testSerialize() throws Exception {
    XDebuggerSettingsManager settingsManager = XDebuggerSettingsManager.getInstance();

    MyDebuggerSettings settings = MyDebuggerSettings.getInstance();
    assertNotNull(settings);
    settings.myOption = "239";

    Element element = XmlSerializer.serialize(settingsManager.getState());
    //System.out.println(JDOMUtil.writeElement(element, SystemProperties.getLineSeparator()));

    settings.myOption = "42";
    assertSame(settings, MyDebuggerSettings.getInstance());

    settingsManager.loadState(XmlSerializer.deserialize(element, XDebuggerSettingsManager.SettingsState.class));
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

    @Override
    @NotNull
    public Configurable createConfigurable() {
      throw new UnsupportedOperationException("'createConfigurable' not implemented in " + getClass().getName());
    }
  }
}
