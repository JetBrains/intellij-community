package org.jetbrains.plugins.gradle.manage.wizard.adjust;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.gradle.GradleLibraryDependency;

import javax.swing.*;

/**
 * @author Denis Zhdanov
 * @since 10/24/11 3:00 PM
 */
public class GradleLibraryDependencySettings implements GradleProjectStructureNodeSettings {

  private final GradleLibrarySettings   myLibrarySettings;
  private final Runnable                myRefreshCallback;
  private final Runnable                myValidateCallback;
  private final JComponent              myComponent;

  public GradleLibraryDependencySettings(@NotNull GradleLibraryDependency dependency) {
    myLibrarySettings = new GradleLibrarySettings();
    
    GradleProjectSettingsBuilder builder = new GradleProjectSettingsBuilder();
    builder.add(myLibrarySettings.getComponent(), GradleProjectSettingsBuilder.InsetSize.NONE);
    Pair<Runnable,Runnable> pair = GradleAdjustImportSettingsUtil.configureCommonDependencyControls(builder, dependency);
    myRefreshCallback = pair.first;
    myValidateCallback = pair.second;
    myComponent = builder.build();
    refresh();
  }

  @Override
  public void refresh() {
    myLibrarySettings.refresh();
    myRefreshCallback.run();
  }

  @Override
  public boolean validate() {
    if (!myLibrarySettings.validate()) {
      return false;
    }
    myValidateCallback.run();
    return true;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }
}
