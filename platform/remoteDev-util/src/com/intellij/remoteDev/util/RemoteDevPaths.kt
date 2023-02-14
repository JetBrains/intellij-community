package com.intellij.remoteDev.util

import com.intellij.openapi.application.PathManager
import java.nio.file.Path
import kotlin.io.path.div

object RemoteDevPaths {
  private fun localCacheRootDir() = Path.of(PathManager.getDefaultSystemPathFor("RemoteDev"))

  fun getLocalActiveProjectsDir(): Path = localCacheRootDir() / "active"
  fun getLocalRecentProjectsDir(): Path = localCacheRootDir() / "recent"
  fun getLocalDistDir(): Path = localCacheRootDir() / "dist"
}

//val REMOTE_DEV_CACHE_LOCATION = listOf(".cache", "JetBrains", "RemoteDev")
//val REMOTE_DEV_IDE_DIR = REMOTE_DEV_CACHE_LOCATION + "dist"
//val REMOTE_DEV_CUSTOM_IDE_DIR = REMOTE_DEV_CACHE_LOCATION + "userProvidedDist"
//val REMOTE_DEV_RECENT_PROJECTS_DIR = REMOTE_DEV_CACHE_LOCATION + "recent"
//val REMOTE_DEV_ACTIVE_PROJECTS_DIR = REMOTE_DEV_CACHE_LOCATION + "active"

const val REMOTE_DEV_EXPAND_SUCCEEDED_MARKER_FILE_NAME = ".expandSucceeded"