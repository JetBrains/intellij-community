/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.mvc.projectView;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Krasilschikov
 */

public class NodeId {

  private final @NotNull PsiElement myElement;

  /**
   * Unique id for Rails Project view nodes.
   * By common sense and for correct support of rename operation.
   * NodeId with equal PsiElements and locationRootMarks should be equal. Thus there is
   * no sence to use psiElement with cached fileUrl
   * On the other hand some elements may not have reperesentaion in Psi
   * (e.g. files with unregistered extension), thus we should work with them via file url.
   *
   * @param element
   */
  public NodeId(@NotNull final PsiElement element) {
    myElement = element;
  }

  @Nullable
  public PsiElement getPsiElement() {
    return myElement;
  }

  public String toString() {
    // For Debug purposes
    return "[psiElement = " + getPsiElement() + "]";
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final NodeId nodeId = (NodeId)o;

    if (!myElement.equals(nodeId.myElement)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myElement.hashCode();
    result = 31 * result;
    return result;
  }
}