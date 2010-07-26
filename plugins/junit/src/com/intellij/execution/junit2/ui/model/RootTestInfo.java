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
package com.intellij.execution.junit2.ui.model;

import com.intellij.execution.Location;
import com.intellij.execution.junit2.info.TestInfo;
import com.intellij.execution.junit2.segments.ObjectReader;
import com.intellij.openapi.project.Project;

/**
* User: anna
* Date: Jul 21, 2010
*/
public class RootTestInfo extends TestInfo {
  private String myName = SpecialNode.TESTS_IN_PROGRESS;

  public String getComment() {
    return "";
  }

  public String getName() { return myName; }

  public void setName(final String name) { myName = name; }

  public boolean shouldRun() {
    return false;
  }

  public int getTestsCount() {
    return 0;
  }

  @Override
  public void readFrom(ObjectReader reader) {
  }

  public Location getLocation(final Project project) {
    return null;
  }

}
