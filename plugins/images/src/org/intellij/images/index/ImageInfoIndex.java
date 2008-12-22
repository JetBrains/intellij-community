package org.intellij.images.index;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.intellij.images.util.ImageInfoReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * @author spleaner
 */
public class ImageInfoIndex implements FileBasedIndexExtension {
  private static final Logger LOG = Logger.getInstance("#org.intellij.images.index.ImageInfoIndex");
  public static final ID<String, ImageInfo> INDEX_ID = ID.create("ImageFileInfoIndex");

  private FileBasedIndex.InputFilter myInputFilter = new FileBasedIndex.InputFilter() {
    public boolean acceptInput(final VirtualFile file) {
      return file.isValid() &&
             file.getFileSystem() == LocalFileSystem.getInstance() &&
             file.getFileType() == ImageFileTypeManager.getInstance().getImageFileType();
    }
  };

  private final DataExternalizer<ImageInfo> myValueExternalizer = new DataExternalizer<ImageInfo>() {
    public void save(final DataOutput out, final ImageInfo info) throws IOException {
      out.writeInt(info.width);
      out.writeInt(info.height);
      out.writeInt(info.bpp);
    }

    public ImageInfo read(final DataInput in) throws IOException {
      return new ImageInfo(in.readInt(), in.readInt(), in.readInt());
    }
  };

  private DataIndexer<String, ImageInfo, FileContent> myDataIndexer = new DataIndexer<String, ImageInfo, FileContent>() {
    @NotNull
    public Map<String, ImageInfo> map(FileContent inputData) {
      final ImageInfo info = fetchImageInfo(inputData.getFile());
      return info == null ? Collections.EMPTY_MAP : Collections.singletonMap(inputData.getFile().getUrl(), info);
    }
  };

  public ID getName() {
    return INDEX_ID;
  }

  public DataIndexer getIndexer() {
    return myDataIndexer;
  }

  @Nullable
  private static ImageInfo fetchImageInfo(@NotNull final VirtualFile file) {
    final ImageInfoReader.Info info = ImageInfoReader.getInfo(file.getPath());
    if (info != null) {
      return new ImageInfo(info.width, info.height, info.bpp);
    }

    return null;
  }

  public static void processValues(VirtualFile virtualFile, FileBasedIndex.ValueProcessor<ImageInfo> processor) {
    FileBasedIndex.getInstance().processValues(INDEX_ID, virtualFile.getUrl(), virtualFile, processor, VirtualFileFilter.ALL);
  }

  public KeyDescriptor getKeyDescriptor() {
    return new EnumeratorStringDescriptor();
  }

  public DataExternalizer getValueExternalizer() {
    return myValueExternalizer;
  }

  public FileBasedIndex.InputFilter getInputFilter() {
    return myInputFilter;
  }

  public boolean dependsOnFileContent() {
    return true;
  }

  public int getVersion() {
    return 0;
  }

  public int getCacheSize() {
    return DEFAULT_CACHE_SIZE;
  }

  public static class ImageInfo {
    public int width;
    public int height;
    public int bpp;

    public ImageInfo(int width, int height, int bpp) {
      this.width = width;
      this.height = height;
      this.bpp = bpp;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ImageInfo imageInfo = (ImageInfo)o;

      if (bpp != imageInfo.bpp) return false;
      if (height != imageInfo.height) return false;
      if (width != imageInfo.width) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = width;
      result = 31 * result + height;
      result = 31 * result + bpp;
      return result;
    }
  }
}
