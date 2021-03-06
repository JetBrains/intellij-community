// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.Iconable;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

  @NotNull Icon getStubIcon();

  @NotNull Icon getIcon(@NotNull String path, @NotNull Class<?> aClass);

  /**
   * Path must be specified without a leading slash, in a format for {@link ClassLoader#getResourceAsStream(String)}
   */
  @ApiStatus.Internal
  @NotNull Icon loadRasterizedIcon(@NotNull String path, @NotNull ClassLoader classLoader, long cacheKey, int flags);

  /**
   * @deprecated Method just for backward compatibility (old generated icon classes).
   */
  @Deprecated
  @ApiStatus.Internal
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  default @NotNull Icon loadRasterizedIcon(@NotNull String path, @NotNull Class<?> aClass, long cacheKey, int flags) {
    return loadRasterizedIcon(path.startsWith("/") ? path.substring(1) : path, aClass.getClassLoader(), cacheKey, flags);
  }

  @NotNull
  default Icon createEmptyIcon(@NotNull Icon icon) {
    return icon;
  }

  @NotNull
  default Icon createOffsetIcon(@NotNull Icon icon) {
    return icon;
  }

  @NotNull
  Icon createLayered(Icon @NotNull ... icons);

  @NotNull
  default Icon colorize(Graphics2D g, @NotNull Icon source, @NotNull Color color) {
    return source;
  }

  @NotNull <T> Icon createDeferredIcon(@Nullable Icon base, T param, @NotNull Function<? super T, ? extends Icon> f);

  @NotNull
  RowIcon createLayeredIcon(@NotNull Iconable instance, Icon icon, int flags);

  @NotNull
  default RowIcon createRowIcon(int iconCount) {
    return createRowIcon(iconCount, RowIcon.Alignment.TOP);
  }

  @NotNull
  RowIcon createRowIcon(int iconCount, RowIcon.Alignment alignment);

  @NotNull
  RowIcon createRowIcon(Icon @NotNull ... icons);

  void registerIconLayer(int flagMask, @NotNull Icon icon);

  @NotNull Icon tooltipOnlyIfComposite(@NotNull Icon icon);
}

final class IconManagerHelper {
  private static final AtomicBoolean isActivated = new AtomicBoolean();
  static volatile IconManager instance = DummyIconManager.INSTANCE;

  static void activate() {
    if (!isActivated.compareAndSet(false, true)) {
      return;
    }

    Iterator<IconManager> iterator = ServiceLoader.load(IconManager.class, IconManager.class.getClassLoader()).iterator();
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

  @Override
  public @NotNull Icon getStubIcon() {
    return DummyIcon.INSTANCE;
  }

  @NotNull
  @Override
  public Icon getIcon(@NotNull String path, @NotNull Class<?> aClass) {
    return DummyIcon.INSTANCE;
  }

  @Override
  public @NotNull Icon loadRasterizedIcon(@NotNull String path, @NotNull ClassLoader classLoader, long cacheKey, int flags) {
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

  @Override
  public @NotNull Icon tooltipOnlyIfComposite(@NotNull Icon icon) {
    return new DummyIcon();
  }

  @Override
  public @NotNull <T> Icon createDeferredIcon(@Nullable Icon base, T param, @NotNull Function<? super T, ? extends Icon> f) {
    return base;
  }

  @NotNull
  @Override
  public RowIcon createRowIcon(int iconCount, RowIcon.Alignment alignment) {
    return new DummyRowIcon(iconCount);
  }

  @NotNull
  @Override
  public Icon createLayered(Icon @NotNull ... icons) {
    return new DummyRowIcon(icons);
  }

  @NotNull
  @Override
  public RowIcon createRowIcon(Icon @NotNull ... icons) {
    return new DummyRowIcon(icons);
  }

  private static class DummyIcon implements Icon {
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

    @NotNull
    @Override
    public Icon getDarkIcon(boolean isDark) {
      return this;
    }

    @Override
    public Icon @NotNull [] getAllIcons() {
      return icons == null ? new Icon[0] : ContainerUtil.packNullables(icons).toArray(new Icon[0]);
    }
  }
}
