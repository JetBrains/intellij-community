package com.intellij.openapi.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.WindowWrapper.Mode;
import com.intellij.openapi.util.BooleanGetter;
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
  @NotNull private final Mode myMode;
  @NotNull private final JComponent myComponent;
  @Nullable private Project myProject;
  @Nullable private Component myParent;
  @Nullable private String myTitle;
  @Nullable private JComponent myPreferredFocusedComponent;
  @Nullable private String myDimensionServiceKey;
  @Nullable private Runnable myOnShowCallback;
  @Nullable private BooleanGetter myOkAction;
  @Nullable private BooleanGetter myCancelAction;

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
  public WindowWrapperBuilder setTitle(@Nullable String title) {
    myTitle = title;
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
  public WindowWrapperBuilder setOkAction(@Nullable BooleanGetter value) {
    myOkAction = value;
    return this;
  }

  @NotNull
  public WindowWrapperBuilder setCancelAction(@Nullable BooleanGetter value) {
    myCancelAction = value;
    return this;
  }

  @NotNull
  public WindowWrapper build() {
    switch (myMode) {
      case FRAME:
        return new FrameWindowWrapper(this);
      case MODAL:
      case NON_MODAL:
        return new DialogWindowWrapper(this);
      default:
        throw new IllegalArgumentException(myMode.toString());
    }
  }

  private static class DialogWindowWrapper implements WindowWrapper {
    @Nullable private final Project myProject;
    @NotNull private final JComponent myComponent;
    @NotNull private final Mode myMode;

    @NotNull private final MyDialogWrapper myDialog;

    public DialogWindowWrapper(@NotNull final WindowWrapperBuilder builder) {
      myProject = builder.myProject;
      myComponent = builder.myComponent;
      myMode = builder.myMode;

      myDialog = builder.myParent != null
                 ? new MyDialogWrapper(builder.myParent, builder.myComponent)
                 : new MyDialogWrapper(builder.myProject, builder.myComponent);
      myDialog.setParameters(builder.myDimensionServiceKey,
                             builder.myPreferredFocusedComponent,
                             builder.myOkAction,
                             builder.myCancelAction);

      final Runnable onShowCallback = builder.myOnShowCallback;
      if (onShowCallback != null) {
        myDialog.getWindow().addWindowListener(new WindowAdapter() {
          @Override
          public void windowOpened(WindowEvent e) {
            onShowCallback.run();
          }
        });
      }

      setTitle(builder.myTitle);
      switch (builder.myMode) {
        case MODAL:
          myDialog.setModal(true);
          break;
        case NON_MODAL:
          myDialog.setModal(false);
          break;
        default:
          assert false;
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
      myDialog.getCancelAction().actionPerformed(null);
    }

    private static class MyDialogWrapper extends DialogWrapper {
      @NotNull private JComponent myComponent;
      @Nullable private String myDimensionServiceKey;
      @Nullable private JComponent myPreferredFocusedComponent;
      @Nullable private BooleanGetter myOkAction;
      @Nullable private BooleanGetter myCancelAction;

      public MyDialogWrapper(@Nullable Project project, @NotNull JComponent component) {
        super(project, true);
        myComponent = component;
      }

      public MyDialogWrapper(@NotNull Component parent, @NotNull JComponent component) {
        super(parent, true);
        myComponent = component;
      }

      public void setParameters(@Nullable String dimensionServiceKey,
                                @Nullable JComponent preferredFocusedComponent,
                                @Nullable BooleanGetter okAction,
                                @Nullable BooleanGetter cancelAction) {
        myDimensionServiceKey = dimensionServiceKey;
        myPreferredFocusedComponent = preferredFocusedComponent;
        myOkAction = okAction;
        myCancelAction = cancelAction;
      }

      @Nullable
      @Override
      protected Border createContentPaneBorder() {
        if (myOkAction == null && myCancelAction == null) return null;
        return super.createContentPaneBorder();
      }

      @Override
      protected JComponent createCenterPanel() {
        return myComponent;
      }

      // it is information dialog - no need to OK or Cancel. Close the dialog by clicking the cross button or pressing Esc.
      @NotNull
      @Override
      protected Action[] createActions() {
        if (myOkAction == null && myCancelAction == null) return new Action[0];
        return super.createActions();
      }

      @Override
      protected void doOKAction() {
        if (myOkAction == null || myOkAction.get()) super.doOKAction();
      }

      @Override
      public void doCancelAction() {
        if (myCancelAction == null || myCancelAction.get()) super.doCancelAction();
      }

      @Nullable
      @Override
      protected JComponent createSouthPanel() {
        if (myOkAction == null && myCancelAction == null) return null;
        return super.createSouthPanel();
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
      assert builder.myMode == Mode.FRAME;
      if (builder.myOkAction != null || builder.myCancelAction != null) throw new IllegalArgumentException();

      myProject = builder.myProject;
      myComponent = builder.myComponent;
      myMode = builder.myMode;
      myOnShowCallback = builder.myOnShowCallback;

      myFrame = new FrameWrapper(builder.myProject, builder.myDimensionServiceKey);

      myFrame.setComponent(builder.myComponent);
      myFrame.setPreferredFocusedComponent(builder.myPreferredFocusedComponent);
      myFrame.setTitle(builder.myTitle);
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
