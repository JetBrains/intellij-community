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

/**
 * StringBuilderSpinAllocator reuses StringBuilder instances performing non-blocking allocation and dispose.
 * @deprecated Simple allocation is faster than this (according to StringBuilderSpinAllocatorTester)
 */
@Deprecated
public class StringBuilderSpinAllocator {

  private StringBuilderSpinAllocator() {
  }

  private static class Creator implements SpinAllocator.ICreator<StringBuilder> {
    @Override
    public StringBuilder createInstance() {
      return new StringBuilder();
    }
  }

  private static class Disposer implements SpinAllocator.IDisposer<StringBuilder> {
    @Override
    public void disposeInstance(final StringBuilder instance) {
      instance.setLength(0);
      if (instance.capacity() > 1024) {
        instance.trimToSize();
      }
    }
  }

  private static final SpinAllocator<StringBuilder> myAllocator = new SpinAllocator<StringBuilder>(new Creator(), new Disposer());

  public static StringBuilder alloc() {
    return myAllocator.alloc();
  }

  public static void dispose(StringBuilder instance) {
    myAllocator.dispose(instance);
  }
}
