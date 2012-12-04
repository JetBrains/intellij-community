package org.jetbrains.plugins.gradle.config;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.Nullable;

/**
 * Defines callback for the gradle config structure change.
 * <p/>
 * Implementations of this interface are not obliged to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 1/17/12 1:02 PM
 */
public interface GradleConfigNotifier {
  
  Topic<GradleConfigNotifier> TOPIC = Topic.create("Gradle config", GradleConfigNotifier.class);

  /**
   * Is expected to be invoked when gradle home path is changed.
   * <p/>
   * <b>Note:</b> this callback is executed <b>after</b> the actual config change.
   *
   * @param oldPath  old path (if any)
   * @param newPath  new path (if any)
   */
  void onGradleHomeChange(@Nullable String oldPath, @Nullable String newPath);
  
  /**
   * Is expected to be invoked when linked gradle project path (path of the 'build.gradle' file) is changed.
   * <p/>
   * <b>Note:</b> this callback is executed <b>after</b> the actual config change.
   * 
   * @param oldPath  old path (if any)
   * @param newPath  new path (if any)
   */
  void onLinkedProjectPathChange(@Nullable String oldPath, @Nullable String newPath);

  /**
   * Is expected to be invoked when 'prefer local gradle distribution to wrapper' setting is changed (generally this
   * switches tooling api to different gradle version).
   * <p/>
   * <b>Note:</b> this callback is executed <b>after</b> the actual config change.
   * 
   * @param preferLocalToWrapper    current value
   */
  void onPreferLocalGradleDistributionToWrapperChange(boolean preferLocalToWrapper);
}
