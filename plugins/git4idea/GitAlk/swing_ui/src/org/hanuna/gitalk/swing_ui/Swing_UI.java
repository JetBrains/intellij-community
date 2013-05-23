package org.hanuna.gitalk.swing_ui;

import com.intellij.icons.AllIcons;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;
import org.hanuna.gitalk.commit.Hash;
import org.hanuna.gitalk.swing_ui.frame.ErrorModalDialog;
import org.hanuna.gitalk.swing_ui.frame.MainFrame;
import org.hanuna.gitalk.swing_ui.frame.ProgressFrame;
import org.hanuna.gitalk.ui.ControllerListener;
import org.hanuna.gitalk.ui.DragDropListener;
import org.hanuna.gitalk.ui.UI_Controller;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author erokhins
 */
public class Swing_UI {
  private final ErrorModalDialog errorFrame = new ErrorModalDialog("error");
  private final ProgressFrame progressFrame = new ProgressFrame("Begin load");
  private final ControllerListener swingControllerListener = new SwingControllerListener();
  private final DragDropListener myDragDropListener = new SwingDragDropListener();
  private final UI_Controller ui_controller;
  private MainFrame mainFrame = null;


  public MainFrame getMainFrame() {
    return mainFrame;
  }

  public Swing_UI(UI_Controller ui_controller) {
    this.ui_controller = ui_controller;
  }


  public ControllerListener getControllerListener() {
    return swingControllerListener;
  }

  public void setState(ControllerListener.State state) {
    switch (state) {
      case USUAL:
        if (mainFrame == null) {
          mainFrame = new MainFrame(ui_controller);
        }
        errorFrame.setVisible(false);
        progressFrame.setVisible(false);
        break;
      case ERROR:
        errorFrame.setVisible(true);
        break;
      case PROGRESS:
        progressFrame.setVisible(true);
        break;
      default:
        throw new IllegalArgumentException();
    }
  }

  public DragDropListener getDragDropListener() {
    return myDragDropListener;
  }

  private static class SwingDragDropListener extends DragDropListener {
    private final JLabel hintLabel = new JLabel("") {
      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D)g;

        float red = 255 / 255f;
        float green = 246 / 255f;
        float blue = 181 / 255f;
        g2d.setPaint(new LinearGradientPaint(0, getHeight() / 2, getWidth(), getHeight() / 2,
                                             new float[] {0f, .3f, .75f, 1f},
                                             new Color[] {
                                               new Color(red, green, blue, 0f),
                                               new Color(red, green, blue, .5f),
                                               new Color(red, green, blue, 1f),
                                               new Color(red, green, blue, 0f)
                                             }
        ));

        g2d.fillRect(0, 0, getWidth(), getHeight());
        super.paintComponent(g);
      }
    };
    private final LightweightHint dragDropHint = new LightweightHint(hintLabel);

    @Override
    public Handler drag() {
      return new Handler() {
        @Override
        public void above(int rowIndex, Hash commit, MouseEvent e, Hash commitBeingDragged) {
          showHint(e, GitLogIcons.CHERRY_PICK, "Above " + commit);
        }

        @Override
        public void below(int rowIndex, Hash commit, MouseEvent e, Hash commitBeingDragged) {
          showHint(e, GitLogIcons.REBASE, "Below " + commit);
        }

        @Override
        public void over(int rowIndex, Hash commit, MouseEvent e, Hash commitBeingDragged) {
          showHint(e, GitLogIcons.REBASE_INTERACTIVE, "Over " + commit);
        }

        @Override
        public void overNode(int rowIndex, Hash commit, MouseEvent e, Hash commitBeingDragged) {
          showHint(e, AllIcons.Icon_CE, "Over Node " + commit);
        }
      };
    }

    private void showHint(MouseEvent e, Icon icon, String text) {
      dragDropHint.hide();
      hintLabel.setIcon(icon);
      hintLabel.setText(text + "        ");
      dragDropHint.show((JComponent)e.getComponent(), e.getX() + 8, e.getY() + 8, null, new HintHint(e));
    }

    @Override
    public Handler drop() {
      return new Handler() {
        @Override
        public void above(int rowIndex, Hash commit, MouseEvent e, Hash commitBeingDragged) {
          dragDropHint.hide();
        }

        @Override
        public void below(int rowIndex, Hash commit, MouseEvent e, Hash commitBeingDragged) {
          dragDropHint.hide();
        }

        @Override
        public void over(int rowIndex, Hash commit, MouseEvent e, Hash commitBeingDragged) {
          dragDropHint.hide();
        }

        @Override
        public void overNode(int rowIndex, Hash commit, MouseEvent e, Hash commitBeingDragged) {
          dragDropHint.hide();
        }
      };
    }

    @Override
    public void draggingStarted(Hash commitBeingDragged) {
      super.draggingStarted(commitBeingDragged); // TODO
    }

    @Override
    public void draggingCanceled(Hash commitBeingDragged) {
      dragDropHint.hide();
    }
  }

  private class SwingControllerListener implements ControllerListener {

    @Override
    public void jumpToRow(final int rowIndex) {
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          mainFrame.getGraphTable().jumpToRow(rowIndex);
          ui_controller.click(rowIndex);
        }
      });
    }

    @Override
    public void updateUI() {
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          mainFrame.getGraphTable().updateUI();
        }
      });
    }

    @Override
    public void setState(@NotNull final State state) {
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          Swing_UI.this.setState(state);
        }
      });
    }

    @Override
    public void setErrorMessage(@NotNull final String errorMessage) {
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          errorFrame.setMessage(errorMessage);
        }
      });
    }

    @Override
    public void setUpdateProgressMessage(@NotNull final String progressMessage) {
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          progressFrame.setMessage(progressMessage);
        }
      });
    }
  }
}
