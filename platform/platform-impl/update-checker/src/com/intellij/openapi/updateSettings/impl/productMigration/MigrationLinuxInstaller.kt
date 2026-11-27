// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.productMigration

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.productMigration.readInstalledProductInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.updateSettings.impl.DistributionInfo
import com.intellij.util.io.Decompressor
import com.intellij.util.system.OS
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.function.Consumer
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

private val LOG = logger<MigrationInstallException>()

private const val PARTIAL_DIR_PREFIX = ".migration-partial-"
private const val STALE_PARTIAL_DIR_MS = 24 * 60 * 60 * 1000L

/**
 * Returns the Unified IDE with the product code [product] in a folder of [parent], or `null`.
 * It looks at each folder of [parent], one level deep. A folder is a match when its `product-info.json` has [product] and a launcher of Linux.
 * The name of a folder has the build number of its first build, so the name does not find the Unified IDE.
 * From more than one match, the result is the folder with the last name in the order of `String`.
 */
@ApiStatus.Internal
fun findInstalledLinuxApp(parent: Path, product: String): Path? {
  val folders = try {
    parent.listDirectoryEntries().filter { it.isDirectory(LinkOption.NOFOLLOW_LINKS) }
  }
  catch (e: IOException) {
    LOG.info("cannot list ${parent}: ${e.message}")
    return null
  }
  return folders.filter { isLinuxApp(it, product) }.maxByOrNull { it.name }
}

private fun isLinuxApp(folder: Path, product: String): Boolean {
  val info = readInstalledProductInfo(folder, OS.Linux)
  return info != null && info.productCode == product && info.launcher != null
}

/**
 * Installs the distribution with the product code [product] into a folder of [parent], or uses the Unified IDE that is already there.
 * See [findInstalledLinuxApp]. The IDE never deletes or changes a folder with content that exists before the call.
 * An empty folder with the name of the archive root gets the install.
 *
 * [download] gives the archive. [unpack] unpacks it into an empty folder.
 * The archive must have one root folder with the product code [product] and a launcher. This folder gets its name in [parent].
 * The unpack goes into a partial folder of this call in [parent]. A rename then makes the install, so [parent] never has a part of it.
 * After a failure and after a cancel, the partial folder is deleted.
 * A partial folder of an IDE that did not end as usual stays. A later call deletes it when it is older than a day.
 */
@ApiStatus.Internal
fun installLinuxApp(
  parent: Path,
  product: String,
  indicator: ProgressIndicator,
  download: () -> Path,
  unpack: (archive: Path, target: Path) -> Unit,
): Path {
  indicator.text = IdeBundle.message("updates.migration.progress.verify")
  findInstalledLinuxApp(parent, product)?.let { return used(it) }

  val archive = download()
  indicator.text = IdeBundle.message("updates.migration.progress.install")
  indicator.text2 = ""
  indicator.isIndeterminate = true
  // another IDE of the user can install the Unified IDE during the download
  findInstalledLinuxApp(parent, product)?.let { return used(it) }

  val partial = try {
    Files.createDirectories(parent)
    deleteStalePartialDirs(parent)
    Files.createTempDirectory(parent, PARTIAL_DIR_PREFIX)
  }
  catch (e: IOException) {
    throw installError("cannot make a folder in ${parent}: ${e.message}", e)
  }
  try {
    unpack(archive, partial)
    val root = partial.listDirectoryEntries().singleOrNull()?.takeIf { it.isDirectory(LinkOption.NOFOLLOW_LINKS) }
               ?: throw installError("the archive has no single root folder")
    if (!isLinuxApp(root, product)) {
      throw installError("the archive has no product ${product} with a launcher: ${readInstalledProductInfo(root, OS.Linux)?.productCode}")
    }

    val target = parent.resolve(root.name)
    try {
      // the rename replaces an empty folder, and it fails for a folder with content
      Files.move(root, target, StandardCopyOption.ATOMIC_MOVE)
    }
    catch (e: IOException) {
      if (isLinuxApp(target, product)) return used(target)
      if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw existsError(target, "${target} exists and is not the product ${product}: ${e}")
      throw installError("cannot move ${root} to ${target}: ${e}", e)
    }
    return target
  }
  catch (e: IOException) {
    throw installError("cannot install into ${parent}: ${e.message}", e)
  }
  finally {
    deleteLogged(partial)
  }
}

private fun used(app: Path): Path {
  LOG.info("${app} is already installed, and the archive is not unpacked")
  return app
}

/**
 * A live install changes its partial folder only at the start, so the age limit is much longer than an unpack.
 * A failure is logged, because the install does not need the cleanup.
 */
private fun deleteStalePartialDirs(parent: Path) {
  val limit = System.currentTimeMillis() - STALE_PARTIAL_DIR_MS
  val dirs = try {
    parent.listDirectoryEntries("${PARTIAL_DIR_PREFIX}*")
  }
  catch (e: IOException) {
    LOG.info("cannot list ${parent}: ${e.message}")
    return
  }
  for (dir in dirs) {
    try {
      if (dir.isDirectory(LinkOption.NOFOLLOW_LINKS) && Files.getLastModifiedTime(dir, LinkOption.NOFOLLOW_LINKS).toMillis() < limit) {
        LOG.info("deleting the old partial folder ${dir}")
        deleteLogged(dir)
      }
    }
    catch (e: IOException) {
      LOG.info("cannot read ${dir}: ${e.message}")
    }
  }
}

/**
 * Unpacks the `.tar.gz` [archive] into [target]. The files keep their mode and their relative links.
 * An entry that goes out of [target], also through a link, gives the usual install error. A cancel of [indicator] stops the unpack.
 */
@ApiStatus.Internal
fun unpackMigrationArchive(archive: Path, target: Path, indicator: ProgressIndicator) {
  try {
    Decompressor.Tar(archive).postProcessor(Consumer<Path> { indicator.checkCanceled() }).extract(target)
  }
  catch (e: IOException) {
    throw installError("cannot unpack ${archive}: ${e.message}", e)
  }
}

/** Installs [distribution] for the current user into the common data folder of the IDEs, and writes its entry of the application menu. */
internal fun installLinuxDistribution(distribution: DistributionInfo, workDir: Path, indicator: ProgressIndicator): Path {
  val parent = try {
    PathManager.getCommonDataPath()
  }
  catch (e: UncheckedIOException) {
    throw installError("cannot make the common data folder: ${e.message}", e)
  }
  val app = installLinuxApp(
    parent = parent,
    product = distribution.product,
    indicator = indicator,
    download = { downloadAndVerify(distribution.fileUrl, workDir, indicator) },
    unpack = { archive, target -> unpackMigrationArchive(archive, target, indicator) },
  )
  // the common data folder is `<data home>/JetBrains`
  writeLinuxDesktopEntry(app, distribution.product, parent.resolveSibling("applications"))
  return app
}
