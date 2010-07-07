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

/*
 * User: anna
 * Date: 23-May-2007
 */
package com.intellij.execution.testframework;

import com.intellij.execution.Location;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NonNls;

import java.util.List;

public interface AbstractTestProxy {
  DataKey<AbstractTestProxy> DATA_KEY = DataKey.create("testProxy");
  @Deprecated @NonNls String DATA_CONSTANT = DATA_KEY.getName();

  boolean isInProgress();

  boolean isDefect();

  //todo?
  boolean shouldRun();

  int getMagnitude();

  boolean isLeaf();

  boolean isInterrupted();

  boolean isPassed();

  String getName();

  Location getLocation(final Project project);

  Navigatable getDescriptor(final Location location);

  AbstractTestProxy getParent();

  List<? extends AbstractTestProxy> getChildren();

  List<? extends AbstractTestProxy> getAllTests();
}
