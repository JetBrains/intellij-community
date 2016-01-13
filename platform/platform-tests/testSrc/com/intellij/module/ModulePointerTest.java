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
package com.intellij.module;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.*;
import com.intellij.testFramework.PlatformTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class ModulePointerTest extends PlatformTestCase {
  public void testCreateByName() throws Exception {
    final ModulePointer pointer = getPointerManager().create("m");
    assertSame(pointer, getPointerManager().create("m"));
    assertNull(pointer.getModule());
    assertEquals("m", pointer.getModuleName());

    final Module module = addModule("m");

    assertSame(module, pointer.getModule());
    assertEquals("m", pointer.getModuleName());
  }

  public void testCreateByModule() throws Exception {
    final Module module = addModule("x");
    final ModulePointer pointer = getPointerManager().create(module);
    assertSame(pointer, getPointerManager().create(module));
    assertSame(pointer, getPointerManager().create("x"));
    assertSame(module, pointer.getModule());
    assertEquals("x", pointer.getModuleName());

    ModifiableModuleModel model = getModuleManager().getModifiableModel();
    model.disposeModule(module);
    commitModel(model);

    assertNull(pointer.getModule());
    assertEquals("x", pointer.getModuleName());

    final Module newModule = addModule("x");
    assertSame(pointer, getPointerManager().create(newModule));
  }

  public void testRenameModule() throws Exception {
    final ModulePointer pointer = getPointerManager().create("abc");
    final Module module = addModule("abc");
    ModifiableModuleModel model = getModuleManager().getModifiableModel();
    model.renameModule(module, "xyz");
    commitModel(model);
    assertSame(module, pointer.getModule());
    assertEquals("xyz", pointer.getModuleName());
  }

  public void testDisposePointerFromUncommitedModifiableModel() throws Exception {
    final ModulePointer pointer = getPointerManager().create("xxx");

    final ModifiableModuleModel modifiableModel = getModuleManager().getModifiableModel();
    final Module module = modifiableModel.newModule(myProject.getBaseDir().getPath() + "/xxx.iml", EmptyModuleType.getInstance().getId());
    assertSame(pointer, getPointerManager().create(module));
    assertSame(pointer, getPointerManager().create("xxx"));

    assertSame(module, pointer.getModule());
    assertEquals("xxx", pointer.getModuleName());

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        modifiableModel.dispose();
      }
    });


    assertNull(pointer.getModule());
    assertEquals("xxx", pointer.getModuleName());
  }

  private ModuleManager getModuleManager() {
    return ModuleManager.getInstance(myProject);
  }

  private Module addModule(final String name) {
    final ModifiableModuleModel model = getModuleManager().getModifiableModel();
    final Module module = model.newModule(myProject.getBaseDir().getPath() + "/" + name + ".iml", EmptyModuleType.getInstance().getId());
    commitModel(model);
    disposeOnTearDown(new Disposable() {
      @Override
      public void dispose() {
        if (!module.isDisposed()) {
          getModuleManager().disposeModule(module);
        }
      }
    });
    return module;
  }

  private static void commitModel(final ModifiableModuleModel model) {
    new WriteAction() {
      @Override
      protected void run(@NotNull final Result result) {
        model.commit();
      }
    }.execute();
  }

  private ModulePointerManager getPointerManager() {
    return ModulePointerManager.getInstance(myProject);
  }
}
