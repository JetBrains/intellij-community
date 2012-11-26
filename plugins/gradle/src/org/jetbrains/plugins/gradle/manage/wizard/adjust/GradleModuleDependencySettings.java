package org.jetbrains.plugins.gradle.manage.wizard.adjust;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.gradle.GradleModuleDependency;

import javax.swing.*;

/**
 * @author Denis Zhdanov
 * @since 10/28/11 12:40 PM
 */
public class GradleModuleDependencySettings implements GradleProjectStructureNodeSettings {

  private final GradleModuleSettings myModuleSettings;
  private final Runnable             myRefreshCallback;
  private final Runnable             myValidateCallback;
  private final JComponent           myComponent;
  
  public GradleModuleDependencySettings(@NotNull GradleModuleDependency dependency) {
    myModuleSettings = new GradleModuleSettings(dependency.getTarget());

    GradleProjectSettingsBuilder builder = new GradleProjectSettingsBuilder();
    builder.add(myModuleSettings.getComponent(), GradleProjectSettingsBuilder.InsetSize.NONE);
    Pair<Runnable,Runnable> pair = GradleAdjustImportSettingsUtil.configureCommonDependencyControls(builder, dependency);
    myRefreshCallback = pair.first;
    myValidateCallback = pair.second;
    myComponent = builder.build();
    refresh();
  }

  @Override
  public boolean validate() {
    if (!myModuleSettings.validate()) {
      return false;
    }
    myValidateCallback.run();
    return true;
  }

  @Override
  public void refresh() {
    myModuleSettings.refresh();
    myRefreshCallback.run(); 
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }
}
