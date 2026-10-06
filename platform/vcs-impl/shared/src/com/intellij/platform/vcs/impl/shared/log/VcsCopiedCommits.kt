// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.log

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.SystemIndependent
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.IOException

/**
 * One commit that a copy from a commit list puts on the clipboard.
 */
@ApiStatus.Internal
@Serializable
data class VcsCopiedCommit(
  val hash: String,
  val rootPath: @SystemIndependent String,
  val subject: String? = null,
  val author: String? = null,
  val commitTimeMs: Long? = null,
)

/**
 * The result of [VcsCopiedCommits.read].
 */
@ApiStatus.Internal
sealed interface VcsCopiedCommitsReadResult {
  data class Success(val commits: List<VcsCopiedCommit>) : VcsCopiedCommitsReadResult

  /**
   * [error] is a technical description for the log. It is not localized.
   */
  data class Failure(val error: String) : VcsCopiedCommitsReadResult
}

/**
 * The clipboard format for the commits that a copy from a commit list carries beside its plain text.
 *
 * A paste target that knows commits reads them with [read]. Every other paste target reads the plain text.
 * The commits travel as a JSON string, so the system clipboard carries them without a custom class.
 */
@ApiStatus.Internal
object VcsCopiedCommits {
  val FLAVOR: DataFlavor = DataFlavor("application/x-intellij-vcs-commits; class=java.lang.String", "VCS commits")

  private val json = Json { ignoreUnknownKeys = true }
  private val serializer = ListSerializer(VcsCopiedCommit.serializer())

  @JvmStatic
  fun createTransferable(text: String, commits: List<VcsCopiedCommit>): Transferable {
    return VcsCopiedCommitsTransferable(text, json.encodeToString(serializer, commits))
  }

  fun read(transferable: Transferable): VcsCopiedCommitsReadResult {
    if (!transferable.isDataFlavorSupported(FLAVOR)) {
      return VcsCopiedCommitsReadResult.Failure("The transferable has no copied commits")
    }
    val data = try {
      transferable.getTransferData(FLAVOR)
    }
    catch (e: UnsupportedFlavorException) {
      return VcsCopiedCommitsReadResult.Failure("The transferable no longer supports the copied commits: ${e.message}")
    }
    catch (e: IOException) {
      return VcsCopiedCommitsReadResult.Failure("Cannot read the copied commits: ${e.message}")
    }
    if (data !is String) {
      return VcsCopiedCommitsReadResult.Failure("The copied commits are not a string: ${data?.javaClass?.name}")
    }
    val commits = try {
      json.decodeFromString(serializer, data)
    }
    catch (e: IllegalArgumentException) {
      return VcsCopiedCommitsReadResult.Failure("Cannot decode the copied commits: ${e.message}")
    }
    if (commits.isEmpty()) {
      return VcsCopiedCommitsReadResult.Failure("The copied commit list is empty")
    }
    return VcsCopiedCommitsReadResult.Success(commits)
  }
}

/** Gives the plain text through a [StringSelection], so the text flavors stay the ones that a plain copy gives. */
private class VcsCopiedCommitsTransferable(text: String, private val commitsJson: String) : Transferable {
  private val textSelection = StringSelection(text)

  override fun getTransferDataFlavors(): Array<DataFlavor> = textSelection.transferDataFlavors + VcsCopiedCommits.FLAVOR

  override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean {
    return VcsCopiedCommits.FLAVOR == flavor || textSelection.isDataFlavorSupported(flavor)
  }

  override fun getTransferData(flavor: DataFlavor?): Any {
    return if (VcsCopiedCommits.FLAVOR == flavor) commitsJson else textSelection.getTransferData(flavor)
  }
}
