package org.jetbrains.plugins.gradle.manage.wizard.adjust;

import com.intellij.ide.util.projectWizard.NamePathComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.gradle.GradleContentRoot;
import org.jetbrains.plugins.gradle.model.gradle.SourceType;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import javax.swing.*;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/**
 * @author Denis Zhdanov
 * @since 10/31/11 1:52 PM
 */
public class GradleContentRootSettings implements GradleProjectStructureNodeSettings {

  private static final Map<SourceType, String> ROOT_TYPE_TITLES = new EnumMap<SourceType, String>(SourceType.class);
  static {
    ROOT_TYPE_TITLES.put(SourceType.SOURCE, GradleBundle.message("gradle.import.structure.settings.label.root.source"));
    ROOT_TYPE_TITLES.put(SourceType.TEST, GradleBundle.message("gradle.import.structure.settings.label.root.test"));
    ROOT_TYPE_TITLES.put(SourceType.EXCLUDED, GradleBundle.message("gradle.import.structure.settings.label.root.excluded"));
    assert ROOT_TYPE_TITLES.size() == SourceType.values().length;
  }

  @NotNull private final JComponent myComponent;

  public GradleContentRootSettings(@NotNull GradleContentRoot contentRoot) {
    GradleProjectSettingsBuilder builder = new GradleProjectSettingsBuilder();
    for (SourceType sourceType : SourceType.values()) {
      Collection<String> paths = contentRoot.getPaths(sourceType);
      if (paths.isEmpty()) {
        continue;
      }
      builder.add(new JLabel(ROOT_TYPE_TITLES.get(sourceType)));
      for (String path : paths) {
        NamePathComponent component = new NamePathComponent("", "  ", "", "", false);
        component.setNameComponentVisible(false);
        component.setPath(path);
        component.getPathPanel().setEditable(false);
        builder.add(component, GradleProjectSettingsBuilder.InsetSize.SMALL);
      }
    }
    myComponent = builder.build();
  }

  @Override
  public boolean validate() {
    return true;
  }

  @Override
  public void refresh() {
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }
}
