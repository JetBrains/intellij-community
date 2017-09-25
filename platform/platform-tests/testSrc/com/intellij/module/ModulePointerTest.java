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
package com.intellij.module;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.*;
import com.intellij.testFramework.PlatformTestCase;
import org.jetbrains.annotations.NotNull;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author nik
 */
public class ModulePointerTest extends PlatformTestCase {
  public void testCreateByName() {
    final ModulePointer pointer = getPointerManager().create("m");
    assertSame(pointer, getPointerManager().create("m"));
    assertNull(pointer.getModule());
    assertEquals("m", pointer.getModuleName());

    final Module module = addModule("m");

    assertSame(module, pointer.getModule());
    assertEquals("m", pointer.getModuleName());
  }

  public void testCreateByModule() {
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

  public void testDisposePointerFromUncommittedModifiableModel() {
    ModulePointerManager pointerManager = getPointerManager();
    final ModulePointer pointer = pointerManager.create("xxx");

    final ModifiableModuleModel modifiableModel = getModuleManager().getModifiableModel();
    final Module module = modifiableModel.newModule(myProject.getBaseDir().getPath() + "/xxx.iml", EmptyModuleType.getInstance().getId());
    assertThat(pointerManager.create(module)).isSameAs(pointer);
    assertThat(pointerManager.create("xxx")).isSameAs(pointer);

    assertThat(pointer.getModule()).isSameAs(module);
    assertThat(pointer.getModuleName()).isEqualTo("xxx");

    ApplicationManager.getApplication().runWriteAction(() -> modifiableModel.dispose());

    assertThat(pointer.getModule()).isNull();
    assertThat(pointer.getModuleName()).isEqualTo("xxx");
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
