/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.formatter;

import com.intellij.formatting.Alignment;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * @author Max Medvedev
 */
class AlignmentProvider {
  private final static Logger LOG = Logger.getInstance(AlignmentProvider.class);

  private final Map<PsiElement, Set<PsiElement>> myTree = new HashMap<PsiElement, Set<PsiElement>>();
  private final Map<Set<PsiElement>, Alignment> myAlignments = new HashMap<Set<PsiElement>, Alignment>();
  private final Map<Set<PsiElement>, Boolean> myAllowBackwardShift = new HashMap<Set<PsiElement>, Boolean>();

  public void addPair(PsiElement e1, PsiElement e2, Boolean allowBackwardShift) {
    LOG.assertTrue(e1 != e2);

    final Set<PsiElement> set1 = myTree.get(e1);
    final Set<PsiElement> set2 = myTree.get(e2);

    if (set1 != null && set2 != null) {
      LOG.assertTrue(!myAlignments.containsKey(set1) || !myAlignments.containsKey(set2));
      LOG.assertTrue(myAllowBackwardShift.get(set1).booleanValue() == myAllowBackwardShift.get(set2).booleanValue());
      if (allowBackwardShift != null) {
        LOG.assertTrue(myAllowBackwardShift.get(set1).booleanValue() == allowBackwardShift.booleanValue());
      }
      if (myAlignments.containsKey(set2)) {
        for (Iterator<PsiElement> iterator = set1.iterator(); iterator.hasNext(); ) {
          PsiElement element = iterator.next();
          iterator.remove();

          addInternal(set2, element);
        }
      }
      else {
        set1.addAll(set2);
        for (Iterator<PsiElement> iterator = set2.iterator(); iterator.hasNext(); ) {
          PsiElement element = iterator.next();
          iterator.remove();

          addInternal(set1, element);
        }
      }
    }
    else if (set1 != null) {
      if (allowBackwardShift != null) {
        LOG.assertTrue(myAllowBackwardShift.get(set1).booleanValue() == allowBackwardShift.booleanValue());
      }
      addInternal(set1, e2);
    }
    else if (set2 != null) {
      if (allowBackwardShift != null) {
        LOG.assertTrue(myAllowBackwardShift.get(set2).booleanValue() == allowBackwardShift.booleanValue());
      }
      addInternal(set2, e1);
    }
    else {
      final HashSet<PsiElement> set = createHashSet();
      addInternal(set, e1);
      addInternal(set, e2);
      myAllowBackwardShift.put(set, allowBackwardShift);

    }
  }

  private void addInternal(Set<PsiElement> set, PsiElement element) {
    myTree.put(element, set);
    set.add(element);
  }

  private static HashSet<PsiElement> createHashSet() {
    return new HashSet<PsiElement>() {
      private final int myhash = new Object().hashCode();

      @Override
      public int hashCode() {
        return myhash;
      }
    };
  }

  public void addPair(ASTNode node1, ASTNode node2, boolean allowBackwardShift) {
    addPair(node1.getPsi(), node2.getPsi(), allowBackwardShift);
  }

  private void add(PsiElement element, boolean allowBackwardShift) {
    if (myTree.get(element) != null) return;

    final HashSet<PsiElement> set = createHashSet();
    set.add(element);
    myTree.put(element, set);
    myAllowBackwardShift.put(set, allowBackwardShift);
  }

  @Nullable
  public Alignment getAlignment(PsiElement e) {
    final Set<PsiElement> set = myTree.get(e);
    if (set == null) {
      return null;
    }

    Alignment alignment = myAlignments.get(set);
    if (alignment != null) return alignment;

    alignment = Alignment.createAlignment(myAllowBackwardShift.get(set));
    myAlignments.put(set, alignment);
    return alignment;
  }

  public Aligner createAligner(PsiElement expression, boolean allowBackwardShift) {
    Aligner aligner = new Aligner(allowBackwardShift);
    aligner.append(expression);
    return aligner;
  }

  public Aligner createAligner(boolean allowBackwardShift) {
    return new Aligner(allowBackwardShift);
  }

  /**
   * This class helps to assign one alignment to some elements.
   * You can create an instance of Aligner and apply 'append' to any element, you want to be aligned.
   *
   * @author Max Medvedev
   */
  class Aligner {
    private PsiElement myRef = null;
    private boolean allowBackwardShift = true;

    Aligner(boolean allowBackwardShift) {
      this.allowBackwardShift = allowBackwardShift;
    }

    void append(@Nullable PsiElement element) {
      if (element == null) return;

      if (myRef == null) {
        myRef = element;
        add(element, allowBackwardShift);
      }
      else {
        addPair(myRef, element, allowBackwardShift);
      }
    }
  }
}
