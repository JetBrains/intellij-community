package org.jetbrains.android;

import com.android.resources.ResourceType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.xml.NanoXmlUtil;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.ResourceEntry;
import org.jetbrains.annotations.NonNls;
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
public class AndroidValueResourcesIndex extends FileBasedIndexExtension<ResourceEntry, Set<AndroidValueResourcesIndex.MyResourceInfo>> {
  public static final ID<ResourceEntry, Set<MyResourceInfo>> INDEX_ID = ID.create("android.value.resources.index");

  @NonNls private static final String RESOURCES_ROOT_TAG = "resources";
  @NonNls private static final String NAME_ATTRIBUTE_VALUE = "name";
  @NonNls private static final String TYPE_ATTRIBUTE_VALUE = "type";

  private final FileBasedIndex.InputFilter myInputFilter = new FileBasedIndex.InputFilter() {
    @Override
    public boolean acceptInput(final VirtualFile file) {
      return (file.getFileSystem() == LocalFileSystem.getInstance() || file.getFileSystem() instanceof TempFileSystem) &&
             file.getFileType() == StdFileTypes.XML;
    }
  };

  private final DataIndexer<ResourceEntry, Set<MyResourceInfo>, FileContent> myIndexer =
    new DataIndexer<ResourceEntry, Set<MyResourceInfo>, FileContent>() {
      @Override
      @NotNull
      public Map<ResourceEntry, Set<MyResourceInfo>> map(FileContent inputData) {
        if (!isSimilarFile(inputData)) {
          return Collections.emptyMap();
        }
        final PsiFile file = inputData.getPsiFile();

        if (!(file instanceof XmlFile)) {
          return Collections.emptyMap();
        }
        final Map<ResourceEntry, Set<MyResourceInfo>> result = new HashMap<ResourceEntry, Set<MyResourceInfo>>();

        file.accept(new XmlRecursiveElementVisitor() {
          @Override
          public void visitXmlTag(XmlTag tag) {
            super.visitXmlTag(tag);
            final String resName = tag.getAttributeValue(NAME_ATTRIBUTE_VALUE);

            if (resName == null) {
              return;
            }
            final String tagName = tag.getName();
            final String resTypeStr;

            if ("item".equals(tagName)) {
              resTypeStr = tag.getAttributeValue(TYPE_ATTRIBUTE_VALUE);
            }
            else {
              resTypeStr = AndroidCommonUtils.getResourceTypeByTagName(tagName);
            }
            final ResourceType resType = resTypeStr != null ? ResourceType.getEnum(resTypeStr) : null;

            if (resType == null) {
              return;
            }
            final int offset = tag.getTextRange().getStartOffset();

            if (resType == ResourceType.ATTR) {
              final XmlTag parentTag = tag.getParentTag();
              final String contextName = parentTag != null ? parentTag.getAttributeValue(NAME_ATTRIBUTE_VALUE) : null;
              processResourceEntry(new ResourceEntry(resTypeStr, resName, contextName != null ? contextName : ""), result, offset);
            }
            else {
              processResourceEntry(new ResourceEntry(resTypeStr, resName, ""), result, offset);
            }
          }
        });

        return result;
      }
    };

  private static boolean isSimilarFile(FileContent inputData) {
    if (CharArrayUtil.indexOf(inputData.getContentAsText(), "<" + RESOURCES_ROOT_TAG, 0) < 0) {
      return false;
    }
    final boolean[] ourRootTag = {false};

    NanoXmlUtil.parse(CharArrayUtil.readerFromCharSequence(inputData.getContentAsText()), new NanoXmlUtil.IXMLBuilderAdapter() {
      @Override
      public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr)
        throws Exception {
        ourRootTag[0] = RESOURCES_ROOT_TAG.equals(name) && nsPrefix == null;
        stop();
      }
    });
    return ourRootTag[0];
  }

  private static void processResourceEntry(@NotNull ResourceEntry entry,
                                           @NotNull Map<ResourceEntry, Set<MyResourceInfo>> result,
                                           int offset) {
    final MyResourceInfo info = new MyResourceInfo(entry, offset);
    result.put(entry, Collections.singleton(info));
    addEntryToMap(info, createTypeMarkerKey(entry.getType()), result);
    addEntryToMap(info, createTypeNameMarkerKey(entry.getType(), entry.getName()), result);
  }

  private static void addEntryToMap(MyResourceInfo info, ResourceEntry marker, Map<ResourceEntry, Set<MyResourceInfo>> result) {
    Set<MyResourceInfo> set = result.get(marker);

    if (set == null) {
      set = new HashSet<MyResourceInfo>();
      result.put(marker, set);
    }
    set.add(info);
  }

  @NotNull
  public static ResourceEntry createTypeMarkerKey(String type) {
    return createTypeNameMarkerKey(type, "TYPE_MARKER_RESOURCE");
  }

  @NotNull
  public static ResourceEntry createTypeNameMarkerKey(String type, String name) {
    return new ResourceEntry(type, normalizeDelimiters(name), "TYPE_MARKER_CONTEXT");
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
  
  private final DataExternalizer<Set<MyResourceInfo>> myValueExternalizer = new DataExternalizer<Set<MyResourceInfo>>() {
    @Override
    public void save(DataOutput out, Set<MyResourceInfo> value) throws IOException {
      out.writeInt(value.size());

      for (MyResourceInfo entry : value) {
        out.writeUTF(entry.getResourceEntry().getType());
        out.writeUTF(entry.getResourceEntry().getName());
        out.writeUTF(entry.getResourceEntry().getContext());
        out.writeInt(entry.getOffset());
      }
    }

    @Nullable
    @Override
    public Set<MyResourceInfo> read(DataInput in) throws IOException {
      final int size = in.readInt();

      if (size == 0) {
        return Collections.emptySet();
      }
      final Set<MyResourceInfo> result = new HashSet<MyResourceInfo>(size);

      for (int i = 0; i < size; i++) {
        final String type = in.readUTF();
        final String name = in.readUTF();
        final String context = in.readUTF();
        final int offset = in.readInt();
        result.add(new MyResourceInfo(new ResourceEntry(type, name, context), offset));
      }
      return result;
    }
  };

  @NotNull
  @Override
  public ID<ResourceEntry, Set<MyResourceInfo>> getName() {
    return INDEX_ID;
  }

  @NotNull
  @Override
  public DataIndexer<ResourceEntry, Set<MyResourceInfo>, FileContent> getIndexer() {
    return myIndexer;  
  }

  @Override
  public KeyDescriptor<ResourceEntry> getKeyDescriptor() {
    return myKeyDescriptor;
  }

  @Override
  public DataExternalizer<Set<MyResourceInfo>> getValueExternalizer() {
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
    return 5;
  }

  public static class MyResourceInfo {
    private final ResourceEntry myResourceEntry;
    private final int myOffset;

    private MyResourceInfo(@NotNull ResourceEntry resourceEntry, int offset) {
      myResourceEntry = resourceEntry;
      myOffset = offset;
    }

    @NotNull
    public ResourceEntry getResourceEntry() {
      return myResourceEntry;
    }

    public int getOffset() {
      return myOffset;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      MyResourceInfo info = (MyResourceInfo)o;

      if (myOffset != info.myOffset) {
        return false;
      }
      if (!myResourceEntry.equals(info.myResourceEntry)) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      int result = myResourceEntry.hashCode();
      result = 31 * result + myOffset;
      return result;
    }
  }
}
