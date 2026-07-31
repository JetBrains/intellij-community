import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.platform.workspace.storage.url.VirtualFileUrl;
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager;

class Conversions {
  void test(VirtualFileUrlManager urlManager,
            VirtualFileManager fileManager,
            VirtualFile file,
            VirtualFileUrl fileUrl,
            String plainUrl) {
    urlManager.<warning descr="Use 'VirtualFileUrls.toVirtualFileUrl(...)' to cache the VirtualFile-to-VirtualFileUrl association">getOrCreateFromUrl</warning>(file.getUrl());
    fileManager.<warning descr="Use 'VirtualFileUrl.virtualFile' to reuse the cached VirtualFile">findFileByUrl</warning>(fileUrl.getUrl());

    final String urlFromFile = file.getUrl();
    urlManager.<warning descr="Use 'VirtualFileUrls.toVirtualFileUrl(...)' to cache the VirtualFile-to-VirtualFileUrl association">getOrCreateFromUrl</warning>(urlFromFile);

    final String urlFromFileUrl = fileUrl.getUrl();
    fileManager.<warning descr="Use 'VirtualFileUrl.virtualFile' to reuse the cached VirtualFile">findFileByUrl</warning>(urlFromFileUrl);

    urlManager.getOrCreateFromUrl(plainUrl);
    fileManager.findFileByUrl(file.getUrl());
    fileManager.findFileByUrl(plainUrl);

    String mutableUrl = fileUrl.getUrl();
    fileManager.findFileByUrl(mutableUrl);
  }
}
