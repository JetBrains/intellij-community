package org.intellij.images.completion;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;
import com.intellij.psi.file.FileLookupInfoProvider;
import com.intellij.util.indexing.FileBasedIndex;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.intellij.images.index.ImageInfoIndex;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class ImageLookupInfoProvider extends FileLookupInfoProvider {

  public Pair<String, String> getLookupInfo(@NotNull VirtualFile file, Project project) {
    final String[] s = new String[] {null};
    ImageInfoIndex.processValues(file, new FileBasedIndex.ValueProcessor<ImageInfoIndex.ImageInfo>() {
      @SuppressWarnings({"HardCodedStringLiteral"})
      public boolean process(VirtualFile file, ImageInfoIndex.ImageInfo value) {
        s[0] = String.format("%sx%s", value.width, value.height);
        return true;
      }
    }, project);

    return s[0] == null ? null : new Pair<String, String>(file.getName(), s[0]);
  }

  @NotNull
  @Override
  public FileType[] getFileTypes() {
    return new FileType[]{ImageFileTypeManager.getInstance().getImageFileType()};
  }
}
