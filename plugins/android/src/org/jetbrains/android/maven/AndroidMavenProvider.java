package org.jetbrains.android.maven;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public interface AndroidMavenProvider {
  ExtensionPointName<AndroidMavenProvider> EP_NAME = ExtensionPointName.create("org.jetbrains.android.mavenProvider");

  boolean isMavenizedModule(@NotNull Module module);

  @NotNull
  List<File> getMavenDependencyArtifactFiles(@NotNull Module module);
}
