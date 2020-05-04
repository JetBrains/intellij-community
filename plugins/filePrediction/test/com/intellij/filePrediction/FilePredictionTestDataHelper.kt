package com.intellij.filePrediction

import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor

internal object FilePredictionTestDataHelper {
  const val DEFAULT_MAIN_FILE = "MainTest"
  const val defaultTestData: String = "/plugins/filePrediction/testData/com/intellij/filePrediction"

  fun findChildRecursively(root: VirtualFile): VirtualFile? {
    val target = Ref<VirtualFile>()
    VfsUtilCore.visitChildrenRecursively(root, object : VirtualFileVisitor<Any?>() {
      override fun visitFile(file: VirtualFile): Boolean {
        val isMainTestFile = FileUtil.namesEqual(FileUtil.getNameWithoutExtension(file.name), DEFAULT_MAIN_FILE)

        if (isMainTestFile && target.isNull) {
          target.set(file)
        }
        return !isMainTestFile
      }
    })
    return target.get()
  }
}