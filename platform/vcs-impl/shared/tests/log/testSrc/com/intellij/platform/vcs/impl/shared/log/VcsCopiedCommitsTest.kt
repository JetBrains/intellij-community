// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.log

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class VcsCopiedCommitsTest {
  @Test
  fun `transferable keeps the plain text for other paste targets`() {
    val transferable = VcsCopiedCommits.createTransferable("Fix the bug Ann 06.10.2026", listOf(FIRST))

    assertTrue(transferable.isDataFlavorSupported(DataFlavor.stringFlavor))
    assertEquals("Fix the bug Ann 06.10.2026", transferable.getTransferData(DataFlavor.stringFlavor))
    assertEquals(DataFlavor.stringFlavor, transferable.transferDataFlavors.first())
  }

  @Test
  fun `transferable gives every text flavor that a plain text copy gives`() {
    val transferable = VcsCopiedCommits.createTransferable("Fix the bug", listOf(FIRST))
    val plainCopy = StringSelection("Fix the bug")

    assertEquals(plainCopy.transferDataFlavors.toList() + VcsCopiedCommits.FLAVOR, transferable.transferDataFlavors.toList())
    plainCopy.transferDataFlavors.forEach { flavor -> assertTrue(transferable.isDataFlavorSupported(flavor)) }
  }

  @Test
  fun `read returns the copied commits in order`() {
    val transferable = VcsCopiedCommits.createTransferable("text", listOf(FIRST, SECOND))

    assertEquals(VcsCopiedCommitsReadResult.Success(listOf(FIRST, SECOND)), VcsCopiedCommits.read(transferable))
  }

  @Test
  fun `read keeps quotes, line breaks and non-ASCII text in a subject`() {
    val commit = FIRST.copy(subject = "Quote \" and \\ slash\nsecond line \u00fcn\u00efc\u00f6d\u00e9")
    val transferable = VcsCopiedCommits.createTransferable("text", listOf(commit))

    assertEquals(VcsCopiedCommitsReadResult.Success(listOf(commit)), VcsCopiedCommits.read(transferable))
  }

  @Test
  fun `read keeps a commit with only a hash and a root`() {
    val commit = VcsCopiedCommit(hash = FIRST.hash, rootPath = FIRST.rootPath)
    val transferable = VcsCopiedCommits.createTransferable("text", listOf(commit))

    assertEquals(VcsCopiedCommitsReadResult.Success(listOf(commit)), VcsCopiedCommits.read(transferable))
  }

  @Test
  fun `read survives the serialization that the system clipboard applies`() {
    val transferable = VcsCopiedCommits.createTransferable("text", listOf(FIRST))
    val restored = serializeAndRestore(transferable.getTransferData(VcsCopiedCommits.FLAVOR))

    assertEquals(VcsCopiedCommitsReadResult.Success(listOf(FIRST)), VcsCopiedCommits.read(FixedTransferable(restored)))
  }

  @Test
  fun `read ignores a field that a newer IDE adds`() {
    val json = """[{"hash":"${FIRST.hash}","rootPath":"${FIRST.rootPath}","newField":42}]"""

    assertEquals(
      VcsCopiedCommitsReadResult.Success(listOf(VcsCopiedCommit(hash = FIRST.hash, rootPath = FIRST.rootPath))),
      VcsCopiedCommits.read(FixedTransferable(json)),
    )
  }

  @Test
  fun `read fails for a transferable without the commits flavor`() {
    assertInstanceOf(VcsCopiedCommitsReadResult.Failure::class.java, VcsCopiedCommits.read(StringSelection("Fix the bug")))
  }

  @Test
  fun `read fails for an empty commit list`() {
    val transferable = VcsCopiedCommits.createTransferable("", emptyList())

    assertInstanceOf(VcsCopiedCommitsReadResult.Failure::class.java, VcsCopiedCommits.read(transferable))
  }

  @Test
  fun `read fails for malformed data`() {
    assertInstanceOf(VcsCopiedCommitsReadResult.Failure::class.java, VcsCopiedCommits.read(FixedTransferable("not json")))
    assertInstanceOf(VcsCopiedCommitsReadResult.Failure::class.java, VcsCopiedCommits.read(FixedTransferable("""[{"subject":"no hash"}]""")))
  }

  @Test
  fun `read fails for data that is not a string`() {
    assertInstanceOf(VcsCopiedCommitsReadResult.Failure::class.java, VcsCopiedCommits.read(FixedTransferable(42)))
  }

  @Test
  fun `read fails when the clipboard owner cannot give the data`() {
    val failing = object : FixedTransferable("") {
      override fun getTransferData(flavor: DataFlavor?): Any = throw IOException("The clipboard owner went away")
    }

    val result = VcsCopiedCommits.read(failing)

    assertInstanceOf(VcsCopiedCommitsReadResult.Failure::class.java, result)
    assertTrue((result as VcsCopiedCommitsReadResult.Failure).error.contains("The clipboard owner went away"))
  }

  @Test
  fun `read fails when the flavor disappears between the check and the read`() {
    val failing = object : FixedTransferable("") {
      override fun getTransferData(flavor: DataFlavor?): Any = throw UnsupportedFlavorException(flavor)
    }

    assertInstanceOf(VcsCopiedCommitsReadResult.Failure::class.java, VcsCopiedCommits.read(failing))
  }

  @Test
  fun `transferable rejects an unknown flavor`() {
    val transferable = VcsCopiedCommits.createTransferable("text", listOf(FIRST))

    assertFalse(transferable.isDataFlavorSupported(DataFlavor.imageFlavor))
    assertThrows<UnsupportedFlavorException> { transferable.getTransferData(DataFlavor.imageFlavor) }
  }

  private open class FixedTransferable(private val commitsData: Any) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(VcsCopiedCommits.FLAVOR)

    override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean = VcsCopiedCommits.FLAVOR == flavor

    override fun getTransferData(flavor: DataFlavor?): Any = commitsData
  }

  private fun serializeAndRestore(value: Any): Any {
    val bytes = ByteArrayOutputStream().also { output -> ObjectOutputStream(output).use { it.writeObject(value) } }.toByteArray()
    return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }
  }

  private companion object {
    val FIRST = VcsCopiedCommit(
      hash = "1111111111111111111111111111111111111111",
      rootPath = "/repo",
      subject = "Fix the bug",
      author = "Ann",
      commitTimeMs = 1_759_700_000_000,
    )
    val SECOND = VcsCopiedCommit(
      hash = "2222222222222222222222222222222222222222",
      rootPath = "/repo/sub",
      subject = "Add the test",
      author = "Bob",
      commitTimeMs = 1_759_600_000_000,
    )
  }
}
