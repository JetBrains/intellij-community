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
package org.jetbrains.java.decompiler.modules.renamer;

import org.jetbrains.java.decompiler.main.extern.IIdentifierRenamer;

import java.util.HashMap;

public class PoolInterceptor {

  private final IIdentifierRenamer helper;

  private final HashMap<String, String> mapOldToNewNames = new HashMap<>();

  private final HashMap<String, String> mapNewToOldNames = new HashMap<>();

  public PoolInterceptor(IIdentifierRenamer helper) {
    this.helper = helper;
  }

  public void addName(String oldName, String newName) {
    mapOldToNewNames.put(oldName, newName);
    mapNewToOldNames.put(newName, oldName);
  }

  public String getName(String oldName) {
    return mapOldToNewNames.get(oldName);
  }

  public String getOldName(String newName) {
    return mapNewToOldNames.get(newName);
  }

  public IIdentifierRenamer getHelper() {
    return helper;
  }
}
