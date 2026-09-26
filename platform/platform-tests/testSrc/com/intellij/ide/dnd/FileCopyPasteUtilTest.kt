// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.dnd

import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.datatransfer.DataFlavor
import java.lang.reflect.Array as ReflectArray

@TestApplication
class FileCopyPasteUtilTest {
  @Test
  fun nullTransferFlavorIsIgnored() {
    assertThat(FileCopyPasteUtil.isFileListFlavorAvailable(dataFlavors(null, DataFlavor.stringFlavor))).isFalse()
  }

  @Test
  fun nonFileTransferFlavorIsIgnored() {
    assertThat(FileCopyPasteUtil.isFileListFlavorAvailable(dataFlavors(DataFlavor.stringFlavor))).isFalse()
  }

  @Test
  fun javaFileListFlavorIsRecognizedWithNullTransferFlavor() {
    assertThat(FileCopyPasteUtil.isFileListFlavorAvailable(dataFlavors(null, DataFlavor.javaFileListFlavor))).isTrue()
  }

  private fun dataFlavors(vararg flavors: DataFlavor?): Array<DataFlavor> {
    @Suppress("UNCHECKED_CAST")
    val array = ReflectArray.newInstance(DataFlavor::class.java, flavors.size) as Array<DataFlavor>
    flavors.forEachIndexed { index, flavor -> ReflectArray.set(array, index, flavor) }
    return array
  }
}
