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
package com.intellij.openapi.components.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.StateStorageException;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.components.impl.stores.ComponentRoamingManager;
import com.intellij.openapi.components.impl.stores.ComponentVersionProvider;
import com.intellij.openapi.components.impl.stores.XmlElementStorage;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.LightPlatformLangTestCase;
import com.intellij.util.io.fs.IFile;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.util.JDOMBuilder.*;

/**
 * @author mike
 */
public class XmlElementStorageTest extends LightPlatformLangTestCase {
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
        new MyXmlElementStorage(document(tag("root", tag("component", attr("name", "test"), tag("foo")))), myParentDisposable);
    Element state = storage.getState(this, "test", Element.class, null);
    assertNotNull(state);
    assertEquals("component", state.getName());
    assertNotNull(state.getChild("foo"));
  }

  public void testGetStateNotSucceeded() throws Exception {
    MyXmlElementStorage storage = new MyXmlElementStorage(document(tag("root")), myParentDisposable);
    Element state = storage.getState(this, "test", Element.class, null);
    assertNull(state);
  }

  public void testSetStateOverridesOldState() throws Exception {
    MyXmlElementStorage storage =
        new MyXmlElementStorage(document(tag("root", tag("component", attr("name", "test"), tag("foo")))), myParentDisposable);
    Element newState = tag("component", attr("name", "test"), tag("bar"));
    StateStorage.ExternalizationSession externalizationSession = storage.startExternalization();
    externalizationSession.setState(this, "test", newState, null);
    storage.startSave(externalizationSession).save();
    assertNotNull(storage.mySavedDocument);
    assertNotNull(storage.mySavedDocument.getRootElement().getChild("component").getChild("bar"));
    assertNull(storage.mySavedDocument.getRootElement().getChild("component").getChild("foo"));
  }


  private class MyXmlElementStorage extends XmlElementStorage {
    private final Document myDocument;
    private Document mySavedDocument;

    public MyXmlElementStorage(final Document document, final Disposable parentDisposable) throws StateStorageException {
      super(new MyPathMacroManager(), parentDisposable, "root", null, "", ComponentRoamingManager.getInstance(), ComponentVersionProvider.EMPTY);
      myDocument = document;
    }

    @Override
    protected Document loadDocument() throws StateStorageException {
      return myDocument;
    }

    @Override
    protected MySaveSession createSaveSession(final MyExternalizationSession externalizationSession) {
      return new MySaveSession(externalizationSession) {
        @Override
        protected void doSave() throws StateStorageException {
          mySavedDocument = getDocumentToSave().clone();
        }

        @NotNull
        @Override
        public Collection<IFile> getStorageFilesToSave() throws StateStorageException {
          return needsSave() ? getAllStorageFiles() : Collections.<IFile>emptyList();
        }

        @NotNull
        @Override
        public List<IFile> getAllStorageFiles() {
          throw new UnsupportedOperationException("Method getAllStorageFiles not implemented in " + getClass());
        }

      };
    }
  }

  private static class MyPathMacroManager implements TrackingPathMacroSubstitutor {
    @Override
    public void expandPaths(final Element element) {
    }

    @Override
    public void reset() {
    }

    @Override
    public Collection<String> getComponents(Collection<String> macros) {
      return Collections.emptyList();
    }

    @Override
    public void collapsePaths(final Element element) {
    }

    @Override
    public String expandPath(final String path) {
      throw new UnsupportedOperationException("Method expandPath not implemented in " + getClass());
    }

    @Override
    public String collapsePath(final String path) {
      throw new UnsupportedOperationException("Method collapsePath not implemented in " + getClass());
    }

    @Override
    public Collection<String> getUnknownMacros(final String componentName) {
      return Collections.emptySet();
    }

    @Override
    public void invalidateUnknownMacros(Set<String> macros) {
    }

    @Override
    public void addUnknownMacros(String componentName, Collection<String> unknownMacros) {
    }
  }
}
