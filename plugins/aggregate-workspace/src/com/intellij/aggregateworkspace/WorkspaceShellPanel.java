package com.intellij.aggregateworkspace;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import javax.swing.*;
import java.awt.*;
import java.util.List;

public final class WorkspaceShellPanel extends JPanel {
  private final Project project;
  private final WorkspaceProjectService workspaceService;

  public WorkspaceShellPanel(Project project) {
    super(new BorderLayout());
    this.project = project;
    this.workspaceService = project.getService(WorkspaceProjectService.class);
    setBorder(JBUI.Borders.empty(12));
    add(buildHeader(), BorderLayout.NORTH);
    add(buildBody(), BorderLayout.CENTER);
  }

  private JComponent buildHeader() {
    JPanel header = new JPanel();
    header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
    header.setOpaque(false);

    JBLabel title = new JBLabel("Unified Workspace");
    title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
    header.add(title);
    header.add(Box.createVerticalStrut(4));

    JBLabel subtitle = new JBLabel("Pick a repository and open or choose a local checkout.");
    subtitle.setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);
    header.add(subtitle);
    header.add(Box.createVerticalStrut(12));
    return header;
  }

  private JComponent buildBody() {
    JPanel content = new JPanel();
    content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
    content.setOpaque(false);

    List<WorkspaceRepository> repositories = workspaceService.getRepositories();
    for (WorkspaceRepository repository : repositories) {
      content.add(buildRepoRow(repository));
      content.add(Box.createVerticalStrut(8));
    }

    JBLabel excluded = new JBLabel("GitHub-Codespaces is excluded from this workspace.");
    excluded.setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);
    content.add(excluded);
    return new JBScrollPane(content);
  }

  private JComponent buildRepoRow(WorkspaceRepository repository) {
    JPanel row = new JPanel(new BorderLayout(JBUI.scale(12), 0));
    row.setBorder(JBUI.Borders.compound(
      JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()),
      JBUI.Borders.empty(10)
    ));
    row.setOpaque(false);

    JPanel details = new JPanel();
    details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
    details.setOpaque(false);

    JBLabel name = new JBLabel(repository.getDisplayName());
    name.setFont(name.getFont().deriveFont(Font.BOLD));
    details.add(name);

    JBLabel description = new JBLabel(repository.getDescription());
    description.setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);
    details.add(Box.createVerticalStrut(4));
    details.add(description);

    JPanel actions = new JPanel(new FlowLayout(FlowLayout.TRAILING, JBUI.scale(6), 0));
    actions.setOpaque(false);

    JButton openButton = new JButton("Open");
    openButton.addActionListener(e -> openCheckout(repository));
    JButton chooseButton = new JButton("Choose");
    chooseButton.addActionListener(e -> chooseCheckout(repository));

    actions.add(openButton);
    actions.add(chooseButton);

    row.add(details, BorderLayout.CENTER);
    row.add(actions, BorderLayout.EAST);
    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
    return row;
  }

  private void openCheckout(WorkspaceRepository repository) {
    String path = workspaceService.getCheckoutPath(repository.getId());
    if (path != null) {
      VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
      if (file != null && file.isDirectory()) {
        ProjectUtil.openOrImport(file.getPath(), project, false);
        return;
      }
    }
    chooseCheckout(repository);
  }

  private void chooseCheckout(WorkspaceRepository repository) {
    VirtualFile selected = FileChooser.chooseFile(
      FileChooserDescriptorFactory.createSingleFolderDescriptor(),
      project,
      null
    );
    if (selected == null) {
      return;
    }
    workspaceService.setCheckoutPath(repository.getId(), selected.getPath());
    ProjectUtil.openOrImport(selected.getPath(), project, false);
  }
}
