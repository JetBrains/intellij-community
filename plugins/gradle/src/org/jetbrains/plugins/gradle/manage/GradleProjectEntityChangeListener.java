package org.jetbrains.plugins.gradle.manage;

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
public interface GradleProjectEntityChangeListener {

  Topic<GradleProjectEntityChangeListener> TOPIC = Topic.create("Gradle config", GradleProjectEntityChangeListener.class);

  /**
   * Is called <b>before</b> the given entity is changed.
   *
   * @param entity  target entity being changed
   */
  void onChangeStart(@NotNull Object entity);

  /**
   * Is called <b>after</b> the given entity has been changed.
   *
   * @param entity  target entity that has been changed
   */
  void onChangeEnd(@NotNull Object entity);
}
