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

package org.jetbrains.plugins.groovy.compiler;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.compiler.options.ExcludedEntriesConfiguration;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.jps.incremental.groovy.JpsGroovySettings;

/**
 * @author peter
 */
@State(
  name = "GroovyCompilerProjectConfiguration",
  storages = {
    @Storage(file = StoragePathMacros.PROJECT_FILE),
    @Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/groovyc.xml", scheme = StorageScheme.DIRECTORY_BASED)
  }
)
public class GroovyCompilerConfiguration implements PersistentStateComponent<JpsGroovySettings>, Disposable {
  private String myHeapSize = JpsGroovySettings.DEFAULT_HEAP_SIZE;
  private boolean myInvokeDynamic = JpsGroovySettings.DEFAULT_INVOKE_DYNAMIC;
  public boolean transformsOk = JpsGroovySettings.DEFAULT_TRANSFORMS_OK;
  private final ExcludedEntriesConfiguration myExcludeFromStubGeneration = new ExcludedEntriesConfiguration();

  public GroovyCompilerConfiguration(Project project) {
    GroovyCompilerWorkspaceConfiguration workspaceConfiguration = ServiceManager.getService(project, GroovyCompilerWorkspaceConfiguration.class);
    loadState(workspaceConfiguration.getState());
    workspaceConfiguration.myHeapSize = JpsGroovySettings.DEFAULT_HEAP_SIZE;
    workspaceConfiguration.transformsOk = JpsGroovySettings.DEFAULT_TRANSFORMS_OK;
    workspaceConfiguration.myInvokeDynamic = JpsGroovySettings.DEFAULT_INVOKE_DYNAMIC;
    workspaceConfiguration.myExcludeFromStubGeneration.removeAllExcludeEntryDescriptions();
  }

  public JpsGroovySettings getState() {
    final JpsGroovySettings bean = new JpsGroovySettings();
    bean.heapSize = myHeapSize;
    bean.invokeDynamic = myInvokeDynamic;
    bean.transformsOk = transformsOk;
    myExcludeFromStubGeneration.writeExternal(bean.excludes);
    return bean;
  }

  public static ExcludedEntriesConfiguration getExcludeConfiguration(Project project) {
    return getInstance(project).myExcludeFromStubGeneration;
  }

  public ExcludedEntriesConfiguration getExcludeFromStubGeneration() {
    return myExcludeFromStubGeneration;
  }

  public void loadState(JpsGroovySettings state) {
    myHeapSize = state.heapSize;
    myInvokeDynamic = state.invokeDynamic;
    transformsOk = state.transformsOk;

    myExcludeFromStubGeneration.readExternal(state.excludes);
  }

  public static GroovyCompilerConfiguration getInstance(Project project) {
    return ServiceManager.getService(project, GroovyCompilerConfiguration.class);
  }

  public String getHeapSize() {
    return myHeapSize;
  }

  public boolean isInvokeDynamic() {
    return myInvokeDynamic;
  }

  public void setHeapSize(String heapSize) {
    myHeapSize = heapSize;
  }

  public void setInvokeDynamic(boolean invokeDynamic) {
    myInvokeDynamic = invokeDynamic;
  }

  public void dispose() {
    Disposer.dispose(myExcludeFromStubGeneration);
  }

}
