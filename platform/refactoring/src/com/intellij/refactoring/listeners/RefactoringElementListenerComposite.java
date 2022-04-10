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

package com.intellij.refactoring.listeners;

import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RefactoringElementListenerComposite implements RefactoringElementListener, UndoRefactoringElementListener {
  private final List<RefactoringElementListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public void addListener(final RefactoringElementListener listener){
    myListeners.add(listener);
  }

  @Override
  public void elementMoved(@NotNull final PsiElement newElement){
    for (RefactoringElementListener myListener : myListeners) {
      myListener.elementMoved(newElement);
    }
  }

  @Override
  public void elementRenamed(@NotNull final PsiElement newElement){
    for (RefactoringElementListener myListener : myListeners) {
      myListener.elementRenamed(newElement);
    }
  }

  @Override
  public void undoElementMovedOrRenamed(@NotNull PsiElement newElement, @NotNull String oldQualifiedName) {
    for (RefactoringElementListener listener : myListeners) {
      if (listener instanceof UndoRefactoringElementListener) {
        ((UndoRefactoringElementListener)listener).undoElementMovedOrRenamed(newElement, oldQualifiedName);
      }
    }
  }
}
