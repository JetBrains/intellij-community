package com.intellij.settingsSync.core

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus

/**
 * Allows storing custom settings in the settings sync, if these settings can't be collected and applied via the standard
 * [PersistentStateComponent] mechanism.
 *
 * @param T the structure class holding the settings.
 */
@ApiStatus.Internal
@ApiStatus.OverrideOnly
interface SettingsProvider<T: Any> {

  companion object {
    val SETTINGS_PROVIDER_EP: ExtensionPointName<SettingsProvider<*>> =
      ExtensionPointName.create("com.intellij.settingsSync.settingsProvider")
  }

  /**
   * Returns the id of this provider.
   * This id is used as a provider identifier during serialization/deserialization from/to the snapshot, and also
   * as a folder on disk storing settings specific to this provider, so it should be suitable as a folder name.
   */
  val id: String

  /**
   * The name of the file which is going to be used to store the settings of this provider on the local drive.
   */
  val fileName: String

  /**
   * Returns current state of the settings as they are in the IDE, or `null` if current settings are equal to default.
   * Returning null for default settings is necessary, to avoid creating a file on disk when it is not needed.
   */
  @RequiresBackgroundThread
  fun collectCurrentSettings(): T?

  /**
   * Applies settings received from the cloud, to the IDE.
   */
  @RequiresBackgroundThread
  fun applyNewSettings(newSettings: T)

  @RequiresBackgroundThread
  @Throws(Exception::class)
  fun serialize(settings: T) : String

  @RequiresBackgroundThread
  @Throws(Exception::class)
  fun deserialize(text: String): T

  /**
   * Merges two states (one from the local IDE, another from the cloud) if they were modified simultaneously.
   *
   * @param base The state of the settings, which was recorded by the Settings Sync before that simultaneous modifications happened.
   * The implementation can use the `base` state, for example, to find the difference in changes made in the IDE and in the cloud.
   * In some cases the base state can't be retrieved (for example, if the settings were initially null (default), and the first change
   * to the settings state was conflicting), in that case the `base` is null.
   *
   * @param older The state of the settings which was made earlier.
   *
   * @param newer The state of the settings which was made later.
   * Usually, if there is a real conflict in the settings between the local and the cloud modifications (when the same property is set
   * to different values), then the newer version should be performed. The user set that value more recently.
   *
   * @return the resulting state which will be used as the conflict resolution and will be recorded to the settings sync and propagated
   * both to the local IDE and to the cloud (and then to other machines).
   */
  @RequiresBackgroundThread
  fun mergeStates(base: T?, older: T, newer: T): T

}