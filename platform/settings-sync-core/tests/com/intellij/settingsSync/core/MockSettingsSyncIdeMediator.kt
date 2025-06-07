package com.intellij.settingsSync.core

import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.relativeTo

internal class MockSettingsSyncIdeMediator : SettingsSyncIdeMediator {
  internal val files = mutableMapOf<String, String>()

  private var exceptionToThrowOnApply: Exception? = null
  private var exceptionToThrowOnGetInitial: Exception? = null

  override suspend fun applyToIde(snapshot: SettingsSnapshot, settings: SettingsSyncState?) {
    if (exceptionToThrowOnApply != null) {
      throw exceptionToThrowOnApply!!
    }

    for (fileState in snapshot.fileStates) {
      if (fileState is FileState.Modified) {
        files[fileState.file] = String(fileState.content, Charset.defaultCharset())
      }
      else {
        files.remove(fileState.file)
      }
    }
  }

  override fun activateStreamProvider() {
  }

  override fun removeStreamProvider() {
  }

  override fun getInitialSnapshot(appConfigPath: Path, lastSavedSnapshot: SettingsSnapshot): SettingsSnapshot {
    if (exceptionToThrowOnGetInitial != null) {
      throw exceptionToThrowOnGetInitial!!
    }
    return getAllFilesFromSettingsAsSnapshot(appConfigPath)
  }

  fun throwOnApply(exception: Exception) {
    exceptionToThrowOnApply = exception
  }

  fun throwOnGetInitial(exception: Exception) {
    exceptionToThrowOnGetInitial = exception
  }

  companion object {
    fun getAllFilesFromSettingsAsSnapshot(appConfigPath: Path): SettingsSnapshot {
      val settingsSyncStorage = appConfigPath.resolve(SETTINGS_SYNC_STORAGE_FOLDER)
      val files = Files.walk(appConfigPath).filter {
        it.isRegularFile() && !it.startsWith(settingsSyncStorage)
      }.toList()

      return settingsSnapshot {
        for (file in files) {
          fileState(file.relativeTo(appConfigPath).toString(), file.readText())
        }
      }
    }
  }
}
