// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.WindowWrapper.Mode;
import com.intellij.openapi.util.BooleanGetter;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.mac.touchbar.TouchbarSupport;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.List;

public final class WindowWrapperBuilder {
  private final @NotNull Mode myMode;
  private final @NotNull JComponent myComponent;
  private @Nullable Project myProject;
  private @Nullable Component myParent;
  private @Nullable @NlsContexts.DialogTitle String title;
  private @Nullable Computable<JComponent> myPreferredFocusedComponent;
  private @NonNls @Nullable String myDimensionServiceKey;
  private @Nullable Runnable myOnShowCallback;
  private @Nullable BooleanGetter myOnCloseHandler;

  public WindowWrapperBuilder(@NotNull Mode mode, @NotNull JComponent component) {
    myMode = mode;
    myComponent = component;
  }

  public @NotNull WindowWrapperBuilder setProject(@Nullable Project project) {
    myProject = project;
    return this;
  }

  public @NotNull WindowWrapperBuilder setParent(@Nullable Component parent) {
    myParent = parent;
    return this;
  }

  public @NotNull WindowWrapperBuilder setTitle(@NlsContexts.DialogTitle @Nullable String title) {
    this.title = title;
    return this;
  }

  public @NotNull WindowWrapperBuilder setPreferredFocusedComponent(@Nullable JComponent preferredFocusedComponent) {
    myPreferredFocusedComponent = new Computable.PredefinedValueComputable<>(preferredFocusedComponent);
    return this;
  }

  public @NotNull WindowWrapperBuilder setPreferredFocusedComponent(@Nullable Computable<JComponent> computable) {
    myPreferredFocusedComponent = computable;
    return this;
  }

  public @NotNull WindowWrapperBuilder setDimensionServiceKey(@NonNls @Nullable String dimensionServiceKey) {
    myDimensionServiceKey = dimensionServiceKey;
    return this;
  }

  public @NotNull WindowWrapperBuilder setOnShowCallback(@NotNull Runnable callback) {
    myOnShowCallback = callback;
    return this;
  }

  public @NotNull WindowWrapperBuilder setOnCloseHandler(@NotNull BooleanGetter handler) {
    myOnCloseHandler = handler;
    return this;
  }

  public @NotNull WindowWrapper build() {
    return switch (myMode) {
      case FRAME -> new FrameWindowWrapper(this);
      case MODAL, NON_MODAL -> new DialogWindowWrapper(this);
    };
  }

  private static void installOnShowCallback(@Nullable Window window, final @Nullable Runnable onShowCallback) {
    if (window == null || onShowCallback == null) return;
    UIUtil.runWhenWindowOpened(window, onShowCallback);
  }

  private static final class DialogWindowWrapper implements WindowWrapper {
    private final @Nullable Project myProject;
    private final @NotNull JComponent myComponent;
    private final @NotNull Mode myMode;

    private final @NotNull MyDialogWrapper myDialog;

    DialogWindowWrapper(final @NotNull WindowWrapperBuilder builder) {
      myProject = builder.myProject;
      myComponent = builder.myComponent;
      myMode = builder.myMode;

      myDialog = builder.myParent != null
                 ? new MyDialogWrapper(builder.myParent, builder.myComponent)
                 : new MyDialogWrapper(builder.myProject, builder.myComponent);
      myDialog.setParameters(builder.myDimensionServiceKey, builder.myPreferredFocusedComponent, builder.myOnCloseHandler);

      installOnShowCallback(myDialog.getWindow(), builder.myOnShowCallback);

      setTitle(builder.title);
      switch (builder.myMode) {
        case MODAL -> myDialog.setModal(true);
        case NON_MODAL -> myDialog.setModal(false);
        default -> {
          assert false;
        }
      }
      myDialog.init();
      Disposer.register(myDialog.getDisposable(), this);
    }

    @Override
    public void dispose() {
      Disposer.dispose(myDialog.getDisposable());
    }

    @Override
    public void show() {
      myDialog.show();
    }

    @Override
    public @Nullable Project getProject() {
      return myProject;
    }

    @Override
    public @NotNull JComponent getComponent() {
      return myComponent;
    }

    @Override
    public @NotNull Mode getMode() {
      return myMode;
    }

    @Override
    public @NotNull Window getWindow() {
      return myDialog.getWindow();
    }

    @Override
    public boolean isDisposed() {
      return myDialog.isDisposed();
    }

    @Override
    public void setTitle(@Nullable String title) {
      myDialog.setTitle(StringUtil.notNullize(title));
    }

    @Override
    public void setImages(@Nullable List<? extends Image> images) {
    }

    @Override
    public void close() {
      myDialog.close(DialogWrapper.CANCEL_EXIT_CODE);
    }

    private static final class MyDialogWrapper extends DialogWrapper {
      private final @NotNull JComponent myComponent;
      private @Nullable @NonNls String myDimensionServiceKey;
      private @Nullable Computable<? extends JComponent> myPreferredFocusedComponent;
      private @Nullable BooleanGetter myOnCloseHandler;

      MyDialogWrapper(@Nullable Project project, @NotNull JComponent component) {
        super(project, true);
        myComponent = component;
      }

      MyDialogWrapper(@NotNull Component parent, @NotNull JComponent component) {
        super(parent, true);
        myComponent = component;
      }

      @Override
      public void init() {
        super.init();
      }

      public void setParameters(@Nullable @NonNls String dimensionServiceKey,
                                @Nullable Computable<? extends JComponent> preferredFocusedComponent,
                                @Nullable BooleanGetter onCloseHandler) {
        myDimensionServiceKey = dimensionServiceKey;
        myPreferredFocusedComponent = preferredFocusedComponent;
        myOnCloseHandler = onCloseHandler;
      }

      @Override
      protected @Nullable Border createContentPaneBorder() {
        return null;
      }

      @Override
      protected JComponent createCenterPanel() {
        return myComponent;
      }

      // it is information dialog - no need to OK or Cancel. Close the dialog by clicking the cross button or pressing Esc.
      @Override
      protected Action @NotNull [] createActions() {
        return new Action[0];
      }

      @Override
      protected @Nullable JComponent createSouthPanel() {
        return null;
      }

      @Override
      protected @Nullable String getDimensionServiceKey() {
        return myDimensionServiceKey;
      }

      @Override
      public @Nullable JComponent getPreferredFocusedComponent() {
        if (myPreferredFocusedComponent != null) return myPreferredFocusedComponent.compute();
        return super.getPreferredFocusedComponent();
      }

      @Override
      public void doCancelAction() {
        if (myOnCloseHandler != null && !myOnCloseHandler.get()) return;
        super.doCancelAction();
      }
    }
  }

  private static final class FrameWindowWrapper implements WindowWrapper {
    private final @Nullable Project myProject;
    private final @NotNull JComponent myComponent;
    private final @NotNull Mode myMode;
    private final @Nullable Runnable myOnShowCallback;

    private final @NotNull MyFrameWrapper myFrame;

    FrameWindowWrapper(@NotNull WindowWrapperBuilder builder) {
      assert builder.myMode == Mode.FRAME;

      myProject = builder.myProject;
      myComponent = builder.myComponent;
      myMode = builder.myMode;

      myFrame = new MyFrameWrapper(builder.myProject, builder.myDimensionServiceKey);
      myFrame.setParameters(builder.myPreferredFocusedComponent);
      myFrame.setOnCloseHandler(builder.myOnCloseHandler);

      myOnShowCallback = builder.myOnShowCallback;

      myFrame.setComponent(builder.myComponent);
      myFrame.setTitle(builder.title == null ? "" : builder.title);
      myFrame.closeOnEsc();
      Disposer.register(myFrame, this);
    }

    @Override
    public void show() {
      TouchbarSupport.showWindowActions(myFrame, myComponent);
      myFrame.show();
      if (myOnShowCallback != null) myOnShowCallback.run();
    }

    @Override
    public @Nullable Project getProject() {
      return myProject;
    }

    @Override
    public @NotNull JComponent getComponent() {
      return myComponent;
    }

    @Override
    public @NotNull Mode getMode() {
      return myMode;
    }

    @Override
    public @NotNull Window getWindow() {
      return myFrame.getFrame();
    }

    @Override
    public boolean isDisposed() {
      return myFrame.isDisposed();
    }

    @Override
    public void setTitle(@Nullable String title) {
      title = StringUtil.notNullize(title);
      myFrame.setTitle(title);

      Window window = getWindow();
      if (window instanceof JFrame) {
        ((JFrame)window).setTitle(title);
      }
      else if (window instanceof JDialog) {
        ((JDialog)window).setTitle(title);
      }
    }

    @Override
    public void setImages(@Nullable List<? extends Image> images) {
      myFrame.setImages(images);
    }

    @Override
    public void close() {
      myFrame.close();
    }

    @Override
    public void dispose() {
      Disposer.dispose(myFrame);
    }

    private static final class MyFrameWrapper extends FrameWrapper {
      private Computable<? extends JComponent> myPreferredFocusedComponent;

      MyFrameWrapper(Project project, @Nullable @NonNls String dimensionServiceKey) {
        super(project, dimensionServiceKey);
      }

      public void setParameters(@Nullable Computable<? extends JComponent> preferredFocusedComponent) {
        myPreferredFocusedComponent = preferredFocusedComponent;
      }

      @Override
      public void dispose() {
        myPreferredFocusedComponent = null;
        super.dispose();
      }

      @Override
      public JComponent getPreferredFocusedComponent() {
        if (myPreferredFocusedComponent != null) return myPreferredFocusedComponent.compute();
        return super.getPreferredFocusedComponent();
      }
    }
  }
}
