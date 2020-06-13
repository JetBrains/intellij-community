// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.compiled.SignatureParsing;
import com.intellij.psi.impl.compiled.StubBuildingVisitor;
import com.intellij.util.SmartList;
import com.intellij.util.cls.ClsFormatException;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.FileBasedIndex.InputFilter;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.*;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.util.io.DataInputOutputUtilRt.readSeq;
import static com.intellij.openapi.util.io.DataInputOutputUtilRt.writeSeq;
import static com.intellij.psi.impl.compiled.ClsFileImpl.EMPTY_ATTRIBUTES;
import static com.intellij.util.io.IOUtil.readUTF;
import static com.intellij.util.io.IOUtil.writeUTF;
import static org.jetbrains.org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static org.jetbrains.plugins.groovy.lang.resolve.GroovyTraitFieldsFileIndex.TraitFieldDescriptor;

public class GroovyTraitFieldsFileIndex
  extends SingleEntryFileBasedIndexExtension<Collection<TraitFieldDescriptor>>
  implements DataExternalizer<Collection<TraitFieldDescriptor>> {

  public static final ID<Integer, Collection<TraitFieldDescriptor>> INDEX_ID = ID.create("groovy.trait.fields");
  public static final String HELPER_SUFFIX = "$Trait$FieldHelper.class";

  private static final InputFilter FILTER = new DefaultFileTypeSpecificInputFilter(JavaClassFileType.INSTANCE) {
    @Override
    public boolean acceptInput(@NotNull VirtualFile file) {
      return StringUtil.endsWith(file.getNameSequence(), HELPER_SUFFIX);
    }
  };

  private static final SingleEntryIndexer<Collection<TraitFieldDescriptor>> INDEXER = new SingleEntryIndexer<Collection<TraitFieldDescriptor>>(true) {
    @Override
    protected Collection<TraitFieldDescriptor> computeValue(@NotNull FileContent inputData) {
      return index(inputData);
    }
  };

  private static final String INSTANCE_PREFIX = "$ins";
  private static final String STATIC_PREFIX = "$static";
  private static final String PRIVATE_PREFIX = "$0";
  private static final String PUBLIC_PREFIX = "$1";
  private static final String DELIMITER = "__";

  @NotNull
  @Override
  public ID<Integer, Collection<TraitFieldDescriptor>> getName() {
    return INDEX_ID;
  }

  @NotNull
  @Override
  public SingleEntryIndexer<Collection<TraitFieldDescriptor>> getIndexer() {
    return INDEXER;
  }

  @NotNull
  @Override
  public DataExternalizer<Collection<TraitFieldDescriptor>> getValueExternalizer() {
    return this;
  }

  @NotNull
  @Override
  public InputFilter getInputFilter() {
    return FILTER;
  }

  @Override
  public int getVersion() {
    return 5;
  }

  private static Collection<TraitFieldDescriptor> index(FileContent inputData) {
    final Collection<TraitFieldDescriptor> values = new ArrayList<>();

    new ClassReader(inputData.getContent()).accept(new ClassVisitor(Opcodes.API_VERSION) {
      @Override
      public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        return new FieldVisitor(Opcodes.API_VERSION) {
          private final List<String> annotations = new SmartList<>();

          @Override
          public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            return StubBuildingVisitor.getAnnotationTextCollector(descriptor, annotations::add);
          }

          @Override
          public void visitEnd() {
            processField(access, name, desc, signature, annotations);
          }
        };
      }

      private void processField(int access, String name, String desc, String signature, List<String> annotations) {
        if ((access & ACC_SYNTHETIC) == 0) return;

        final boolean isStatic;
        final boolean isPublic;
        Pair<Boolean, String> p;
        if ((p = parse(STATIC_PREFIX, INSTANCE_PREFIX, name)).first != null) {
          isStatic = p.first;
          name = p.second;
        }
        else {
          return;
        }
        if ((p = parse(PUBLIC_PREFIX, PRIVATE_PREFIX, name)).first != null) {
          isPublic = p.first;
          name = p.second;
        }
        else {
          return;
        }

        final String typeString = fieldType(desc, signature);
        if (typeString == null) return;

        final int delimiter = name.indexOf(DELIMITER);
        if (delimiter > -1) {
          name = name.substring(delimiter + DELIMITER.length());
        }

        byte flags = (byte)((isPublic ? TraitFieldDescriptor.PUBLIC : 0) | (isStatic ? TraitFieldDescriptor.STATIC : 0));
        values.add(new TraitFieldDescriptor(flags, typeString, name, annotations));
      }

      private Pair<Boolean, String> parse(String prefix, String prefix2, String input) {
        if (input.startsWith(prefix)) {
          return Pair.create(true, input.substring(prefix.length()));
        }
        else if (input.startsWith(prefix2)) {
          return Pair.create(false, input.substring(prefix2.length()));
        }
        else {
          return Pair.create(null, input);
        }
      }

      private String fieldType(String desc, String signature) {
        if (signature != null) {
          try {
            return SignatureParsing.parseTypeString(new StringCharacterIterator(signature), StubBuildingVisitor.GUESSING_MAPPER);
          }
          catch (ClsFormatException ignored) { }
        }

        String raw = Type.getType(desc).getClassName();
        return StubBuildingVisitor.GUESSING_MAPPER.fun(raw);
      }
    }, EMPTY_ATTRIBUTES, ClassReader.SKIP_CODE);

    return values;
  }

  @Override
  public void save(@NotNull DataOutput out, Collection<TraitFieldDescriptor> values) throws IOException {
    writeSeq(out, values, descriptor -> {
      out.writeByte(descriptor.flags);
      writeUTF(out, descriptor.typeString);
      writeUTF(out, descriptor.name);
      writeSeq(out, descriptor.annotations, it -> writeUTF(out, it));
    });
  }

  @Override
  public Collection<TraitFieldDescriptor> read(@NotNull DataInput in) throws IOException {
    return readSeq(in, () -> new TraitFieldDescriptor(in.readByte(), readUTF(in), readUTF(in), readSeq(in, () -> readUTF(in))));
  }

  public static class TraitFieldDescriptor {
    public static final byte PUBLIC = 0x01;
    public static final byte STATIC = 0x02;

    public final byte flags;
    public final String typeString;
    public final String name;
    public final List<String> annotations;

    private TraitFieldDescriptor(byte flags, @NotNull String typeString, @NotNull String name, @NotNull List<String> annotations) {
      this.flags = flags;
      this.typeString = typeString;
      this.name = name;
      this.annotations = annotations;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      TraitFieldDescriptor that = (TraitFieldDescriptor)o;

      if (flags != that.flags) return false;
      if (!typeString.equals(that.typeString)) return false;
      if (!name.equals(that.name)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = flags;
      result = 31 * result + typeString.hashCode();
      result = 31 * result + name.hashCode();
      return result;
    }
  }
}