package org.jetbrains.android.importDependencies;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Trinity;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
class ImportSourceRootsDialog extends DialogWrapper {
  private final ElementsChooser<Trinity<String, String, Collection<String>>> mySourcePathsChooser;

  public ImportSourceRootsDialog(@NotNull Project project, @NotNull List<Trinity<String, String, Collection<String>>> sourceRoots) {
    super(project, false);

    setTitle(AndroidBundle.message("android.import.dependencies.source.roots.dialog.title"));

    mySourcePathsChooser = new ElementsChooser<Trinity<String, String, Collection<String>>>(true) {
      public String getItemText(@NotNull Trinity<String, String, Collection<String>> sourceRootTrinity) {
        return sourceRootTrinity.second.length() > 0
               ? sourceRootTrinity.first + " (" + sourceRootTrinity.second + ")"
               : sourceRootTrinity.first;
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

  public List<Trinity<String, String, Collection<String>>> getMarkedElements() {
    return mySourcePathsChooser.getMarkedElements();
  }
}
