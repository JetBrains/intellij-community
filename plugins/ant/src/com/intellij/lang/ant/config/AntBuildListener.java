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

package com.intellij.lang.ant.config;

public interface AntBuildListener {

  int FINISHED_SUCCESSFULLY = 0;
  int ABORTED = 1;
  int FAILED_TO_RUN = 2;

  AntBuildListener NULL = new AntBuildListener() {
    @Override
    public void buildFinished(int state, int errorCount) { }
  };

  void buildFinished(int state, int errorCount);
}
