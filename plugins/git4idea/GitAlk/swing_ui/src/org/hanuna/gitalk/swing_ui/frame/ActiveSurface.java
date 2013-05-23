package org.hanuna.gitalk.swing_ui.frame;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import org.hanuna.gitalk.refs.Ref;
import org.hanuna.gitalk.ui.UI_Controller;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Graph + the panel with branch labels above it.
 *
 * @author Kirill Likhodedov
 */
public class ActiveSurface extends JPanel {

  private final UI_GraphTable graphTable;
  private final BranchesPanel myBranchesPanel;

  ActiveSurface(UI_Controller ui_controller) {
    this.graphTable = new UI_GraphTable(ui_controller);
    List<Ref> allRefs = ui_controller.getRefs();
    ArrayList<Ref> refsWithoutTags = new ArrayList<Ref>(Collections2.filter(allRefs, new Predicate<Ref>() {
      @Override
      public boolean apply(@Nullable Ref input) {
        assert input != null;
        Ref.RefType type = input.getType();
        return type == Ref.RefType.LOCAL_BRANCH || type == Ref.RefType.REMOTE_BRANCH || type == Ref.RefType.ANOTHER;
      }
    }));
    myBranchesPanel = new BranchesPanel(ui_controller, refsWithoutTags);
    packTables();
  }

  public UI_GraphTable getGraphTable() {
    return graphTable;
  }

  private void packTables() {
    setLayout(new BorderLayout());
    add(myBranchesPanel, BorderLayout.NORTH);
    add(new JScrollPane(graphTable), BorderLayout.CENTER);
  }

}
