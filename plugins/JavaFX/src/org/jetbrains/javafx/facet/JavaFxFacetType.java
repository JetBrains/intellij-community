package org.jetbrains.javafx.facet;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.JavaFxFileType;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxFacetType extends FacetType<JavaFxFacet, JavaFxFacetConfiguration> {
  public JavaFxFacetType() {
    super(JavaFxFacet.ID, "javafx", "JavaFX");
  }

  @Override
  public JavaFxFacetConfiguration createDefaultConfiguration() {
    return new JavaFxFacetConfiguration();
  }

  @Override
  public JavaFxFacet createFacet(@NotNull Module module,
                                 String name,
                                 @NotNull JavaFxFacetConfiguration configuration,
                                 @Nullable Facet underlyingFacet) {
    return new JavaFxFacet(module, name, configuration);
  }

  @Override
  public Icon getIcon() {
    return JavaFxFileType.INSTANCE.getIcon();
  }

  @Override
  public boolean isSuitableModuleType(ModuleType moduleType) {
    return moduleType instanceof JavaModuleType;
  }
}
