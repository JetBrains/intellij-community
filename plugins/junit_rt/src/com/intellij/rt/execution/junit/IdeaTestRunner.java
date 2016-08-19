/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
 * Date: 05-Jun-2009
 */
package com.intellij.rt.execution.junit;

import java.util.ArrayList;
import java.util.List;

public interface IdeaTestRunner {

  int startRunnerWithArgs(String[] args, ArrayList listeners, String name, int count, boolean sendTree);

  Object getTestToStart(String[] args, String name);
  List getChildTests(Object description);
  String getStartDescription(Object child);

  String getTestClassName(Object child);
}