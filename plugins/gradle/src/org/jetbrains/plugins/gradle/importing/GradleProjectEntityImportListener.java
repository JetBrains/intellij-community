package org.jetbrains.plugins.gradle.importing;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

/**
 * Defines contract for the callback to be notified on project structure changes triggered by the gradle integrations.
 * <p/>
 * Implementations of this interface are not obliged to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/24/12 1:05 PM
 */
public interface GradleProjectEntityImportListener {

  Topic<GradleProjectEntityImportListener> TOPIC = Topic.create("Gradle config", GradleProjectEntityImportListener.class);

  /**
   * Is called <b>before</b> the given entity is imported.
   * 
   * @param entity  target entity being imported
   */
  void onImportStart(@NotNull Object entity);

  /**
   * Is called <b>after</b> the given entity has been imported.
   *
   * @param entity  target entity that has been imported
   */
  void onImportEnd(@NotNull Object entity);
}
