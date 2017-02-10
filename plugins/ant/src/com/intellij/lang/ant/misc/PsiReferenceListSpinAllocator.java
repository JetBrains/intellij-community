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
package com.intellij.lang.ant.misc;

import com.intellij.psi.PsiReference;
import com.intellij.util.SpinAllocator;

import java.util.ArrayList;
import java.util.List;

public class PsiReferenceListSpinAllocator {

  private PsiReferenceListSpinAllocator() {
  }

  private static class Creator implements SpinAllocator.ICreator<List<PsiReference>> {
    public List<PsiReference> createInstance() {
      return new ArrayList<>();
    }
  }

  private static class Disposer implements SpinAllocator.IDisposer<List<PsiReference>> {
    public void disposeInstance(final List<PsiReference> instance) {
      instance.clear();
    }
  }

  private static final SpinAllocator<List<PsiReference>> myAllocator =
    new SpinAllocator<>(new Creator(), new Disposer());

  public static List<PsiReference> alloc() {
    return myAllocator.alloc();
  }

  public static void dispose(List<PsiReference> instance) {
    myAllocator.dispose(instance);
  }
}
