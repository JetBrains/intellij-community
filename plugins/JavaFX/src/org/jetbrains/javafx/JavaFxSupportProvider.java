package org.jetbrains.javafx;

import com.intellij.ide.util.frameworkSupport.FrameworkSupportConfigurable;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModel;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportProvider;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.ModuleType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.facet.JavaFxFacet;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxSupportProvider extends FrameworkSupportProvider {
  protected JavaFxSupportProvider() {
    super("Support Provider: " + JavaFxFacet.ID.toString(), "JavaFX");
  }

  @NotNull
  @Override
  public FrameworkSupportConfigurable createConfigurable(@NotNull FrameworkSupportModel model) {
    return new JavaFxSupportConfigurable();
  }

  @Override
  public Icon getIcon() {
    return JavaFxFileType.INSTANCE.getIcon();
  }

  @Override
  public boolean isEnabledForModuleType(@NotNull ModuleType moduleType) {
    return moduleType instanceof JavaModuleType;
  }
}
