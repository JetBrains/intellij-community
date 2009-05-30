package org.intellij.images.fileTypes;

import com.intellij.lang.documentation.QuickDocumentationProvider;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.indexing.FileBasedIndex;
import org.intellij.images.index.ImageInfoIndex;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author spleaner
 */
public class ImageDocumentationProvider extends QuickDocumentationProvider {
  private static final int MAX_IMAGE_SIZE = 300;

  public String getQuickNavigateInfo(PsiElement element) {
    return null;
  }

  @Override
  public String generateDoc(PsiElement element, PsiElement originalElement) {
    final String[] result = new String[] {null};

    if (element instanceof PsiFileSystemItem && !((PsiFileSystemItem)element).isDirectory()) {
      final VirtualFile file = ((PsiFileSystemItem)element).getVirtualFile();
      if (file instanceof VirtualFileWithId) {
        ImageInfoIndex.processValues(file, new FileBasedIndex.ValueProcessor<ImageInfoIndex.ImageInfo>() {
          public boolean process(VirtualFile file, ImageInfoIndex.ImageInfo value) {
            int imageWidth = value.width;
            int imageHeight = value.height;

            int maxSize = Math.max(value.width, value.height);
            if (maxSize > MAX_IMAGE_SIZE) {
              double scaleFactor = (double)MAX_IMAGE_SIZE / (double)maxSize;
              imageWidth *= scaleFactor;
              imageHeight *= scaleFactor;
            }
            try {
              String path = file.getPath();
              if (SystemInfo.isWindows) {
                path = "/" + path;
              }
              final String url = new URI("file", null, path, null).toString();
              result[0] = String.format("<html><body><img src=\"%s\" width=\"%s\" height=\"%s\"><p>%sx%s, %sbpp</p><body></html>", url, imageWidth,
                                   imageHeight, value.width, value.height, value.bpp);
            }
            catch (URISyntaxException e) {
              // nothing
            }
            return true;
          }
        }, element.getProject());
      }
    }

    return result[0];
  }
}
