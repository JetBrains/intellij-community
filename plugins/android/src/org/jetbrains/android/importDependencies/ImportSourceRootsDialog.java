package org.jetbrains.android.importDependencies;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.ide.util.projectWizard.importSources.JavaModuleSourceRoot;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
class ImportSourceRootsDialog extends DialogWrapper {
  private final ElementsChooser<JavaModuleSourceRoot> mySourcePathsChooser;

  public ImportSourceRootsDialog(@NotNull Project project, @NotNull List<JavaModuleSourceRoot> sourceRoots) {
    super(project, false);

    setTitle(AndroidBundle.message("android.import.dependencies.source.roots.dialog.title"));

    mySourcePathsChooser = new ElementsChooser<JavaModuleSourceRoot>(true) {
      public String getItemText(@NotNull JavaModuleSourceRoot sourceRoot) {
        final String packagePrefix = sourceRoot.getPackagePrefix();
        final String path = sourceRoot.getDirectory().getAbsolutePath();
        return packagePrefix.length() > 0 ? path + " (" + packagePrefix + ")" : path;
      }
    };
    mySourcePathsChooser.setElements(sourceRoots, true);

    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    panel.setPreferredSize(new Dimension(350, 200));
    final JBLabel label = new JBLabel(AndroidBundle.message("android.import.dependencies.source.roots.dialog.label"));
    label.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
    panel.add(label, BorderLayout.NORTH);
    panel.add(mySourcePathsChooser, BorderLayout.CENTER);
    return panel;
  }

  public List<JavaModuleSourceRoot> getMarkedElements() {
    return mySourcePathsChooser.getMarkedElements();
  }
}
