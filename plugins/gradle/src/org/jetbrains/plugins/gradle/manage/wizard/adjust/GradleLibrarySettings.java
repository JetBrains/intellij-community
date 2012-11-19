package org.jetbrains.plugins.gradle.manage.wizard.adjust;

import com.intellij.ide.util.projectWizard.NamePathComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.gradle.GradleLibrary;
import org.jetbrains.plugins.gradle.model.gradle.LibraryPathType;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import javax.swing.*;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Denis Zhdanov
 * @since 8/23/11 4:22 PM
 */
public class GradleLibrarySettings implements GradleProjectStructureNodeSettings {

  private static final Map<LibraryPathType, String> PATH_TITLES = new EnumMap<LibraryPathType, String>(LibraryPathType.class);
  static {
    PATH_TITLES.put(LibraryPathType.BINARY, GradleBundle.message("gradle.import.structure.settings.label.library.path.binary"));
    PATH_TITLES.put(LibraryPathType.SOURCE, GradleBundle.message("gradle.import.structure.settings.label.library.path.source"));
    PATH_TITLES.put(LibraryPathType.DOC, GradleBundle.message("gradle.import.structure.settings.label.library.path.doc"));
    assert PATH_TITLES.size() == LibraryPathType.values().length;
  }
  
  private final GradleLibrary myLibrary;
  private final JComponent    myComponent;
  private final JTextField    myNameControl;
  
  public GradleLibrarySettings(@NotNull GradleLibrary library) {
    myLibrary = library;
    GradleProjectSettingsBuilder builder = new GradleProjectSettingsBuilder();
    myNameControl = GradleAdjustImportSettingsUtil.configureNameControl(builder, library);
    setupLibraryPaths(builder);
    myComponent = builder.build();
  }
  
  private void setupLibraryPaths(@NotNull GradleProjectSettingsBuilder builder) {
    for (LibraryPathType pathType : LibraryPathType.values()) {
      Set<String> paths = myLibrary.getPaths(pathType);
      if (paths.isEmpty()) {
        continue;
      }
      builder.add(new JLabel(PATH_TITLES.get(pathType)));
      for (String path : paths) {
        NamePathComponent component = new NamePathComponent("", "  ", "", "", false);
        component.setNameComponentVisible(false);
        component.setPath(path);
        component.getPathPanel().setEditable(false);
        builder.add(component, GradleProjectSettingsBuilder.InsetSize.SMALL);
      }
    }
  }
  
  @Override
  public boolean validate() {
    return GradleAdjustImportSettingsUtil.validate(myLibrary, myNameControl);
  }

  @Override
  public void refresh() {
    myNameControl.setText(myLibrary.getName());
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }
}
