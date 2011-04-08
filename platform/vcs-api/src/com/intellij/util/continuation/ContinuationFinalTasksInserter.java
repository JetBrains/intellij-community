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
package com.intellij.util.continuation;

import com.intellij.util.Consumer;

/**
 * @author irengrig
 *         Date: 4/7/11
 *         Time: 7:25 PM
 */
public class ContinuationFinalTasksInserter {
  private final ContinuationContext myContext;
  private final Consumer<TaskDescriptor> myPatcher;

  public ContinuationFinalTasksInserter(ContinuationContext context) {
    myContext = context;
    myPatcher = new Consumer<TaskDescriptor>() {
      @Override
      public void consume(TaskDescriptor taskDescriptor) {
        taskDescriptor.setHaveMagicCure(true);
      }
    };
  }

  public void allNextAreFinal() {
    myContext.addNewTasksPatcher(myPatcher);
  }

  public void removeFinalPropertyAdder() {
    myContext.removeNewTasksPatcher(myPatcher);
  }
}
