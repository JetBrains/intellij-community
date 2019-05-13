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
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;

/**
 * @author irengrig
 */
public class DummyChangesViewContentManager implements ChangesViewContentI {
  @Override
  public void setUp(ToolWindow toolWindow) {

  }

  @Override
  public boolean isAvailable() {
    return false;
  }

  @Override
  public void addContent(final Content content) {
  }

  @Override
  public void removeContent(final Content content) {
  }

  @Override
  public void setSelectedContent(final Content content) {
  }

  @Override
  public <T> T getActiveComponent(final Class<T> aClass) {
    return null;
  }

  @Override
  public void selectContent(final String tabName) {
  }
}
