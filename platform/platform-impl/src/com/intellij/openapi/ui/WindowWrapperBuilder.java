package com.intellij.openapi.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.WindowWrapper.Mode;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class WindowWrapperBuilder {
  @NotNull private final Mode mode;
  @NotNull private final JComponent component;
  @Nullable private Project project;
  @Nullable private Component parent;
  @Nullable private String title;
  @Nullable private JComponent preferredFocusedComponent;
  @Nullable private String dimensionServiceKey;
  @Nullable private Runnable onShowCallback;

  public WindowWrapperBuilder(@NotNull Mode mode, @NotNull JComponent component) {
    this.mode = mode;
    this.component = component;
  }

  @NotNull
  public WindowWrapperBuilder setProject(@Nullable Project project) {
    this.project = project;
    return this;
  }

  @NotNull
  public WindowWrapperBuilder setParent(@Nullable Component parent) {
    this.parent = parent;
    return this;
  }

  @NotNull
  public WindowWrapperBuilder setTitle(@Nullable String title) {
    this.title = title;
    return this;
  }

  @NotNull
  public WindowWrapperBuilder setPreferredFocusedComponent(@Nullable JComponent preferredFocusedComponent) {
    this.preferredFocusedComponent = preferredFocusedComponent;
    return this;
  }

  @NotNull
  public WindowWrapperBuilder setDimensionServiceKey(@Nullable String dimensionServiceKey) {
    this.dimensionServiceKey = dimensionServiceKey;
    return this;
  }

  @NotNull
  public WindowWrapperBuilder setOnShowCallback(@NotNull Runnable callback) {
    this.onShowCallback = callback;
    return this;
  }

  @NotNull
  public WindowWrapper build() {
    switch (mode) {
      case FRAME:
        return new FrameWindowWrapper(this);
      case MODAL:
      case NON_MODAL:
        return new DialogWindowWrapper(this);
      default:
        throw new IllegalArgumentException(mode.toString());
    }
  }

  private static class DialogWindowWrapper implements WindowWrapper {
    @Nullable private final Project myProject;
    @NotNull private final JComponent myComponent;
    @NotNull private final Mode myMode;

    @NotNull private final DialogWrapper myDialog;

    public DialogWindowWrapper(@NotNull final WindowWrapperBuilder builder) {
      myProject = builder.project;
      myComponent = builder.component;
      myMode = builder.mode;

      if (builder.parent != null) {
        myDialog = new MyDialogWrapper(builder.parent, builder.component, builder.dimensionServiceKey, builder.preferredFocusedComponent);
      }
      else {
        myDialog = new MyDialogWrapper(builder.project, builder.component, builder.dimensionServiceKey, builder.preferredFocusedComponent);
      }

      final Runnable onShowCallback = builder.onShowCallback;
      if (onShowCallback != null) {
        myDialog.getWindow().addWindowListener(new WindowAdapter() {
          @Override
          public void windowOpened(WindowEvent e) {
            onShowCallback.run();
          }
        });
      }

      setTitle(builder.title);
      switch (builder.mode) {
        case MODAL:
          myDialog.setModal(true);
          break;
        case NON_MODAL:
          myDialog.setModal(false);
          break;
        default:
          throw new IllegalArgumentException(builder.mode.toString());
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

    @Nullable
    @Override
    public Project getProject() {
      return myProject;
    }

    @NotNull
    @Override
    public JComponent getComponent() {
      return myComponent;
    }

    @NotNull
    @Override
    public Mode getMode() {
      return myMode;
    }

    @NotNull
    @Override
    public Window getWindow() {
      return myDialog.getWindow();
    }

    @Override
    public void setTitle(@Nullable String title) {
      myDialog.setTitle(StringUtil.notNullize(title));
    }

    @Override
    public void setImage(@Nullable Image image) {
    }

    @Override
    public void close() {
      myDialog.close(DialogWrapper.CANCEL_EXIT_CODE);
    }

    private static class MyDialogWrapper extends DialogWrapper {
      @NotNull private final JComponent myComponent;
      @Nullable private final String myDimensionServiceKey;
      @Nullable private final JComponent myPreferredFocusedComponent;

      public MyDialogWrapper(@Nullable Project project,
                             @NotNull JComponent component,
                             @Nullable String dimensionServiceKey,
                             @Nullable JComponent preferredFocusedComponent) {
        super(project, true);
        myComponent = component;
        myDimensionServiceKey = dimensionServiceKey;
        myPreferredFocusedComponent = preferredFocusedComponent;
      }

      public MyDialogWrapper(@NotNull Component parent,
                             @NotNull JComponent component,
                             @Nullable String dimensionServiceKey,
                             @Nullable JComponent preferredFocusedComponent) {
        super(parent, true);
        myComponent = component;
        myDimensionServiceKey = dimensionServiceKey;
        myPreferredFocusedComponent = preferredFocusedComponent;
      }

      @Nullable
      @Override
      protected Border createContentPaneBorder() {
        return null;
      }

      @Override
      protected JComponent createCenterPanel() {
        return myComponent;
      }

      // it is information dialog - no need to OK or Cancel. Close the dialog by clicking the cross button or pressing Esc.
      @NotNull
      @Override
      protected Action[] createActions() {
        return new Action[0];
      }

      @Nullable
      @Override
      protected JComponent createSouthPanel() {
        return null;
      }

      @Nullable
      @Override
      protected String getDimensionServiceKey() {
        return myDimensionServiceKey;
      }

      @Nullable
      @Override
      public JComponent getPreferredFocusedComponent() {
        return myPreferredFocusedComponent;
      }
    }
  }

  private static class FrameWindowWrapper implements WindowWrapper {
    @Nullable private final Project myProject;
    @NotNull private final JComponent myComponent;
    @NotNull private final Mode myMode;
    @Nullable private final Runnable myOnShowCallback;

    @NotNull private final FrameWrapper myFrame;

    public FrameWindowWrapper(@NotNull WindowWrapperBuilder builder) {
      myProject = builder.project;
      myComponent = builder.component;
      myMode = builder.mode;
      myOnShowCallback = builder.onShowCallback;

      myFrame = new FrameWrapper(builder.project, builder.dimensionServiceKey);

      assert builder.mode == Mode.FRAME;

      myFrame.setComponent(builder.component);
      myFrame.setPreferredFocusedComponent(builder.preferredFocusedComponent);
      myFrame.setTitle(builder.title);
      myFrame.closeOnEsc();
      Disposer.register(myFrame, this);
    }

    @Override
    public void show() {
      myFrame.show();
      if (myOnShowCallback != null) myOnShowCallback.run();
    }

    @Nullable
    @Override
    public Project getProject() {
      return myProject;
    }

    @NotNull
    @Override
    public JComponent getComponent() {
      return myComponent;
    }

    @NotNull
    @Override
    public Mode getMode() {
      return myMode;
    }

    @NotNull
    @Override
    public Window getWindow() {
      return myFrame.getFrame();
    }

    @Override
    public void setTitle(@Nullable String title) {
      title = StringUtil.notNullize(title);
      myFrame.setTitle(title);

      Window window = getWindow();
      if (window instanceof JFrame) ((JFrame)window).setTitle(title);
      if (window instanceof JDialog) ((JDialog)window).setTitle(title);
    }

    @Override
    public void setImage(@Nullable Image image) {
      myFrame.setImage(image);
    }

    @Override
    public void close() {
      myFrame.close();
    }

    @Override
    public void dispose() {
      Disposer.dispose(myFrame);
    }
  }
}
