// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.formatter;

import com.intellij.formatting.Alignment;
import com.intellij.psi.PsiElement;
import kotlin.Lazy;
import kotlin.LazyKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class AlignmentProvider {

  private final Map<PsiElement, Aligner> myElement2Aligner = new HashMap<>();

  public void addPair(@NotNull PsiElement e1, @NotNull PsiElement e2, boolean allowBackwardShift) {
    Aligner aligner = createAligner(allowBackwardShift);
    aligner.append(e1);
    aligner.append(e2);
  }

  @Nullable
  public Alignment getAlignment(@NotNull PsiElement e) {
    Aligner aligner = myElement2Aligner.get(e);
    return aligner == null ? null : aligner.getAlignment();
  }

  @NotNull
  public Aligner createAligner(boolean allowBackwardShift) {
    return new Aligner(allowBackwardShift, Alignment.Anchor.LEFT);
  }

  @NotNull
  public Aligner createAligner(PsiElement element, boolean allowBackwardShift, Alignment.Anchor anchor) {
    final Aligner aligner = new Aligner(allowBackwardShift, anchor);
    aligner.append(element);
    return aligner;
  }

  /**
   * This class helps to assign one alignment to some elements.
   * You can create an instance of Aligner and apply 'append' to any element, you want to be aligned.
   *
   * @author Max Medvedev
   */
  public final class Aligner {

    private final Lazy<Alignment> myAlignment;

    private Aligner(boolean allowBackwardShift, @NotNull Alignment.Anchor anchor) {
      myAlignment = LazyKt.lazy(() -> Alignment.createAlignment(allowBackwardShift, anchor));
    }

    public void append(@Nullable PsiElement element) {
      if (element == null) return;
      assert !myElement2Aligner.containsKey(element) || myElement2Aligner.get(element) == this;
      myElement2Aligner.put(element, this);
    }

    public @NotNull Alignment getAlignment() {
      return myAlignment.getValue();
    }
  }
}
