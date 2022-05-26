// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.ScalableIcon;
import com.intellij.ui.icons.RowIcon;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public interface IconManager {
  static @NotNull IconManager getInstance() {
    IconManager result = IconManagerHelper.instance;
    return result == null ? DummyIconManager.INSTANCE : result;
  }

  // Icon Loader is quite heavy, better to not instantiate class unless required
  static void activate(@Nullable IconManager impl) throws Throwable {
    IconManagerHelper.activate(impl);
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
  @NotNull Icon loadRasterizedIcon(@NotNull String path, @NotNull ClassLoader classLoader, int cacheKey, int flags);

  default @NotNull Icon createEmptyIcon(@NotNull Icon icon) {
    return icon;
  }

  default @NotNull Icon createOffsetIcon(@NotNull Icon icon) {
    return icon;
  }

  @NotNull Icon createLayered(Icon @NotNull ... icons);

  default @NotNull Icon colorize(Graphics2D g, @NotNull Icon source, @NotNull Color color) {
    return source;
  }

  @NotNull <T> Icon createDeferredIcon(@Nullable Icon base, T param, @NotNull Function<? super T, ? extends Icon> iconProducer);

  @NotNull RowIcon createLayeredIcon(@NotNull Iconable instance, Icon icon, int flags);

  default @NotNull RowIcon createRowIcon(int iconCount) {
    return createRowIcon(iconCount, RowIcon.Alignment.TOP);
  }

  @NotNull RowIcon createRowIcon(int iconCount, RowIcon.Alignment alignment);

  @NotNull RowIcon createRowIcon(Icon @NotNull ... icons);

  void registerIconLayer(int flagMask, @NotNull Icon icon);

  @NotNull Icon tooltipOnlyIfComposite(@NotNull Icon icon);
}

final class IconManagerHelper {
  private static final AtomicBoolean isActivated = new AtomicBoolean();
  static volatile IconManager instance;

  static void activate(@Nullable IconManager impl) throws Throwable {
    if (!isActivated.compareAndSet(false, true)) {
      return;
    }

    if (impl == null) {
      Class<?> implClass = IconManagerHelper.class.getClassLoader().loadClass("com.intellij.ui.CoreIconManager");
      instance = (IconManager)MethodHandles.lookup().findConstructor(implClass, MethodType.methodType(void.class)).invoke();
    }
    else {
      instance = impl;
    }
  }

  static void deactivate() {
    if (isActivated.compareAndSet(true, false)) {
      instance = null;
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

  @Override
  public @NotNull Icon getIcon(@NotNull String path, @NotNull Class<?> aClass) {
    return new DummyIcon(path);
  }

  @Override
  public @NotNull Icon loadRasterizedIcon(@NotNull String path, @NotNull ClassLoader classLoader, int cacheKey, int flags) {
    return new DummyIcon(path);
  }

  @Override
  public @NotNull RowIcon createLayeredIcon(@NotNull Iconable instance, Icon icon, int flags) {
    Icon[] icons = new Icon[2];
    icons[0] = icon;
    return new DummyRowIcon(icons);
  }

  @Override
  public void registerIconLayer(int flagMask, @NotNull Icon icon) {
  }

  @Override
  public @NotNull Icon tooltipOnlyIfComposite(@NotNull Icon icon) {
    return icon;
  }

  @Override
  public @NotNull <T> Icon createDeferredIcon(@Nullable Icon base, T param, @NotNull Function<? super T, ? extends Icon> iconProducer) {
    return base;
  }

  @Override
  public @NotNull RowIcon createRowIcon(int iconCount, RowIcon.Alignment alignment) {
    return new DummyRowIcon(iconCount);
  }

  @Override
  public @NotNull Icon createLayered(Icon @NotNull ... icons) {
    return new DummyRowIcon(icons);
  }

  @Override
  public @NotNull RowIcon createRowIcon(Icon @NotNull ... icons) {
    return new DummyRowIcon(icons);
  }

  private static class DummyIcon implements ScalableIcon {
    static final DummyIcon INSTANCE = new DummyIcon("<DummyIcon>");
    private final String path;

    private DummyIcon(@NotNull String path) {
      this.path = path;
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
      return path.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return this == obj || (obj instanceof DummyIcon && ((DummyIcon)obj).path == path);
    }

    @Override
    public String toString() {
      return path;
    }

    @Override
    public float getScale() {
      return 1;
    }

    @Override
    public @NotNull Icon scale(float scaleFactor) {
      return this;
    }
  }

  private static final class DummyRowIcon extends DummyIcon implements RowIcon {
    private Icon[] icons;

    DummyRowIcon(int iconCount) {
      super("<DummyRowIcon>");
      icons = new Icon[iconCount];
    }

    DummyRowIcon(Icon[] icons) {
      super("<DummyRowIcon>");
      this.icons = icons;
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
    public @NotNull Icon getDarkIcon(boolean isDark) {
      return this;
    }

    @Override
    public Icon @NotNull [] getAllIcons() {
      List<Icon> list = new ArrayList<>();
      for (Icon element : icons) {
        if (element != null) {
          list.add(element);
        }
      }
      return list.toArray(new Icon[0]);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      return o instanceof DummyRowIcon && Arrays.equals(icons, ((DummyRowIcon)o).icons);
    }

    @Override
    public int hashCode() {
      return icons.length > 0 ? icons[0].hashCode() : 0;
    }

    @Override
    public String toString() {
      return "Row icon. myIcons=" + Arrays.asList(icons);
    }
  }
}
