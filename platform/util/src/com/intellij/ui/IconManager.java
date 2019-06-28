// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.Iconable;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public interface IconManager {
  @NotNull
  static IconManager getInstance() {
    return IconManagerHelper.instance;
  }

  // Icon Loader is quite heavy, better to not instantiate class unless required
  static void activate() {
    IconManagerHelper.activate();
  }

  @TestOnly
  static void deactivate() {
    IconManagerHelper.deactivate();
  }

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
  Icon createLayered(@NotNull Icon... icons);

  @NotNull
  default Icon colorize(Graphics2D g, @NotNull Icon source, @NotNull Color color) {
    return source;
  }

  @NotNull
  Icon getAnalyzeIcon();

  @NotNull
  <T> Icon createDeferredIcon(@NotNull Icon base, T param, @NotNull Function<? super T, ? extends Icon> f);

  @NotNull
  RowIcon createLayeredIcon(@NotNull Iconable instance, Icon icon, int flags);

  @NotNull
  default RowIcon createRowIcon(int iconCount) {
    return createRowIcon(iconCount, RowIcon.Alignment.TOP);
  }

  @NotNull
  RowIcon createRowIcon(int iconCount, RowIcon.Alignment alignment);

  @NotNull
  RowIcon createRowIcon(@NotNull Icon... icons);

  void registerIconLayer(int flagMask, @NotNull Icon icon);
}

final class IconManagerHelper {
  private static final AtomicBoolean isActivated = new AtomicBoolean();
  static volatile IconManager instance = DummyIconManager.INSTANCE;

  static void activate() {
    if (!isActivated.compareAndSet(false, true)) {
      return;
    }

    Iterator<IconManager> iterator = ServiceLoader.load(IconManager.class).iterator();
    if (iterator.hasNext()) {
      instance = iterator.next();
    }
  }

  static void deactivate() {
    if (isActivated.compareAndSet(true, false)) {
      instance = DummyIconManager.INSTANCE;
    }
  }
}

final class DummyIconManager implements IconManager {
  static final IconManager INSTANCE = new DummyIconManager();

  private DummyIconManager() {
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
  public RowIcon createLayeredIcon(@NotNull Iconable instance, Icon icon, int flags) {
    return new DummyRowIcon();
  }

  @Override
  public void registerIconLayer(int flagMask, @NotNull Icon icon) {
  }

  @NotNull
  @Override
  public <T> Icon createDeferredIcon(@NotNull Icon base, T param, @NotNull Function<? super T, ? extends Icon> f) {
    return base;
  }

  @NotNull
  @Override
  public RowIcon createRowIcon(int iconCount, RowIcon.Alignment alignment) {
    return new DummyRowIcon(iconCount);
  }

  @NotNull
  @Override
  public Icon createLayered(@NotNull Icon... icons) {
    return new DummyRowIcon(icons);
  }

  @NotNull
  @Override
  public RowIcon createRowIcon(@NotNull Icon... icons) {
    return new DummyRowIcon(icons);
  }

  private static class DummyIcon implements Icon {
    static final DummyIcon INSTANCE = new DummyIcon();

    private DummyIcon() {
    }

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

  private static class DummyRowIcon extends DummyIcon implements RowIcon {
    private Icon[] icons;

    DummyRowIcon(int iconCount) {
      icons = new Icon[iconCount];
    }

    DummyRowIcon(Icon[] icons) {
      this.icons = icons;
    }

    DummyRowIcon() {
    }

    @Override
    public int getIconCount() {
      return icons == null ? 0 : icons.length;
    }

    @Override
    public Icon getIcon(int index) {
      return icons[index];
    }

    @Override
    public void setIcon(Icon icon, int i) {
      if (icons == null) {
        icons = new Icon[4];
      }
      icons[i] = icon;
    }

    @Override
    public Icon getDarkIcon(boolean isDark) {
      return this;
    }

    @NotNull
    @Override
    public Icon[] getAllIcons() {
      return icons == null ? new Icon[0] : ContainerUtil.packNullables(icons).toArray(new Icon[0]);
    }
  }
}
