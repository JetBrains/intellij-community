package org.jetbrains.plugins.javaFX.sceneBuilder;

import com.intellij.openapi.project.Project;

/**
 * @author Alexander Lobas
 */
public interface SceneBuilderProvider {
  SceneBuilderCreator get(Project project, boolean choosePathIfEmpty);
}