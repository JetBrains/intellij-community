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

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.util.SpinAllocator;

import java.util.HashSet;
import java.util.Set;

public class PsiElementWithValueSetSpinAllocator {

  private PsiElementWithValueSetSpinAllocator() {
  }

  private static class Creator implements SpinAllocator.ICreator<Set<Pair<PsiElement, String>>> {
    public Set<Pair<PsiElement, String>> createInstance() {
      return new HashSet<Pair<PsiElement, String>>();
    }
  }

  private static class Disposer implements SpinAllocator.IDisposer<Set<Pair<PsiElement, String>>> {
    public void disposeInstance(final Set<Pair<PsiElement, String>> instance) {
      instance.clear();
    }
  }

  private static final SpinAllocator<Set<Pair<PsiElement, String>>> myAllocator =
    new SpinAllocator<Set<Pair<PsiElement, String>>>(new Creator(), new Disposer());

  public static Set<Pair<PsiElement, String>> alloc() {
    return myAllocator.alloc();
  }

  public static void dispose(Set<Pair<PsiElement, String>> instance) {
    myAllocator.dispose(instance);
  }
}
