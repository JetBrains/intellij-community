import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager

class Conversions {
  fun test(
    urlManager: VirtualFileUrlManager,
    fileManager: VirtualFileManager,
    file: VirtualFile,
    fileUrl: VirtualFileUrl,
    plainUrl: String,
  ) {
    urlManager.<warning descr="Use 'toVirtualFileUrl(...)' to cache the VirtualFile-to-VirtualFileUrl association">getOrCreateFromUrl</warning>(file.url)
    fileManager.<warning descr="Use 'VirtualFileUrl.virtualFile' to reuse the cached VirtualFile">findFileByUrl</warning>(fileUrl.url)

    val urlFromFile = file.url
    urlManager.<warning descr="Use 'toVirtualFileUrl(...)' to cache the VirtualFile-to-VirtualFileUrl association">getOrCreateFromUrl</warning>(urlFromFile)

    val urlFromFileUrl = fileUrl.url
    fileManager.<warning descr="Use 'VirtualFileUrl.virtualFile' to reuse the cached VirtualFile">findFileByUrl</warning>(urlFromFileUrl)

    urlManager.getOrCreateFromUrl(plainUrl)
    fileManager.findFileByUrl(file.url)
    fileManager.findFileByUrl(plainUrl)

    var mutableUrl = fileUrl.url
    fileManager.findFileByUrl(mutableUrl)
    mutableUrl = plainUrl
  }
}
