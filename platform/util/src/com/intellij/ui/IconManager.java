// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.Iconable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.function.Function;

public interface IconManager {
  @NotNull
  static IconManager getInstance() {
    return Holder.INSTANCE;
  }

  @NotNull
  Icon getIcon(@NotNull String path);

  @NotNull
  Icon getIcon(@NotNull String path, @NotNull Class aClass);

  @NotNull
  default Icon createEmptyIcon(@NotNull Icon icon) {
    return icon;
  }

  @NotNull
  default Icon createOffsetIcon(@NotNull Icon icon) {
    return icon;
  }

  @NotNull
  default Icon createLayered(@NotNull Icon... icons) {
    return icons[0];
  }

  @NotNull
  default Icon colorize(Graphics2D g, @NotNull Icon source, @NotNull Color color) {
    return source;
  }

  @NotNull
  Icon getAnalyzeIcon();

  @NotNull
  <T> Icon createDeferredIcon(@NotNull Icon base, T param, @NotNull Function<? super T, ? extends Icon> f);

  @NotNull
  Icon createLayeredIcon(@NotNull Iconable instance, Icon icon, int flags);
}

class Holder {
  @NotNull
  private static IconManager getInstance() {
    Iterator<IconManager> iterator = ServiceLoader.load(IconManager.class).iterator();
    if (iterator.hasNext()) {
      return iterator.next();
    }
    else {
      return new DummyIconManager();
    }
  }

  static final IconManager INSTANCE = getInstance();
}

final class DummyIconManager implements IconManager {
  @NotNull
  @Override
  public Icon getIcon(@NotNull String path) {
    return DummyIcon.INSTANCE;
  }

  @NotNull
  @Override
  public Icon getIcon(@NotNull String path, @NotNull Class aClass) {
    return DummyIcon.INSTANCE;
  }

  @NotNull
  @Override
  public Icon getAnalyzeIcon() {
    return DummyIcon.INSTANCE;
  }

  @NotNull
  @Override
  public Icon createLayeredIcon(@NotNull Iconable instance, Icon icon, int flags) {
    return DummyIcon.INSTANCE;
  }

  @NotNull
  @Override
  public <T> Icon createDeferredIcon(@NotNull Icon base, T param, @NotNull Function<? super T, ? extends Icon> f) {
    return new DummyIcon();
  }

  private static final class DummyIcon implements Icon {
    static final DummyIcon INSTANCE = new DummyIcon();
    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
    }

    @Override
    public int getIconWidth() {
      return 16;
    }

    @Override
    public int getIconHeight() {
      return 16;
    }

    @Override
    public int hashCode() {
      return 0;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof DummyIcon;
    }
  }
}
