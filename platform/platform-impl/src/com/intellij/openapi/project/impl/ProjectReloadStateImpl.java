/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.project.impl;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.ProjectReloadState;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public class ProjectReloadStateImpl extends ProjectReloadState implements ProjectComponent, JDOMExternalizable {

  public static final int UNKNOWN = 0;
  public static final int BEFORE_RELOAD = 1;
  public static final int AFTER_RELOAD = 2;

  public int STATE = UNKNOWN;

  public void projectOpened() {

  }

  public void projectClosed() {

  }

  @NotNull
  public String getComponentName() {
    return "ProjectReloadState";
  }

  public void initComponent() { }

  public void disposeComponent() {

  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  private void transformState() {
    if (STATE == BEFORE_RELOAD) {
      STATE = AFTER_RELOAD;
    }
    else if (STATE == AFTER_RELOAD) {
      STATE = UNKNOWN;
    }
  }

  public void setBeforeReload() {
    STATE = BEFORE_RELOAD;
  }

  public boolean isAfterAutomaticReload() {
    return STATE == AFTER_RELOAD;
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    transformState();
  }

  public void onBeforeAutomaticProjectReload() {
    setBeforeReload();
  }
}
