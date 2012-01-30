package org.jetbrains.plugins.gradle.diff;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;

/**
 * @author Denis Zhdanov
 * @since 1/26/12 11:54 AM
 */
public class GradleProjectStructureHelperImpl implements GradleProjectStructureHelper {

  @NotNull
  @Override
  public LanguageLevel getLanguageLevel(@NotNull Project project) {
    return LanguageLevelProjectExtension.getInstance(project).getLanguageLevel();
  }

  @NotNull
  @Override
  public Collection<Module> getModules(@NotNull Project project) {
    return Arrays.asList(ModuleManager.getInstance(project).getModules());
  }

  @NotNull
  @Override
  public Collection<OrderEntry> getOrderEntries(@NotNull Module module) {
    return Arrays.asList(ModuleRootManager.getInstance(module).getOrderEntries());
  }
}
