package org.jetbrains.plugins.gradle.importing;

import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.gradle.GradleContentRoot;

import java.util.Collections;

/**
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/7/12 3:20 PM
 */
public class GradleContentRootImporter {

  public void importContentRoots(@NotNull GradleContentRoot contentRoot, @NotNull Module module) {
    importContentRoots(Collections.singleton(contentRoot), module);
  }
  
  public void importContentRoots(@NotNull Iterable<GradleContentRoot> contentRoots, @NotNull Module module) {
    // TODO den implement
  }
}
