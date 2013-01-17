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
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;

/**
 * @author peter
 */
@State(
  name = "GroovyCompilerProjectConfiguration",
  storages = {
    @Storage( file = StoragePathMacros.PROJECT_FILE),
    @Storage( file = StoragePathMacros.PROJECT_CONFIG_DIR + "/groovyc.xml", scheme = StorageScheme.DIRECTORY_BASED)
  }
)
public class GroovyCompilerConfiguration implements PersistentStateComponent<GroovyCompilerConfiguration.MyStateBean>, Disposable {
  static final String DEFAULT_HEAP_SIZE = "400";
  static final boolean DEFAULT_INVOKE_DYNAMIC = false;
  static final boolean DEFAULT_TRANSFORMS_OK = false;

  private String myHeapSize = DEFAULT_HEAP_SIZE;
  private boolean myInvokeDynamic = DEFAULT_INVOKE_DYNAMIC;
  public boolean transformsOk = DEFAULT_TRANSFORMS_OK;
  private final ExcludedEntriesConfiguration myExcludeFromStubGeneration = new ExcludedEntriesConfiguration();

  public GroovyCompilerConfiguration(Project project) {
    GroovyCompilerWorkspaceConfiguration workspaceConfiguration = ServiceManager.getService(project, GroovyCompilerWorkspaceConfiguration.class);
    loadState(workspaceConfiguration.getState());
    workspaceConfiguration.myHeapSize = DEFAULT_HEAP_SIZE;
    workspaceConfiguration.transformsOk = DEFAULT_TRANSFORMS_OK;
    workspaceConfiguration.myInvokeDynamic = DEFAULT_INVOKE_DYNAMIC;
    workspaceConfiguration.myExcludeFromStubGeneration.removeAllExcludeEntryDescriptions();
  }

  public MyStateBean getState() {
    final MyStateBean bean = new MyStateBean();
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

  public void loadState(MyStateBean state) {
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

  public static class MyStateBean {
    public String heapSize = DEFAULT_HEAP_SIZE;
    public boolean invokeDynamic = DEFAULT_INVOKE_DYNAMIC;

    @Tag("excludes") public Element excludes = new Element("aaa");

    public boolean transformsOk = DEFAULT_TRANSFORMS_OK;

  }
}
