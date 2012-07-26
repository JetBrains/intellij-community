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

        NanoXmlUtil.parse(CharArrayUtil.readerFromCharSequence(inputData.getContentAsText()), new ValueResourcesFileParser() {
          @Override
          protected void stop() {
            throw new NanoXmlUtil.ParserStoppedException();
          }

          @Override
          protected void process(@NotNull ResourceEntry entry) {
            result.put(createKey(entry), Collections.<ResourceEntry>emptySet());
            addEntryToMap(entry, createTypeMarkerKey(entry.getType()), result);
            addEntryToMap(entry, createTypeNameMarkerKey(entry.getType(), entry.getName()), result);
          }
        });

        return result;
      }
    };

  private static void addEntryToMap(ResourceEntry entry, ResourceEntry marker, Map<ResourceEntry, Set<ResourceEntry>> result) {
    Set<ResourceEntry> set = result.get(marker);

    if (set == null) {
      set = new HashSet<ResourceEntry>();
      result.put(marker, set);
    }
    set.add(entry);
  }

  @NotNull
  public static ResourceEntry createTypeMarkerKey(String type) {
    return createTypeNameMarkerKey(type, "TYPE_MARKER_RESOURCE");
  }

  @NotNull
  public static ResourceEntry createTypeNameMarkerKey(String type, String name) {
    return createKey(type, name, "TYPE_MARKER_CONTEXT");
  }

  @NotNull
  public static ResourceEntry createKey(ResourceEntry entry) {
    return createKey(entry.getType(), entry.getName(), entry.getContext());
  }

  @NotNull
  public static ResourceEntry createKey(String type, String name, String context) {
    return new ResourceEntry(type, normalizeDelimiters(name), context);
  }

  private static String normalizeDelimiters(String s) {
    final StringBuilder result = new StringBuilder();

    for (int i = 0, n = s.length(); i < n; i++) {
      final char c = s.charAt(i);
      if (Character.isLetterOrDigit(c)) {
        result.append(c);
      }
      else {
        result.append('_');
      }
    }
    return result.toString();
  }

  private final KeyDescriptor<ResourceEntry> myKeyDescriptor = new KeyDescriptor<ResourceEntry>() {
    @Override
    public void save(DataOutput out, ResourceEntry value) throws IOException {
      out.writeUTF(value.getType());
      out.writeUTF(value.getName());
      out.writeUTF(value.getContext());
    }

    @Override
    public ResourceEntry read(DataInput in) throws IOException {
      final String resType = in.readUTF();
      final String resName = in.readUTF();
      final String resContext = in.readUTF();
      return new ResourceEntry(resType, resName, resContext);
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
        out.writeUTF(entry.getType());
        out.writeUTF(entry.getName());
        out.writeUTF(entry.getContext());
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
        final String type = in.readUTF();
        final String name = in.readUTF();
        final String context = in.readUTF();
        result.add(new ResourceEntry(type, name, context));
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
    return 3;
  }
}
