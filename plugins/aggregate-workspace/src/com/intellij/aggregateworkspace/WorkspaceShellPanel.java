package com.intellij.aggregateworkspace;

import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import javax.swing.*;
import java.awt.*;
import java.util.List;

public final class WorkspaceShellPanel extends JPanel {
  private final Project project;

  public WorkspaceShellPanel(Project project) {
    super(new BorderLayout());
    this.project = project;

    setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    setBackground(new Color(0xF7F7F7));
    add(buildHeader(), BorderLayout.NORTH);
    add(buildBody(), BorderLayout.CENTER);
  }

  private JComponent buildHeader() {
    JBLabel title = new JBLabel("Unified Workspace Shell");
    title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
    title.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
    return title;
  }

  private JComponent buildBody() {
    JPanel content = new JPanel();
    content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
    content.setOpaque(false);

    List<WorkspaceRepo> repos = project.getService(WorkspaceProjectService.class).getRepos();

    content.add(sectionTitle("Included repositories"));
    for (WorkspaceRepo repo : repos) {
      if (repo.isIncluded()) {
        content.add(repoCard(repo));
      }
    }

    content.add(Box.createVerticalStrut(12));
    content.add(sectionTitle("Excluded"));
    for (WorkspaceRepo repo : repos) {
      if (!repo.isIncluded()) {
        content.add(repoCard(repo));
      }
    }

    return new JBScrollPane(content);
  }

  private static JComponent sectionTitle(String text) {
    JBLabel label = new JBLabel(text);
    label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
    label.setBorder(BorderFactory.createEmptyBorder(8, 0, 6, 0));
    return label;
  }

  private static JComponent repoCard(WorkspaceRepo repo) {
    JPanel card = new JPanel(new BorderLayout());
    card.setBorder(BorderFactory.createCompoundBorder(
      BorderFactory.createLineBorder(new Color(0xD7D7D7)),
      BorderFactory.createEmptyBorder(10, 12, 10, 12)
    ));
    card.setBackground(repo.isIncluded() ? new Color(0xFFFFFF) : new Color(0xFFF1F1));
    card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

    JBLabel title = new JBLabel(repo.getName());
    title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));

    JBLabel description = new JBLabel(repo.getDescription());
    description.setForeground(new Color(0x5B5B5B));

    card.add(title, BorderLayout.NORTH);
    card.add(description, BorderLayout.CENTER);

    if (!repo.isIncluded()) {
      JLabel badge = new JLabel("excluded");
      badge.setForeground(new Color(0xA03030));
      badge.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
      card.add(badge, BorderLayout.EAST);
    }
    return card;
  }
}
