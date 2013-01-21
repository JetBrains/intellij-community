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

package org.jetbrains.plugins.groovy.compiler;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.compiler.options.ExcludedEntriesConfiguration;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.jps.incremental.groovy.JpsGroovySettings;

/**
 * @author peter
 */
@State(name = "GroovyCompilerConfiguration", storages = @Storage( file = StoragePathMacros.WORKSPACE_FILE))
public class GroovyCompilerWorkspaceConfiguration implements PersistentStateComponent<JpsGroovySettings>, Disposable {
  String myHeapSize = JpsGroovySettings.DEFAULT_HEAP_SIZE;
  boolean myInvokeDynamic = JpsGroovySettings.DEFAULT_INVOKE_DYNAMIC;
  boolean transformsOk = JpsGroovySettings.DEFAULT_TRANSFORMS_OK;
  final ExcludedEntriesConfiguration myExcludeFromStubGeneration = new ExcludedEntriesConfiguration();

  public JpsGroovySettings getState() {
    final JpsGroovySettings bean = new JpsGroovySettings();
    bean.heapSize = myHeapSize;
    bean.invokeDynamic = myInvokeDynamic;
    bean.transformsOk = transformsOk;
    myExcludeFromStubGeneration.writeExternal(bean.excludes);
    return bean;
  }

  public void loadState(JpsGroovySettings state) {
    myHeapSize = state.heapSize;
    myInvokeDynamic = state.invokeDynamic;
    transformsOk = state.transformsOk;

    myExcludeFromStubGeneration.readExternal(state.excludes);
  }

  public void dispose() {
    Disposer.dispose(myExcludeFromStubGeneration);
  }

}
