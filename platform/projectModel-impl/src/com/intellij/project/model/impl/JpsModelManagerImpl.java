/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.project.model.impl;

import com.intellij.project.model.JpsLibraryManager;
import com.intellij.project.model.JpsModelManager;
import com.intellij.project.model.JpsModuleManager;
import com.intellij.project.model.impl.library.JpsLibraryManagerImpl;
import com.intellij.project.model.impl.module.JpsModuleManagerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsEventDispatcher;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsNamedElement;
import org.jetbrains.jps.model.impl.JpsEventDispatcherBase;
import org.jetbrains.jps.model.impl.JpsModelImpl;

/**
 * @author nik
 */
public class JpsModelManagerImpl extends JpsModelManager {
  private final JpsModel myModel;
  private JpsModel myModifiableModel;
  private final JpsModuleManagerImpl myModuleManager;
  private final JpsLibraryManagerImpl myLibraryManager;

  public JpsModelManagerImpl() {
    myModel = new JpsModelImpl(new MyJpsEventDispatcher());
    myModuleManager = new JpsModuleManagerImpl();
    myLibraryManager = new JpsLibraryManagerImpl();
  }

  @NotNull
  @Override
  public JpsModuleManager getModuleManager() {
    return myModuleManager;
  }

  @Override
  @NotNull
  public JpsLibraryManager getLibraryManager() {
    return myLibraryManager;
  }

  @Override
  public void startModification(JpsEventDispatcher eventDispatcher) {
    myModifiableModel = myModel.createModifiableModel(eventDispatcher);
  }

  @Override
  public void commitChanges() {
    myModifiableModel.commit();
  }

  private static class MyJpsEventDispatcher extends JpsEventDispatcherBase implements JpsEventDispatcher {
    @Override
    public void fireElementChanged(@NotNull JpsElement element) {
    }

    @Override
    public void fireElementRenamed(@NotNull JpsNamedElement element, @NotNull String oldName, @NotNull String newName) {
    }
  }
}
