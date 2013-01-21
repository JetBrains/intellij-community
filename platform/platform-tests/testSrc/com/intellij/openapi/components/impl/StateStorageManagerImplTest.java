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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.StateStorageException;
import com.intellij.openapi.components.StateStorageOperation;
import com.intellij.openapi.components.impl.stores.StateStorageManagerImpl;
import com.intellij.openapi.components.impl.stores.StorageData;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.LightPlatformLangTestCase;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * @author mike
 */
public class StateStorageManagerImplTest extends LightPlatformLangTestCase {
  private StateStorageManagerImpl myStateStorageManager;

  @Override
  public final void setUp() throws Exception {
    super.setUp();
    myStateStorageManager = new StateStorageManagerImpl(null, "foo", null, ApplicationManager.getApplication().getPicoContainer()) {
      @Override
      protected StorageData createStorageData(String storageSpec) {
        throw new UnsupportedOperationException("Method createStorageData not implemented in " + getClass());
      }

      @Override
      protected String getOldStorageSpec(Object component, String componentName, StateStorageOperation operation) throws StateStorageException {
        throw new UnsupportedOperationException("Method getOldStorageSpec not implemented in " + getClass());
      }

      @Override
      protected String getVersionsFilePath() {
        return null;
      }
    };
    myStateStorageManager.addMacro("MACRO1", "/temp/m1");
  }

  @Override
  public void tearDown() throws Exception {
    Disposer.dispose(myStateStorageManager);
    super.tearDown();
  }

  public void testCreateFileStateStorageMacroSubstituted() {
    StateStorage data = myStateStorageManager.getFileStateStorage("$MACRO1$/test.xml");
    assertThat(data, is(notNullValue()));
  }

  public void testCreateStateStorageAssertionThrownWhenUnknownMacro() {
    try {
      myStateStorageManager.getFileStateStorage("$UNKNOWN_MACRO$/test.xml");
      fail("Exception expected");
    }
    catch (IllegalArgumentException e) {
      assertEquals("Unknown macro: $UNKNOWN_MACRO$ in storage spec: $UNKNOWN_MACRO$/test.xml", e.getMessage());
    }
  }

  public void testCreateFileStateStorageMacroSubstitutedWhenExpansionHas$() {
    myStateStorageManager.addMacro("DOLLAR_MACRO", "/temp/d$");
    StateStorage data = myStateStorageManager.getFileStateStorage("$DOLLAR_MACRO$/test.xml");
    assertThat(data, is(notNullValue()));
  }
}
