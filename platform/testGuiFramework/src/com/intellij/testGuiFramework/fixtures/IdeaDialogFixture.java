// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Ref;
import com.intellij.testGuiFramework.framework.GuiTestUtil;
import org.fest.reflect.exception.ReflectionError;
import org.fest.reflect.reference.TypeRef;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.ContainerFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.ref.WeakReference;

import static junit.framework.Assert.assertNotNull;
import static org.fest.reflect.core.Reflection.field;

public abstract class IdeaDialogFixture<T extends DialogWrapper> extends ComponentFixture<IdeaDialogFixture, JDialog> implements ContainerFixture<JDialog> {
  @NotNull private final T myDialogWrapper;

  @Nullable
  protected static <T extends DialogWrapper> T getDialogWrapperFrom(@NotNull JDialog dialog, Class<T> dialogWrapperType) {
    try {
      WeakReference<DialogWrapper> dialogWrapperRef = field("myDialogWrapper").ofType(new TypeRef<WeakReference<DialogWrapper>>() {})
        .in(dialog)
        .get();
      assertNotNull(dialogWrapperRef);
      DialogWrapper wrapper = dialogWrapperRef.get();
      if (dialogWrapperType.isInstance(wrapper)) {
        return dialogWrapperType.cast(wrapper);
      }
    }
    catch (ReflectionError ignored) {
    }
    return null;
  }

  public static class DialogAndWrapper<T extends DialogWrapper> {
    public final JDialog dialog;
    public final T wrapper;

    public DialogAndWrapper(@NotNull JDialog dialog, @NotNull T wrapper) {
      this.dialog = dialog;
      this.wrapper = wrapper;
    }
  }

  @NotNull
  public static <T extends DialogWrapper> DialogAndWrapper<T> find(@NotNull Robot robot, @NotNull final Class<? extends T> clz) {
    return find(robot, clz, new GenericTypeMatcher<>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog component) {
        return component.isShowing();
      }
    });
  }

  @NotNull
  public static <T extends DialogWrapper> DialogAndWrapper<T> find(@NotNull Robot robot, @NotNull final Class<? extends T> clz,
                                                                   @NotNull final GenericTypeMatcher<JDialog> matcher) {
    final Ref<T> wrapperRef = new Ref<>();
    JDialog dialog = GuiTestUtil.INSTANCE.waitUntilFound(robot, new GenericTypeMatcher<>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        if (matcher.matches(dialog)) {
          T wrapper = getDialogWrapperFrom(dialog, clz);
          if (wrapper != null) {
            wrapperRef.set(wrapper);
            return true;
          }
        }
        return false;
      }
    });
    return new DialogAndWrapper<>(dialog, wrapperRef.get());
  }

  protected IdeaDialogFixture(@NotNull Robot robot, @NotNull JDialog target, @NotNull T dialogWrapper) {
    super(IdeaDialogFixture.class, robot, target);
    myDialogWrapper = dialogWrapper;
  }

  protected IdeaDialogFixture(@NotNull Robot robot, @NotNull DialogAndWrapper<T> dialogAndWrapper) {
    this(robot, dialogAndWrapper.dialog, dialogAndWrapper.wrapper);
  }

  @NotNull
  protected T getDialogWrapper() {
    return myDialogWrapper;
  }

  public void clickCancel() {
    // Grab focus in case it is not automatically done by the window manager, e.g. 9wm
    focus();

    GuiTestUtil.INSTANCE.findAndClickCancelButton(this);
  }

  public void close() {
    robot().close(target());
  }
}
