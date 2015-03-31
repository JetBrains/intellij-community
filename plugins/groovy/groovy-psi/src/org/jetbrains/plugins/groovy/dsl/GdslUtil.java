/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Contract;

public class GdslUtil {
  public static final Key<GroovyClassDescriptor> INITIAL_CONTEXT = Key.create("gdsl.initialContext");

  public static final Condition<VirtualFile> GDSL_FILTER = new Condition<VirtualFile>() {
    @Override
    @Contract("null -> false")
    public boolean value(VirtualFile file) {
      return file != null && !file.isDirectory() && StringUtil.endsWith(file.getNameSequence(), ".gdsl");
    }
  };
  static volatile boolean ourGdslStopped = false;

  static void stopGdsl() {
    ourGdslStopped = true;
  }
}
