package org.hanuna.gitalk.swing_ui.frame;

import com.intellij.icons.AllIcons;
import org.hanuna.gitalk.swing_ui.GitLogIcons;
import org.hanuna.gitalk.ui.UI_Controller;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author erokhins
 */
public class MainFrame {
  private final UI_Controller ui_controller;

  private final JPanel myToolbar = new JPanel();
  private final JPanel mainPanel = new JPanel();

  private final ActiveSurface myActiveSurface;


  public MainFrame(final UI_Controller ui_controller) {
    this.ui_controller = ui_controller;
    myActiveSurface = new ActiveSurface(ui_controller);
    packMainPanel();
  }

  public UI_GraphTable getGraphTable() {
    return myActiveSurface.getGraphTable();
  }

  private void packToolbar() {
    myToolbar.setLayout(new BoxLayout(myToolbar, BoxLayout.LINE_AXIS));
    myToolbar.setMaximumSize(new Dimension(10000, 10));

    Action hide = new AbstractAction("", GitLogIcons.SPIDER) {
      @Override
      public void actionPerformed(ActionEvent e) {
        ui_controller.hideAll();
      }
    };
    myToolbar.add(new JButton(hide));

    Action show = new AbstractAction("", GitLogIcons.WEB) {
      @Override
      public void actionPerformed(ActionEvent e) {
        ui_controller.showAll();
      }
    };
    myToolbar.add(new JButton(show));

    Action refresh = new AbstractAction("", AllIcons.Actions.Refresh) {
      @Override
      public void actionPerformed(ActionEvent e) {
        ui_controller.refresh();
      }
    };
    myToolbar.add(new JButton(refresh));

    Action apply = new AbstractAction("", GitLogIcons.APPLY) {
      @Override
      public void actionPerformed(ActionEvent e) {
        ui_controller.applyInteractiveRebase();
      }
    };
    myToolbar.add(new JButton(apply));

    final JCheckBox visibleLongEdges = new JCheckBox("Show full patch", false);
    visibleLongEdges.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        ui_controller.setLongEdgeVisibility(visibleLongEdges.isSelected());
      }
    });
    myToolbar.add(visibleLongEdges);

    myToolbar.add(Box.createHorizontalGlue());
  }

  private void packMainPanel() {
    mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

    packToolbar();
    mainPanel.add(myToolbar);
    mainPanel.add(myActiveSurface);
  }

  public JPanel getMainComponent() {
    return mainPanel;
  }

  public BranchesPanel getBranchPanel() {
    return myActiveSurface.getBranchesPanel();
  }
}
