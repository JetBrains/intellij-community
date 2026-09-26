// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.actionSystem.impl.UtilsKt;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * @author gregsh
 */
public class ActionPromoterTest extends LightPlatformTestCase {

  public void testPromotion() {
    AnAction copy = newAction("copy"), paste = newAction("paste"), cut = newAction("cut");
    copy.getTemplatePresentation().setText("copy");
    paste.getTemplatePresentation().setText("paste");
    cut.getTemplatePresentation().setText("cut");
    List<AnAction> actions = List.of(copy, paste, cut);
    DataContext ctx = DataContext.EMPTY_CONTEXT;
    assertEquals(
      "promoteAll", List.of(copy, paste, cut), UtilsKt.rearrangeByPromotersImpl(
        actions, ctx, List.of(newPromoter(o -> (List<AnAction>)o))));
    assertEquals(
      "promoteNull", List.of(copy, paste, cut), UtilsKt.rearrangeByPromotersImpl(
        actions, ctx, List.of(newPromoter(o -> null))));
    assertEquals(
      "promoteNullAction", List.of(copy, paste, cut), UtilsKt.rearrangeByPromotersImpl(
        actions, ctx, List.of(newPromoter(o -> Collections.singletonList(null)))));
    assertEquals(
      "promoteLast", List.of(cut, copy, paste), UtilsKt.rearrangeByPromotersImpl(
        actions, ctx, List.of(newPromoter(o -> Collections.singletonList(cut)))));
    assertEquals(
      "promoteDemote", List.of(cut, paste, copy), UtilsKt.rearrangeByPromotersImpl(
        actions, ctx, List.of(newPromoter(o -> List.of(cut, paste, copy)))));
    assertEquals(
      "promoteDoubleShift", List.of(cut, copy, paste), UtilsKt.rearrangeByPromotersImpl(
        actions, ctx, List.of(newPromoter(o -> List.of(o.get(1), o.get(2), o.get(0))),
                              newPromoter(o -> List.of(o.get(1), o.get(2), o.get(0))))));
  }

  public void testSuppression() {
    AnAction copy = newAction("copy"), paste = newAction("paste"), cut = newAction("cut");
    copy.getTemplatePresentation().setText("copy");
    paste.getTemplatePresentation().setText("paste");
    cut.getTemplatePresentation().setText("cut");
    List<AnAction> actions = List.of(copy, paste, cut);
    DataContext ctx = DataContext.EMPTY_CONTEXT;
    assertEquals(
      "suppressAll", List.of(), UtilsKt.rearrangeByPromotersImpl(
        actions, ctx, List.of(newSuppressor(o -> (List<AnAction>)o))));
    assertEquals(
      "suppressNull", List.of(copy, paste, cut), UtilsKt.rearrangeByPromotersImpl(
        actions, ctx, List.of(newSuppressor(o -> null))));
    assertEquals(
      "suppressNullAction", List.of(copy, paste, cut), UtilsKt.rearrangeByPromotersImpl(
        actions, ctx, List.of(newSuppressor(o -> Collections.singletonList(null)))));
    assertEquals(
      "suppressLast", List.of(copy, paste), UtilsKt.rearrangeByPromotersImpl(
        actions, ctx, List.of(newSuppressor(o -> Collections.singletonList(cut)))));
  }

  private static @NotNull AnAction newAction(@NotNull String name) {
    return EmptyAction.createEmptyAction(name, null, false);
  }

  private static @NotNull ActionPromoter newPromoter(@NotNull Function<List<? extends AnAction>, List<AnAction>> function) {
    return new ActionPromoter() {
      @Override
      public @Nullable List<AnAction> promote(@NotNull @Unmodifiable List<? extends AnAction> actions, @NotNull DataContext context) {
        return function.apply(actions);
      }
    };
  }

  private static @NotNull ActionPromoter newSuppressor(@NotNull Function<List<? extends AnAction>, List<AnAction>> function) {
    return new ActionPromoter() {
      @Override
      public @Nullable List<AnAction> suppress(@NotNull @Unmodifiable List<? extends AnAction> actions, @NotNull DataContext context) {
        return function.apply(actions);
      }
    };
  }
}
