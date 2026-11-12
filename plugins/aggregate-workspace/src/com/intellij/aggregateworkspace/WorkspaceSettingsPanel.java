package com.intellij.aggregateworkspace;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import javax.swing.*;
import java.awt.*;
import java.util.List;

public final class WorkspaceSettingsPanel extends JPanel {
  private final Project project;
  private final List<WorkspaceRepository> repositories;
  private final java.util.Map<String, JCheckBox> checks = new java.util.HashMap<>();

  public WorkspaceSettingsPanel(Project project) {
    this.project = project;
    this.repositories = WorkspaceRepositoryRegistry.repositories;
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    add(new JLabel("Included repositories"));
    add(Box.createVerticalStrut(8));
    for (WorkspaceRepository repo : repositories) {
      JCheckBox box = new JCheckBox(repo.displayName + " - " + repo.description);
      box.setSelected(PropertiesComponent.getInstance(project).getBoolean(repo.id + ".enabled", true));
      checks.put(repo.id, box);
      add(box);
      add(Box.createVerticalStrut(6));
    }
  }

  public boolean isModified() {
    for (WorkspaceRepository repo : repositories) {
      JCheckBox box = checks.get(repo.id);
      if (box != null && box.isSelected() != PropertiesComponent.getInstance(project).getBoolean(repo.id + ".enabled", true)) {
        return true;
      }
    }
    return false;
  }

  public void apply() {
    for (WorkspaceRepository repo : repositories) {
      JCheckBox box = checks.get(repo.id);
      if (box != null) {
        PropertiesComponent.getInstance(project).setValue(repo.id + ".enabled", box.isSelected(), true);
      }
    }
  }

  public void reset() {
    for (WorkspaceRepository repo : repositories) {
      JCheckBox box = checks.get(repo.id);
      if (box != null) {
        box.setSelected(PropertiesComponent.getInstance(project).getBoolean(repo.id + ".enabled", true));
      }
    }
  }
}
