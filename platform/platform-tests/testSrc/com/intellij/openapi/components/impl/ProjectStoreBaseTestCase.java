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
package com.intellij.openapi.components.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.JDOMBuilder;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.testFramework.PlatformTestCase;

import java.io.File;
import java.io.UnsupportedEncodingException;

public abstract class ProjectStoreBaseTestCase extends PlatformTestCase {
  @Override
  protected Project doCreateProject(File projectFile) throws Exception {
    return ProjectManagerEx.getInstanceEx().loadProject(projectFile.getAbsolutePath());
  }

  protected byte[] getIprFileContent() throws UnsupportedEncodingException {
    final String iprContent = JDOMUtil.writeDocument(
      JDOMBuilder.document(JDOMBuilder.tag("project",
                   JDOMBuilder.attr("version", "4"),
                   JDOMBuilder.tag("component", JDOMBuilder.attr("name", "TestIprComponent"),
                       JDOMBuilder.tag("option", JDOMBuilder.attr("name", "VALUE"), JDOMBuilder.attr("value", "true")))
      )),
      "\n");
    return iprContent.getBytes(CharsetToolkit.UTF8);
  }

  @State(
    name = "TestIprComponent",
    storages = {
      @Storage(file = "$PROJECT_FILE$")
    }
  )
  public static class TestIprComponent implements PersistentStateComponent<DataBean> {
    ProjectStoreBaseTestCase.DataBean myState;

    @Override
    public ProjectStoreBaseTestCase.DataBean getState() {
      throw new UnsupportedOperationException("Method getState not implemented in " + getClass());
    }

    @Override
    public void loadState(ProjectStoreBaseTestCase.DataBean object) {
      myState = object;
    }
  }

  public static class DataBean {
    @SuppressWarnings("UnusedDeclaration") public boolean VALUE = false;
  }
}
