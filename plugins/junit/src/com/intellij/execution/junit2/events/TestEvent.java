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

package com.intellij.execution.junit2.events;

import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.testframework.AbstractTestProxy;

public class TestEvent {
  private final TestProxy mySource;

  public TestEvent(final TestProxy source) {
    mySource = source;
  }

  public TestProxy getSource() {
    return mySource;
  }

  public int hashCode() {
    return mySource.hashCode();
  }

  public boolean equals(final Object obj) {
    if (obj == null)
      return false;
    if (mySource != ((TestEvent) obj).mySource) return false;
    return obj.getClass() == getClass();
  }

  public AbstractTestProxy getTestSubtree() {
    return null;
  }
}
