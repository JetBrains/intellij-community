/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.intellij.util;

import java.util.HashSet;
import java.util.Set;

public class StringSetSpinAllocator {

  private StringSetSpinAllocator() {
  }

  private static class Creator implements SpinAllocator.ICreator<Set<String>> {
    @Override
    public Set<String> createInstance() {
      return new HashSet<String>();
    }
  }

  private static class Disposer implements SpinAllocator.IDisposer<Set<String>> {
    @Override
    public void disposeInstance(final Set<String> instance) {
      instance.clear();
    }
  }

  private static final SpinAllocator<Set<String>> myAllocator =
    new SpinAllocator<Set<String>>(new Creator(), new Disposer());

  public static Set<String> alloc() {
    return myAllocator.alloc();
  }

  public static void dispose(Set<String> instance) {
    myAllocator.dispose(instance);
  }
}
