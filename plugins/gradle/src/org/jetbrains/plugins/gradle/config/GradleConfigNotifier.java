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

  /**
   * Is expected to be invoked when service directory path is changed.
   * <p/>
   * <b>Note:</b> this callback is executed <b>after</b> the actual config change.
   *
   * @param oldPath  old path (if any)
   * @param newPath  new path (if any)
   * @see GradleSettings#getServiceDirectoryPath() 
   */
  void onServiceDirectoryPathChange(@Nullable String oldPath, @Nullable String newPath);
  
  /**
   * Gradle settings changes might affect project structure, e.g. switching from one gradle version to another one or from
   * gradle wrapper to local installation of different version and vice versa can trigger new binaries usage (different gradle
   * versions use different file system directories for holding dependencies).
   * <p/>
   * So, we might want to refresh project structure on gradle setting change. However, there is a possible case that more
   * than one significant setting is changed during single editing session (e.g. a user opens gradle settings, changes linked
   * project path and 'use gradle wrapper' and then presses 'Ok'.). We don't want to trigger two refreshes then. That's why
   * it's possible to indicate that settings are changed in bulk now.
   * <p/>
   * {@link #onBulkChangeEnd()} is expected to be called at the 'finally' section which starts just after the call to
   * current method.
   */
  void onBulkChangeStart();

  /**
   * @see #onBulkChangeStart()
   */
  void onBulkChangeEnd();
}
