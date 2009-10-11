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

import com.intellij.psi.PsiElement;
import com.intellij.util.SpinAllocator;

import java.util.HashSet;
import java.util.Set;

public class PsiElementSetSpinAllocator {

  private PsiElementSetSpinAllocator() {
  }

  private static class Creator implements SpinAllocator.ICreator<Set<PsiElement>> {
    public Set<PsiElement> createInstance() {
      return new HashSet<PsiElement>();
    }
  }

  private static class Disposer implements SpinAllocator.IDisposer<Set<PsiElement>> {
    public void disposeInstance(final Set<PsiElement> instance) {
      instance.clear();
    }
  }

  private static final SpinAllocator<Set<PsiElement>> myAllocator =
    new SpinAllocator<Set<PsiElement>>(new Creator(), new Disposer());

  public static Set<PsiElement> alloc() {
    return myAllocator.alloc();
  }

  public static void dispose(Set<PsiElement> instance) {
    myAllocator.dispose(instance);
  }
}
