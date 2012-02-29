package org.jetbrains.android;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.xml.NanoXmlUtil;
import org.jetbrains.android.util.ResourceEntry;
import org.jetbrains.android.util.ValueResourcesFileParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidValueResourcesIndex extends FileBasedIndexExtension<ResourceEntry, Set<ResourceEntry>> {
  public static final ID<ResourceEntry, Set<ResourceEntry>> INDEX_ID = ID.create("android.value.resources.index");

  private final FileBasedIndex.InputFilter myInputFilter = new FileBasedIndex.InputFilter() {
    @Override
    public boolean acceptInput(final VirtualFile file) {
      return (file.getFileSystem() == LocalFileSystem.getInstance() || file.getFileSystem() instanceof TempFileSystem) &&
             file.getFileType() == StdFileTypes.XML;
    }
  };

  private final DataIndexer<ResourceEntry, Set<ResourceEntry>, FileContent> myIndexer =
    new DataIndexer<ResourceEntry, Set<ResourceEntry>, FileContent>() {
      @Override
      @NotNull
      public Map<ResourceEntry, Set<ResourceEntry>> map(FileContent inputData) {

        if (CharArrayUtil.indexOf(inputData.getContentAsText(), "<resources", 0) < 0) {
          return Collections.emptyMap();
        }
        final Map<ResourceEntry, Set<ResourceEntry>> result = new HashMap<ResourceEntry, Set<ResourceEntry>>();

        NanoXmlUtil.parse(new ByteArrayInputStream(inputData.getContent()), new ValueResourcesFileParser() {
          @Override
          protected void stop() {
            throw new NanoXmlUtil.ParserStoppedException();
          }

          @Override
          protected void process(@NotNull ResourceEntry entry) {
            result.put(entry, Collections.<ResourceEntry>emptySet());

            final ResourceEntry typeMarkerEntry = createTypeMarkerEntry(entry.getType());
            Set<ResourceEntry> set = result.get(typeMarkerEntry);

            if (set == null) {
              set = new HashSet<ResourceEntry>();
              result.put(typeMarkerEntry, set);
            }
            set.add(entry);
          }
        });

        return result;
      }
    };

  public static ResourceEntry createTypeMarkerEntry(String type) {
    return new ResourceEntry(type, "TYPE_MARKER_RESOURCE");
  }

  private final KeyDescriptor<ResourceEntry> myKeyDescriptor = new KeyDescriptor<ResourceEntry>() {
    @Override
    public void save(DataOutput out, ResourceEntry value) throws IOException {
      out.writeUTF(value.getType());
      out.writeUTF(value.getName());
    }

    @Override
    public ResourceEntry read(DataInput in) throws IOException {
      final String resType = in.readUTF();
      final String resName = in.readUTF();
      return new ResourceEntry(resType, resName);
    }

    @Override
    public int getHashCode(ResourceEntry value) {
      return value.hashCode();
    }

    @Override
    public boolean isEqual(ResourceEntry val1, ResourceEntry val2) {
      return val1.equals(val2);
    }
  };
  
  private final DataExternalizer<Set<ResourceEntry>> myValueExternalizer = new DataExternalizer<Set<ResourceEntry>>() {
    @Override
    public void save(DataOutput out, Set<ResourceEntry> value) throws IOException {
      out.writeInt(value.size());

      for (ResourceEntry entry : value) {
        myKeyDescriptor.save(out, entry);
      }
    }

    @Nullable
    @Override
    public Set<ResourceEntry> read(DataInput in) throws IOException {
      final int size = in.readInt();

      if (size == 0) {
        return Collections.emptySet();
      }
      final Set<ResourceEntry> result = new HashSet<ResourceEntry>(size);

      for (int i = 0; i < size; i++) {
        result.add(myKeyDescriptor.read(in));
      }
      return result;
    }
  };

  @NotNull
  @Override
  public ID<ResourceEntry, Set<ResourceEntry>> getName() {
    return INDEX_ID;
  }

  @NotNull
  @Override
  public DataIndexer<ResourceEntry, Set<ResourceEntry>, FileContent> getIndexer() {
    return myIndexer;  
  }

  @Override
  public KeyDescriptor<ResourceEntry> getKeyDescriptor() {
    return myKeyDescriptor;
  }

  @Override
  public DataExternalizer<Set<ResourceEntry>> getValueExternalizer() {
    return myValueExternalizer;
  }

  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return myInputFilter;
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return 0;
  }
}
