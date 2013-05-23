package org.hanuna.gitalk.swing_ui;

import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.swing_ui.frame.ErrorModalDialog;
import org.hanuna.gitalk.swing_ui.frame.MainFrame;
import org.hanuna.gitalk.swing_ui.frame.ProgressFrame;
import org.hanuna.gitalk.ui.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * @author erokhins
 */
public class Swing_UI {
  private final ErrorModalDialog errorFrame = new ErrorModalDialog("error");
  private final ProgressFrame progressFrame = new ProgressFrame("Begin load");
  private final ControllerListener swingControllerListener = new SwingControllerListener();
  private final DragDropListener myDragDropListener = new SwingDragDropListener();
  private final UI_Controller ui_controller;
  private final DragDropConditions myConditions = new DragDropConditions();
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

  private class DragDropConditions {

    public boolean sameBranch(Node commit, List<Node> commitsBeingDragged) {
      return true; // TODO
    }
  }

  interface Action {
    void hint(Node commit, MouseEvent e, List<Node> commitsBeingDragged);
    void perform(Node commit, MouseEvent e, List<Node> commitsBeingDragged);
  }

  private class SwingDragDropListener extends DragDropListener {

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

    private final Action NONE = new Action() {
      @Override
      public void hint(Node commit, MouseEvent e, List<Node> commitsBeingDragged) {
      }

      @Override
      public void perform(Node commit, MouseEvent e, List<Node> commitsBeingDragged) {
      }
    };

    private final Action MOVE = new Action() {

      @Override
      public void hint(Node commit, MouseEvent e, List<Node> commitsBeingDragged) {
        showHint(e, GitLogIcons.MOVE, "Move " + renderCommits(commitsBeingDragged));
      }

      @Override
      public void perform(Node commit, MouseEvent e, List<Node> commitsBeingDragged) {

      }
    };

    private final Action PICK = new Action() {

      @Override
      public void hint(Node commit, MouseEvent e, List<Node> commitsBeingDragged) {
        showHint(e, GitLogIcons.CHERRY_PICK, "Cherry-pick " + renderCommits(commitsBeingDragged));
      }

      @Override
      public void perform(Node commit, MouseEvent e, List<Node> commitsBeingDragged) {

      }
    };

    private final Action REBASE = new Action() {

      @Override
      public void hint(Node commit, MouseEvent e, List<Node> commitsBeingDragged) {
        showHint(e, GitLogIcons.REBASE, "Rebase " + renderCommits(commitsBeingDragged));
      }

      @Override
      public void perform(Node commit, MouseEvent e, List<Node> commitsBeingDragged) {

      }
    };

    private final Action FORBIDDEN = new Action() {

      @Override
      public void hint(Node commit, MouseEvent e, List<Node> commitsBeingDragged) {
        showHint(e, GitLogIcons.FORBIDDEN, "No action possible");
      }

      @Override
      public void perform(Node commit, MouseEvent e, List<Node> commitsBeingDragged) {
        // Do nothing
      }
    };

    private CharSequence renderCommits(List<Node> commitsBeingDragged) {
      if (commitsBeingDragged.size() == 1) {
        Node node = commitsBeingDragged.get(0);
        String message = ui_controller.getDataPack().getCommitDataGetter().getCommitData(node).getMessage();
        return "\"" + message + "\"";
      }

      return commitsBeingDragged.size() + " commits";
    }

    private final ActionHandler actionHandler = new ActionHandler();

    private class ActionHandler extends Handler implements Action {
      private Action currentAction = NONE;

      @Override
      public void above(int rowIndex, Node commit, MouseEvent e, List<Node> commitsBeingDragged) {
        if (myConditions.sameBranch(commit, commitsBeingDragged)) {
          currentAction = MOVE;
        }
        else {
          currentAction = FORBIDDEN;
        }
      }

      @Override
      public void below(int rowIndex, Node commit, MouseEvent e, List<Node> commitsBeingDragged) {
        if (myConditions.sameBranch(commit, commitsBeingDragged)) {
          currentAction = MOVE;
        }
        else {
          currentAction = FORBIDDEN;
        }
      }

      @Override
      public void over(int rowIndex, Node commit, MouseEvent e, List<Node> commitsBeingDragged) {
        // cherry pick (different branches)
        // rebase (different branches + label on top)
        // nothing otherwise
        currentAction = REBASE;
      }

      @Override
      public void overNode(int rowIndex, Node commit, MouseEvent e, List<Node> commitsBeingDragged) {
        // fixup (same branch)
        // nothing otherwise
        currentAction = FORBIDDEN;
      }

      @Override
      public void hint(Node commit, MouseEvent e, List<Node> commitsBeingDragged) {
        currentAction.hint(commit, e, commitsBeingDragged);
      }

      @Override
      public void perform(Node commit, MouseEvent e, List<Node> commitsBeingDragged) {
        currentAction.perform(commit, e, commitsBeingDragged);
        dragDropHint.hide();
      }
    }

    @Override
    public Handler drag() {
      return new Handler() {
        @Override
        public void above(int rowIndex, Node commit, MouseEvent e, List<Node> commitsBeingDragged) {
          actionHandler.above(rowIndex, commit, e, commitsBeingDragged);
          actionHandler.hint(commit, e, commitsBeingDragged);
        }

        @Override
        public void below(int rowIndex, Node commit, MouseEvent e, List<Node> commitsBeingDragged) {
          actionHandler.below(rowIndex, commit, e, commitsBeingDragged);
          actionHandler.hint(commit, e, commitsBeingDragged);
        }

        @Override
        public void over(int rowIndex, Node commit, MouseEvent e, List<Node> commitsBeingDragged) {
          actionHandler.over(rowIndex, commit, e, commitsBeingDragged);
          actionHandler.hint(commit, e, commitsBeingDragged);
        }

        @Override
        public void overNode(int rowIndex, Node commit, MouseEvent e, List<Node> commitsBeingDragged) {
          actionHandler.overNode(rowIndex, commit, e, commitsBeingDragged);
          actionHandler.hint(commit, e, commitsBeingDragged);
        }
      };
    }

    private void showForbidden(MouseEvent e) {
      showHint(e, GitLogIcons.FORBIDDEN, "No action possible");
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
        public void above(int rowIndex, Node commit, MouseEvent e, List<Node> commitsBeingDragged) {
          actionHandler.above(rowIndex, commit, e, commitsBeingDragged);
          actionHandler.perform(commit, e, commitsBeingDragged);
        }

        @Override
        public void below(int rowIndex, Node commit, MouseEvent e, List<Node> commitsBeingDragged) {
          actionHandler.below(rowIndex, commit, e, commitsBeingDragged);
          actionHandler.perform(commit, e, commitsBeingDragged);
        }

        @Override
        public void over(int rowIndex, Node commit, MouseEvent e, List<Node> commitsBeingDragged) {
          actionHandler.over(rowIndex, commit, e, commitsBeingDragged);
          actionHandler.perform(commit, e, commitsBeingDragged);
        }

        @Override
        public void overNode(int rowIndex, Node commit, MouseEvent e, List<Node> commitsBeingDragged) {
          actionHandler.overNode(rowIndex, commit, e, commitsBeingDragged);
          actionHandler.perform(commit, e, commitsBeingDragged);
        }
      };
    }

    @Override
    public void draggingStarted(List<Node> commitsBeingDragged) {
      super.draggingStarted(commitsBeingDragged); // TODO
    }

    @Override
    public void draggingCanceled(List<Node> commitsBeingDragged) {
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
