package org.jetbrains.plugins.gradle.diff;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * IntelliJ code provides a lot of statical bindings to the interested pieces of data. For example we need to execute code
 * like below to get list of modules for the target project:
 * <pre>
 *   ModuleManager.getInstance(project).getModules()
 * </pre>
 * That means that it's not possible to test target classes in isolation if corresponding infrastructure is not set up.
 * However, we don't want to set it up if we execute a simple standalone test.
 * <p/>
 * This interface is intended to encapsulate access to the underlying project infrastructure.
 * <p/>
 * Implementations of this interface are expected to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 1/26/12 11:32 AM
 */
public interface GradleProjectStructureHelper {

  @NotNull
  LanguageLevel getLanguageLevel(@NotNull Project project);

  @NotNull
  Collection<Module> getModules(@NotNull Project project);

  @NotNull
  Collection<OrderEntry> getOrderEntries(@NotNull Module module);
}
