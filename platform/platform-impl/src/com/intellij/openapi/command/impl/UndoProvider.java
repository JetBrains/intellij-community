/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.command.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;

public interface UndoProvider {
  ExtensionPointName<UndoProvider> EP_NAME = ExtensionPointName.create("com.intellij.undoProvider");
  ExtensionPointName<UndoProvider> PROJECT_EP_NAME = ExtensionPointName.create("com.intellij.projectUndoProvider");

  void commandStarted(Project project);
  void commandFinished(Project project);
}