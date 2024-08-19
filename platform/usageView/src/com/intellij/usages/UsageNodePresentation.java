// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages;

import com.intellij.util.LazyInitializer;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

@Internal
public final class UsageNodePresentation {
  private static final @NotNull LazyInitializer.LazyValue<@NotNull UsageNodePresentation> EMPTY = LazyInitializer.create(
    () -> new UsageNodePresentation(null, TextChunk.EMPTY_ARRAY, null)
  );

  public static @NotNull UsageNodePresentation empty() {
    return EMPTY.get();
  }

  private final @Nullable Icon myIcon;
  private final @Nullable Color myBackgroundColor;
  private final @NotNull Object myText;

  public UsageNodePresentation(
    @Nullable Icon icon,
    @NotNull TextChunk @NotNull [] text,
    @Nullable Color color) {
    myIcon = icon;
    myText = TextChunk.compact(text);
    myBackgroundColor = color;
  }

  public @Nullable Icon getIcon() {
    return myIcon;
  }

  public @NotNull TextChunk @NotNull [] getText() {
    return TextChunk.inflate(myText);
  }

  public @Nullable Color getBackgroundColor() {
    return myBackgroundColor;
  }
}
