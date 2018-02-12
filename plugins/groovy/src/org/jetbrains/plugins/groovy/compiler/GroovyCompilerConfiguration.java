// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
import org.jetbrains.annotations.NotNull;
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
  private final ExcludedEntriesConfiguration myExcludeFromStubGeneration = new ExcludedEntriesConfiguration(null);

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
  public void loadState(@NotNull JpsGroovySettings state) {
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
