package com.intellij.findUsagesMl

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager

internal class SearchSessionData(
  val queryNames: List<String>,
  queryFiles: List<VirtualFile>,
  candidateFiles: List<VirtualFile>,
) {
  private val queryFileUrls: List<String> = queryFiles.map { it.url }
  private val candidateFileUrls: List<String> = candidateFiles.map { it.url }

  fun queryFiles(): List<VirtualFile> {
    val virtualFileManager: VirtualFileManager = VirtualFileManager.getInstance()
    return queryFileUrls.mapNotNull { virtualFileManager.findFileByUrl(it) }
  }

  fun candidateFiles(): List<VirtualFile> {
    val virtualFileManager: VirtualFileManager = VirtualFileManager.getInstance()
    return candidateFileUrls.mapNotNull { virtualFileManager.findFileByUrl(it) }
  }
}
