package com.intellij.openapi.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.WindowWrapper.Mode;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class WindowWrapperBuilder {
  @NotNull private final Mode myMode;
  @NotNull private final JComponent myComponent;
  @Nullable private Project myProject;
  @Nullable private JComponent myPreferredFocusedComponent;
  @Nullable private String myDimensionServiceKey;
  @Nullable private Component myParent;
  @Nullable private Runnable myOnShowCallback;

  public WindowWrapperBuilder(@NotNull Mode mode, @NotNull JComponent component) {
    myMode = mode;
    myComponent = component;
  }

  @NotNull
  public WindowWrapperBuilder setProject(@Nullable Project project) {
    myProject = project;
    return this;
  }

  @NotNull
  public WindowWrapperBuilder setParent(@Nullable Component parent) {
    myParent = parent;
    return this;
  }

  @NotNull
  public WindowWrapperBuilder setPreferredFocusedComponent(@Nullable JComponent preferredFocusedComponent) {
    myPreferredFocusedComponent = preferredFocusedComponent;
    return this;
  }

  @NotNull
  public WindowWrapperBuilder setDimensionServiceKey(@Nullable String dimensionServiceKey) {
    myDimensionServiceKey = dimensionServiceKey;
    return this;
  }

  @NotNull
  public WindowWrapperBuilder setOnShowCallback(@NotNull Runnable callback) {
    myOnShowCallback = callback;
    return this;
  }

  @NotNull
  public WindowWrapper build() {
    switch (myMode) {
      case FRAME:
        return new FrameWindowWrapper(myProject, myComponent, myMode, myParent, myPreferredFocusedComponent, myDimensionServiceKey,
                                      myOnShowCallback);
      case MODAL:
      case NON_MODAL:
        return new DialogWindowWrapper(myProject, myComponent, myMode, myParent, myPreferredFocusedComponent, myDimensionServiceKey,
                                       myOnShowCallback);
      default:
        throw new IllegalArgumentException(myMode.toString());
    }
  }

  private static class DialogWindowWrapper implements WindowWrapper {
    @Nullable private final Project myProject;
    @NotNull private final JComponent myComponent;
    @NotNull private final Mode myMode;

    @NotNull private final DialogWrapper myDialog;

    public DialogWindowWrapper(@Nullable Project project,
                               @NotNull JComponent component,
                               @NotNull Mode mode,
                               @Nullable Component parent,
                               @Nullable JComponent preferredFocusedComponent,
                               @Nullable String dimensionServiceKey,
                               @Nullable final Runnable onShowCallback) {
      myProject = project;
      myComponent = component;
      myMode = mode;

      if (parent != null) {
        myDialog = new MyDialogWrapper(parent, component, dimensionServiceKey, preferredFocusedComponent);
      }
      else {
        myDialog = new MyDialogWrapper(project, component, dimensionServiceKey, preferredFocusedComponent);
      }

      if (onShowCallback != null) {
        myDialog.getWindow().addWindowListener(new WindowAdapter() {
          @Override
          public void windowOpened(WindowEvent e) {
            onShowCallback.run();
          }
        });
      }

      switch (mode) {
        case MODAL:
          myDialog.setModal(true);
          break;
        case NON_MODAL:
          myDialog.setModal(false);
          break;
        default:
          throw new IllegalArgumentException(mode.toString());
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
      if (title == null) return;
      myDialog.setTitle(title);
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

    public FrameWindowWrapper(@Nullable Project project,
                              @NotNull JComponent component,
                              @NotNull Mode mode,
                              @Nullable Component parent,
                              @Nullable JComponent preferredFocusedComponent,
                              @Nullable String dimensionServiceKey,
                              @Nullable Runnable onShowCallback) {
      myProject = project;
      myComponent = component;
      myMode = mode;
      myOnShowCallback = onShowCallback;

      myFrame = new MyFrameWrapper(this, project, dimensionServiceKey);

      assert mode == Mode.FRAME;

      myFrame.setComponent(component);
      myFrame.setPreferredFocusedComponent(preferredFocusedComponent);
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
      if (title == null) return;
      myFrame.setTitle(title);

      Window window = getWindow();
      if (window instanceof JFrame) ((JFrame)window).setTitle(title);
      if (window instanceof JDialog) ((JDialog)window).setTitle(title);
    }

    @Override
    public void setImage(@Nullable Image image) {
      if (image == null) return;
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

    private static class MyFrameWrapper extends FrameWrapper {
      @NotNull private final WindowWrapper myWindowWrapper;

      public MyFrameWrapper(@NotNull WindowWrapper wrapper, @Nullable Project project, @Nullable @NonNls String dimensionServiceKey) {
        super(project, dimensionServiceKey);
        myWindowWrapper = wrapper;
      }
    }
  }
}
