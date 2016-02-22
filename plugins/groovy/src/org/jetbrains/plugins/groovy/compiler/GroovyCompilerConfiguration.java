/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.compiler.options.ExcludesConfiguration;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.jps.incremental.groovy.JpsGroovySettings;

/**
 * @author peter
 */
@State(name = "GroovyCompilerProjectConfiguration", storages = @Storage("groovyc.xml"))
public class GroovyCompilerConfiguration implements PersistentStateComponent<JpsGroovySettings>, Disposable {
  private String myConfigScript = "";
  private String myHeapSize = JpsGroovySettings.DEFAULT_HEAP_SIZE;
  private boolean myInvokeDynamic = JpsGroovySettings.DEFAULT_INVOKE_DYNAMIC;
  public boolean transformsOk = JpsGroovySettings.DEFAULT_TRANSFORMS_OK;
  private final ExcludedEntriesConfiguration myExcludeFromStubGeneration = new ExcludedEntriesConfiguration();

  @Override
  public JpsGroovySettings getState() {
    final JpsGroovySettings bean = new JpsGroovySettings();
    bean.heapSize = myHeapSize;
    bean.configScript = myConfigScript;
    bean.invokeDynamic = myInvokeDynamic;
    bean.transformsOk = transformsOk;
    myExcludeFromStubGeneration.writeExternal(bean.excludes);
    return bean;
  }

  public static ExcludesConfiguration getExcludeConfiguration(Project project) {
    return getInstance(project).myExcludeFromStubGeneration;
  }

  public ExcludesConfiguration getExcludeFromStubGeneration() {
    return myExcludeFromStubGeneration;
  }

  @Override
  public void loadState(JpsGroovySettings state) {
    myHeapSize = state.heapSize;
    myConfigScript = state.configScript;
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

  public String getConfigScript() {
    return myConfigScript;
  }

  public void setConfigScript(String configScript) {
    myConfigScript = configScript;
  }

  @Override
  public void dispose() {
    Disposer.dispose(myExcludeFromStubGeneration);
  }

}
