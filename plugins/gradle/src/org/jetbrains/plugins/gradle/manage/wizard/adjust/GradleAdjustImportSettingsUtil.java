package org.jetbrains.plugins.gradle.manage.wizard.adjust;

import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.gradle.AbstractGradleDependency;
import org.jetbrains.plugins.gradle.model.gradle.Named;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import javax.swing.*;

/**
 * @author Denis Zhdanov
 * @since 8/24/11 2:40 PM
 */
public class GradleAdjustImportSettingsUtil {

  private GradleAdjustImportSettingsUtil() {
  }

  /**
   * Performs generic check of the name of the given component.
   * 
   * @param namedComponent  target component
   * @param componentNameUI UI control that allow to manage target component's name
   * @return                <code>true</code> if validation is successful; <code>false</code> otherwise
   */
  public static boolean validate(@NotNull Named namedComponent, @NotNull JComponent componentNameUI) {
    if (!StringUtil.isEmptyOrSpaces(namedComponent.getName())) {
      return true;
    }
    GradleUtil.showBalloon(componentNameUI, MessageType.ERROR, GradleBundle.message("gradle.import.text.error.undefined.name"));
    return false;
  }

  /**
   * Allows to configure GUI controls for managing common dependency settings.
   *
   * @param builder     target GUI builder
   * @param dependency  target dependency
   * @return            pair of two callbacks. The first one is {@link GradleProjectStructureNodeSettings#refresh() 'refresh'} callback,
   *                    the second one is {@link GradleProjectStructureNodeSettings#validate() 'validate'} callback
   */
  @NotNull
  public static Pair<Runnable, Runnable> configureCommonDependencyControls(@NotNull GradleProjectSettingsBuilder builder,
                                                                           @NotNull final AbstractGradleDependency dependency)
  {
    builder.setKeyAndValueControlsOnSameRow(true);
    
    final JCheckBox exportedCheckBock = new JCheckBox();
    builder.add("gradle.import.structure.settings.label.export", exportedCheckBock);

    final JComboBox scopeComboBox = new JComboBox(DependencyScope.values());
    builder.add("gradle.import.structure.settings.label.scope", scopeComboBox);
    
    Runnable refreshCallback = new Runnable() {
      @Override
      public void run() {
         exportedCheckBock.setSelected(dependency.isExported());
         scopeComboBox.setSelectedItem(dependency.getScope());
      }
    };
    
    Runnable validateCallback = new Runnable() {
      @Override
      public void run() {
        dependency.setExported(exportedCheckBock.isSelected());
        dependency.setScope((DependencyScope)scopeComboBox.getSelectedItem());
      }
    };
    return new Pair<Runnable, Runnable>(refreshCallback, validateCallback);
  }
}
