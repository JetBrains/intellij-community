package org.hanuna.gitalk.swing_ui.frame;

import com.intellij.util.ui.UIUtil;
import org.hanuna.gitalk.commit.Hash;
import org.hanuna.gitalk.refs.Ref;
import org.hanuna.gitalk.swing_ui.render.Print_Parameters;
import org.hanuna.gitalk.swing_ui.render.painters.RefPainter;
import org.hanuna.gitalk.ui.UI_Controller;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;

/**
 * Panel with branch labels, above the graph.
 *
 * @author Kirill Likhodedov
 */
public class BranchesPanel extends JPanel {

  private final UI_Controller myUiController;

  public BranchesPanel(UI_Controller ui_controller, List<Ref> refs) {
    myUiController = ui_controller;
    setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));

    for (Ref ref : refs) {
      BranchLabel branchLabel = new BranchLabel(ref);
      add(branchLabel);
    }
    add(Box.createHorizontalGlue());

    setPreferredSize(new Dimension(-1, Print_Parameters.HEIGHT_CELL + UIUtil.DEFAULT_VGAP));
  }

  private class BranchLabel extends JPanel {

    private final RefPainter myRefPainter;
    private final Ref myRef;

    public BranchLabel(Ref ref) {
      myRef = ref;
      myRefPainter = new RefPainter();
      this.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          Hash commitCash = myRef.getCommitHash();
          myUiController.updateVisibleBranches();
          myUiController.jumpToCommit(commitCash);
        }
      });
    }

    @Override
    protected void paintComponent(Graphics g) {
      myRefPainter.draw((Graphics2D)g, Collections.singletonList(myRef), 0);
    }
  }
}
