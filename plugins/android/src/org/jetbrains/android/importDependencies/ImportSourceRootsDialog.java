package org.jetbrains.android.importDependencies;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.ide.util.projectWizard.importSources.JavaModuleSourceRoot;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.VerticalFlowLayout;
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
    final JPanel panel = new JPanel(new VerticalFlowLayout());
    panel.setPreferredSize(new Dimension(350, 200));
    panel.add(new JLabel(AndroidBundle.message("android.import.dependencies.source.roots.dialog.label")));
    panel.add(mySourcePathsChooser);
    return panel;
  }

  public List<JavaModuleSourceRoot> getMarkedElements() {
    return mySourcePathsChooser.getMarkedElements();
  }
}
