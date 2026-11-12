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
import org.jetbrains.annotations.Nullable;

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

    JBLabel title = new JBLabel(WorkspaceBundle.message("shell.title"));
    title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
    JBLabel subtitle = new JBLabel(WorkspaceBundle.message("shell.subtitle"));
    subtitle.setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);
    header.add(title);
    header.add(Box.createVerticalStrut(4));
    header.add(subtitle);
    header.add(Box.createVerticalStrut(12));
    return header;
  }

  private JComponent buildBody() {
    JPanel content = new JPanel();
    content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
    content.setOpaque(false);

    List<WorkspaceRepo> repositories = workspaceService.getRepositories();
    for (WorkspaceRepo repository : repositories) {
      content.add(repoCard(repository));
      content.add(Box.createVerticalStrut(8));
    }

    JBLabel excluded = new JBLabel(WorkspaceBundle.message("shell.excluded"));
    excluded.setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);
    content.add(Box.createVerticalStrut(8));
    content.add(excluded);
    return new JBScrollPane(content);
  }

  private JComponent repoCard(WorkspaceRepo repository) {
    JPanel card = new JPanel(new BorderLayout(JBUI.scale(12), 0));
    card.setBorder(JBUI.Borders.compound(
      JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()),
      JBUI.Borders.empty(10)
    ));
    card.setOpaque(false);

    JPanel details = new JPanel();
    details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
    details.setOpaque(false);
    JBLabel name = new JBLabel(repository.getName());
    name.setFont(name.getFont().deriveFont(Font.BOLD));
    JBLabel description = new JBLabel(repository.getDescription());
    description.setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);
    details.add(name);
    details.add(Box.createVerticalStrut(4));
    details.add(description);

    JPanel actions = new JPanel(new FlowLayout(FlowLayout.TRAILING, JBUI.scale(6), 0));
    actions.setOpaque(false);
    JButton openButton = new JButton(WorkspaceBundle.message("repo.open"));
    openButton.addActionListener(event -> openCheckout(repository));
    JButton chooseButton = new JButton(WorkspaceBundle.message("repo.choose"));
    chooseButton.addActionListener(event -> chooseCheckout(repository));
    actions.add(openButton);
    actions.add(chooseButton);

    card.add(details, BorderLayout.CENTER);
    card.add(actions, BorderLayout.EAST);
    card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
    return card;
  }

  private void openCheckout(WorkspaceRepo repository) {
    String path = workspaceService.getCheckoutPath(repository);
    VirtualFile directory = path == null ? null : LocalFileSystem.getInstance().findFileByPath(path);
    if (directory != null && directory.isDirectory()) {
      ProjectUtil.openOrImport(directory.getPath(), project, false);
    }
    else {
      chooseCheckout(repository);
    }
  }

  private void chooseCheckout(WorkspaceRepo repository) {
    VirtualFile directory = FileChooser.chooseFile(
      FileChooserDescriptorFactory.createSingleFolderDescriptor(), project, null
    );
    if (directory == null) {
      return;
    }
    workspaceService.setCheckoutPath(repository, directory.getPath());
    ProjectUtil.openOrImport(directory.getPath(), project, false);
  }
}
