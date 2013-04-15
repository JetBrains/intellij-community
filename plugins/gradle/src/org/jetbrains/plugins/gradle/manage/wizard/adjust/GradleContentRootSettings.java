package org.jetbrains.plugins.gradle.manage.wizard.adjust;

import com.intellij.ide.util.projectWizard.NamePathComponent;
import com.intellij.openapi.externalSystem.model.project.ContentRootData;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/**
 * @author Denis Zhdanov
 * @since 10/31/11 1:52 PM
 */
public class GradleContentRootSettings implements GradleProjectStructureNodeSettings {

  private static final Map<ExternalSystemSourceType, String> ROOT_TYPE_TITLES = ContainerUtilRt.newHashMap();

  static {
    // TODO den implement
//    ROOT_TYPE_TITLES.put(ExternalSystemSourceType.SOURCE,
//                         ExternalSystemBundle.message("gradle.import.structure.settings.label.root.source"));
//    ROOT_TYPE_TITLES.put(ExternalSystemSourceType.TEST,
//                         ExternalSystemBundle.message("gradle.import.structure.settings.label.root.test"));
//    ROOT_TYPE_TITLES.put(ExternalSystemSourceType.EXCLUDED,
//                         ExternalSystemBundle.message("gradle.import.structure.settings.label.root.excluded"));
  }

  @NotNull private final JComponent myComponent;

  public GradleContentRootSettings(@NotNull ContentRootData contentRoot) {
    GradleProjectSettingsBuilder builder = new GradleProjectSettingsBuilder();
    // TODO den implement
//    for (ExternalSystemSourceType sourceType : ExternalSystemSourceType.values()) {
//      Collection<String> paths = contentRoot.getPaths(sourceType);
//      if (paths.isEmpty()) {
//        continue;
//      }
//      builder.add(new JLabel(ROOT_TYPE_TITLES.get(sourceType)));
//      for (String path : paths) {
//        NamePathComponent component = new NamePathComponent("", "  ", "", "", false);
//        component.setNameComponentVisible(false);
//        component.setPath(path);
//        component.getPathPanel().setEditable(false);
//        builder.add(component, GradleProjectSettingsBuilder.InsetSize.SMALL);
//      }
//    }
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
