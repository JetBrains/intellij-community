package org.jetbrains.android.importDependencies;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.containers.HashMap;
import org.jetbrains.android.util.AndroidBundle;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
class ImportDependenciesDialog extends DialogWrapper {
  private final CheckBoxList myCheckBoxList = new CheckBoxList();
  private final List<ImportDependenciesTask> myTasks;
  private final Map<ImportDependenciesTask, JCheckBox> myTask2Checkbox = new HashMap<ImportDependenciesTask, JCheckBox>();

  protected ImportDependenciesDialog(Project project, List<ImportDependenciesTask> tasks) {
    super(project, false);
    setTitle(AndroidBundle.message("android.import.dependencies.dialog.title"));

    myTasks = tasks;

    final JCheckBox[] checkBoxes = new JCheckBox[tasks.size()];

    for (int i = 0; i < checkBoxes.length; i++) {
      final ImportDependenciesTask task = tasks.get(i);
      final JCheckBox checkBox = new JCheckBox(task.getTitle());
      checkBox.setSelected(true);
      checkBoxes[i] = checkBox;
      myTask2Checkbox.put(task, checkBox);
    }

    myCheckBoxList.setModel(new CollectionListModel(checkBoxes));

    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    final JBScrollPane pane = new JBScrollPane(myCheckBoxList);
    pane.setPreferredSize(new Dimension(500, 200));
    return pane;
  }

  public List<ImportDependenciesTask> getSelectedTasks() {
    final List<ImportDependenciesTask> result = new ArrayList<ImportDependenciesTask>();
    for (ImportDependenciesTask task : myTasks) {
      if (myTask2Checkbox.get(task).isSelected()) {
        result.add(task);
      }
    }
    return result;
  }
}
