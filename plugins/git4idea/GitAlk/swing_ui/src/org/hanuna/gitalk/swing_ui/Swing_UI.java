package org.hanuna.gitalk.swing_ui;

import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;
import org.hanuna.gitalk.data.rebase.GitActionHandler;
import org.hanuna.gitalk.data.rebase.InteractiveRebaseBuilder;
import org.hanuna.gitalk.data.rebase.RebaseCommand;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.refs.Ref;
import org.hanuna.gitalk.swing_ui.frame.ErrorModalDialog;
import org.hanuna.gitalk.swing_ui.frame.MainFrame;
import org.hanuna.gitalk.swing_ui.frame.ProgressFrame;
import org.hanuna.gitalk.ui.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

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
  private final GitActionHandler.Callback myCallback = new Callback();
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

  private static class Callback implements GitActionHandler.Callback {
    @Override
    public void disableModifications() {

    }

    @Override
    public void enableModifications() {

    }

    @Override
    public void interactiveCommandApplied(RebaseCommand command) {

    }
  }

  private class DragDropConditions {

    public boolean sameBranch(Node commit, List<Node> commitsBeingDragged) {
      for (Node dragged : commitsBeingDragged) {
        if (!ui_controller.getDataPackUtils().isSameBranch(commit, dragged)) return false;
      }
      return true;
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

    private class MoveAction implements Action {

      private final InteractiveRebaseBuilder.InsertPosition myPosition;

      private MoveAction(InteractiveRebaseBuilder.InsertPosition position) {
        myPosition = position;
      }

      @Override
      public void hint(Node commit, MouseEvent e, List<Node> commitsBeingDragged) {
        List<Ref> refsAbove = getRefsAbove(commit);
        showHint(e, GitLogIcons.MOVE, "Move " + renderCommits(commitsBeingDragged) +
                                      (refsAbove.size() == 1 ? " in branch " + refsAbove.get(0).getName() : ""));

        // TODO: highlight branch(es) under cursor
      }

      @Override
      public void perform(final Node commit, MouseEvent e, final List<Node> commitsBeingDragged) {
        runRefAction(commit, e, new RefAction() {
          @Override
          public void perform(Ref ref) {
            ui_controller.getInteractiveRebaseBuilder().moveCommits(ref, commit, myPosition, commitsBeingDragged);
          }
        });
      }
    }

    private final Action MOVE_ABOVE = new MoveAction(InteractiveRebaseBuilder.InsertPosition.ABOVE);

    private final Action MOVE_BELOW = new MoveAction(InteractiveRebaseBuilder.InsertPosition.BELOW);

    private final Action CHERRY_PICK = new Action() {

      @Override
      public void hint(Node commit, MouseEvent e, List<Node> commitsBeingDragged) {
        showHint(e, GitLogIcons.CHERRY_PICK, "Cherry-pick " + renderCommits(commitsBeingDragged));
      }

      @Override
      public void perform(final Node commit, MouseEvent e, final List<Node> commitsBeingDragged) {
        runRefAction(commit, e, new RefAction() {
          @Override
          public void perform(Ref ref) {
            ui_controller.getGitActionHandler().cherryPick(ref, commitsBeingDragged, myCallback);
          }
        });
      }
    };

    private final Action REBASE_WHOLE_BRANCH_ONTO_COMMIT_UNDER_CURSOR = new Action() {

      @Override
      public void hint(Node commit, MouseEvent e, List<Node> commitsBeingDragged) {
        showHint(e, GitLogIcons.REBASE, "Rebase " + renderCommits(commitsBeingDragged));
      }

      @Override
      public void perform(final Node commit, MouseEvent e, List<Node> commitsBeingDragged) {
        showRefPopup(getRefsAbove(commitsBeingDragged.get(0)), e.getComponent(), new RefAction() {
              @Override
              public void perform(Ref ref) {
                ui_controller.getGitActionHandler().rebase(commit, ref, myCallback);
              }
            });
      }
    };

    private final Action REBASE_SOME_COMMITS_ONTO_COMMIT_UNDER_CURSOR = new Action() {

      @Override
      public void hint(Node commit, MouseEvent e, List<Node> commitsBeingDragged) {
        showHint(e, GitLogIcons.REBASE, "Rebase " + renderCommits(commitsBeingDragged) + " interactively");
      }

      @Override
      public void perform(final Node commit, MouseEvent e, final List<Node> commitsBeingDragged) {
        showRefPopup(getRefsAbove(commitsBeingDragged.get(0)), e.getComponent(), new RefAction() {
              @Override
              public void perform(Ref ref) {
                ui_controller.getInteractiveRebaseBuilder().startRebaseOnto(ref, commit, commitsBeingDragged);
              }
            });
      }
    };

    private final Action FIX_UP = new Action() {

      @Override
      public void hint(Node commit, MouseEvent e, List<Node> commitsBeingDragged) {
        showHint(e, GitLogIcons.FIX_UP, "Fix up " + renderCommits(commitsBeingDragged)
                                        + " into " + renderCommits(Collections.singletonList(commit)));
      }

      @Override
      public void perform(final Node commit, MouseEvent e, final List<Node> commitsBeingDragged) {
        runRefAction(commit, e, new RefAction() {
          @Override
          public void perform(Ref ref) {
            ui_controller.getInteractiveRebaseBuilder().fixUp(ref, commit, commitsBeingDragged);
          }
        });
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

    private void runRefAction(Node commit, MouseEvent e, RefAction action) {
      showRefPopup(getRefsAbove(commit), e.getComponent(), action);
    }

    private List<Ref> getRefsAbove(Node commit) {
      Set<Node> upRefNodes = ui_controller.getDataPackUtils().getUpRefNodes(commit);
      List<Ref> refs = new ArrayList<Ref>();
      for (Node refNode : upRefNodes) {
        List<Ref> nodeRefs = ui_controller.getDataPack().getRefsModel().refsToCommit(refNode.getCommitHash());
        for (Ref ref : nodeRefs) {
          if (ref.getType() == Ref.RefType.LOCAL_BRANCH || ref.getType() == Ref.RefType.REMOTE_BRANCH) {
            refs.add(ref);
          }
        }
      }
      return refs;
    }

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

      private Action pickOrRebase(Node commit, MouseEvent e, List<Node> commitsBeingDragged, boolean overCommit) {
        // labelOnTop
        // modifiers
        // multiple selection: sequential selection + rebase modifier -> rebase, otherwise -> pick
        return REBASE_SOME_COMMITS_ONTO_COMMIT_UNDER_CURSOR;
      }

      @Override
      public void above(int rowIndex, Node commit, MouseEvent e, List<Node> commitsBeingDragged) {
        if (myConditions.sameBranch(commit, commitsBeingDragged)) {
          currentAction = MOVE_ABOVE;
        }
        else {
          currentAction = pickOrRebase(commit, e, commitsBeingDragged, false);
        }
      }

      @Override
      public void below(int rowIndex, Node commit, MouseEvent e, List<Node> commitsBeingDragged) {
        if (myConditions.sameBranch(commit, commitsBeingDragged)) {
          currentAction = MOVE_BELOW;
        }
        else {
          currentAction = pickOrRebase(commit, e, commitsBeingDragged, false);
        }
      }

      @Override
      public void over(int rowIndex, Node commit, MouseEvent e, List<Node> commitsBeingDragged) {
        overNode(rowIndex, commit, e, commitsBeingDragged);
      }

      @Override
      public void overNode(int rowIndex, Node commit, MouseEvent e, List<Node> commitsBeingDragged) {
        if (myConditions.sameBranch(commit, commitsBeingDragged)) {
          currentAction = FIX_UP;
        }
        else {
          currentAction = pickOrRebase(commit, e, commitsBeingDragged, true);
        }
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

    }

    @Override
    public void draggingCanceled(List<Node> commitsBeingDragged) {
      dragDropHint.hide();
    }

  }

  private static void showRefPopup(final List<Ref> refs, Component popupParent, final RefAction refAction) {
    if (refs.size() == 1) {
      refAction.perform(refs.get(0));
    }
    else {
      JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<Ref>("Select target branch", refs.toArray(new Ref[refs.size()])) {

        @NotNull
        @Override
        public String getTextFor(Ref value) {
          return value.getName();
        }

        @Override
        public PopupStep onChosen(Ref selectedValue, boolean finalChoice) {
          refAction.perform(selectedValue);
          return FINAL_CHOICE;
        }

        @Override
        public boolean isSpeedSearchEnabled() {
          return true;
        }
      }).showInCenterOf(popupParent);
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
          mainFrame.getGraphTable().setModel(ui_controller.getGraphTableModel());
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
