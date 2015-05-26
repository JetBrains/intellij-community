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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.components.impl.stores.StorageData;
import com.intellij.openapi.components.impl.stores.XmlElementStorage;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import static com.intellij.openapi.util.JDOMBuilder.attr;
import static com.intellij.openapi.util.JDOMBuilder.tag;

/**
 * @author mike
 */
public class XmlElementStorageTest extends LightPlatformTestCase {
  private Disposable myParentDisposable;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myParentDisposable = Disposer.newDisposable();
  }

  @Override
  public void tearDown() throws Exception {
    Disposer.dispose(myParentDisposable);
    super.tearDown();
  }

  public void testGetStateSucceeded() throws Exception {
    MyXmlElementStorage storage =
      new MyXmlElementStorage(tag("root", tag("component", attr("name", "test"), tag("foo"))));
    Element state = storage.getState(this, "test", Element.class, null);
    assertNotNull(state);
    assertEquals("component", state.getName());
    assertNotNull(state.getChild("foo"));
  }

  public void testGetStateNotSucceeded() throws Exception {
    MyXmlElementStorage storage = new MyXmlElementStorage(tag("root"));
    Element state = storage.getState(this, "test", Element.class, null);
    assertNull(state);
  }

  public void testSetStateOverridesOldState() throws Exception {
    MyXmlElementStorage storage =
      new MyXmlElementStorage(tag("root", tag("component", attr("name", "test"), tag("foo"))));
    Element newState = tag("component", attr("name", "test"), tag("bar"));
    StateStorage.ExternalizationSession externalizationSession = storage.startExternalization();
    externalizationSession.setState(this, "test", newState, null);
    externalizationSession.createSaveSession().save();
    assertNotNull(storage.mySavedElement);
    assertNotNull(storage.mySavedElement.getChild("component").getChild("bar"));
    assertNull(storage.mySavedElement.getChild("component").getChild("foo"));
  }


  private static class MyXmlElementStorage extends XmlElementStorage {
    private final Element myElement;
    private Element mySavedElement;

    public MyXmlElementStorage(Element element) {
      super("", RoamingType.PER_USER, new MyPathMacroManager(), "root", null);
      myElement = element;
    }

    @Override
    protected Element loadLocalData() {
      return myElement;
    }

    @NotNull
    @Override
    protected XmlElementStorageSaveSession createSaveSession(@NotNull StorageData storageData) {
      return new XmlElementStorageSaveSession(storageData) {
        @Override
        protected void doSave(@Nullable Element element) {
          mySavedElement = element == null ? null : element.clone();
        }
      };
    }
  }

  private static class MyPathMacroManager implements TrackingPathMacroSubstitutor {
    @Override
    public void expandPaths(@NotNull final Element element) {
    }

    @Override
    public void reset() {
    }

    @NotNull
    @Override
    public Collection<String> getComponents(@NotNull Collection<String> macros) {
      return Collections.emptyList();
    }

    @Override
    public void collapsePaths(@NotNull final Element element) {
    }

    @Override
    public String expandPath(final String path) {
      throw new UnsupportedOperationException("Method expandPath not implemented in " + getClass());
    }

    @Override
    public String collapsePath(@Nullable String path) {
      throw new UnsupportedOperationException("Method collapsePath not implemented in " + getClass());
    }

    @NotNull
    @Override
    public Collection<String> getUnknownMacros(final String componentName) {
      return Collections.emptySet();
    }

    @Override
    public void invalidateUnknownMacros(@NotNull Set<String> macros) {
    }

    @Override
    public void addUnknownMacros(@NotNull String componentName, @NotNull Collection<String> unknownMacros) {
    }
  }
}
