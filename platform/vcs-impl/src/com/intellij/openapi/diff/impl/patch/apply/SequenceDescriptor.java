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
package com.intellij.openapi.diff.impl.patch.apply;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 9/28/11
 * Time: 2:50 PM
 */
public interface SequenceDescriptor {
  SequenceDescriptor STUB = new SequenceDescriptor() {
    @Override
    public int getDistance() {
      return 0;
    }

    @Override
    public int getSizeOfFragmentToBeReplaced() {
      return 0;
    }

    @Override
    public boolean isUsesAlreadyApplied() {
      return false;
    }
  };

  int getDistance();
  int getSizeOfFragmentToBeReplaced();
  boolean isUsesAlreadyApplied();
}
