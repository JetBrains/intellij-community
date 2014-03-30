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

import java.util.ArrayList;
import java.util.List;

public class StringListSpinAllocator {

  private StringListSpinAllocator() {
  }

  private static class Creator implements SpinAllocator.ICreator<List<String>> {
    @Override
    public List<String> createInstance() {
      return new ArrayList<String>();
    }
  }

  private static class Disposer implements SpinAllocator.IDisposer<List<String>> {
    @Override
    public void disposeInstance(final List<String> instance) {
      instance.clear();
    }
  }

  private static final SpinAllocator<List<String>> myAllocator =
    new SpinAllocator<List<String>>(new Creator(), new Disposer());

  public static List<String> alloc() {
    return myAllocator.alloc();
  }

  public static void dispose(List<String> instance) {
    myAllocator.dispose(instance);
  }
}