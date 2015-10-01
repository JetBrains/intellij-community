/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.compiled.StubBuildingVisitor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.FileBasedIndex.InputFilter;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.FieldVisitor;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static com.intellij.util.io.DataInputOutputUtil.*;
import static com.intellij.util.io.IOUtil.readUTF;
import static com.intellij.util.io.IOUtil.writeUTF;
import static org.jetbrains.org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static org.jetbrains.org.objectweb.asm.Opcodes.ASM5;
import static org.jetbrains.plugins.groovy.lang.resolve.GroovyTraitFieldsFileIndex.TraitFieldDescriptor;

public class GroovyTraitFieldsFileIndex
  extends FileBasedIndexExtension<Integer, Collection<TraitFieldDescriptor>>
  implements DataIndexer<Integer, Collection<TraitFieldDescriptor>, FileContent>,
             DataExternalizer<Collection<TraitFieldDescriptor>> {

  public static final ID<Integer, Collection<TraitFieldDescriptor>> INDEX_ID = ID.create("groovy.trait.fields");
  public static final InputFilter INPUT_FILTER = new DefaultFileTypeSpecificInputFilter(JavaClassFileType.INSTANCE) {
    @Override
    public boolean acceptInput(@NotNull VirtualFile file) {
      return StringUtil.endsWith(file.getNameSequence(), "$Trait$FieldHelper.class");
    }
  };

  private static final Logger LOG = Logger.getInstance(GroovyTraitFieldsFileIndex.class);
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
  public DataIndexer<Integer, Collection<TraitFieldDescriptor>, FileContent> getIndexer() {
    return this;
  }

  @NotNull
  @Override
  public KeyDescriptor<Integer> getKeyDescriptor() {
    return EnumeratorIntegerDescriptor.INSTANCE;
  }

  @NotNull
  @Override
  public DataExternalizer<Collection<TraitFieldDescriptor>> getValueExternalizer() {
    return this;
  }

  @NotNull
  @Override
  public InputFilter getInputFilter() {
    return INPUT_FILTER;
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return 2;
  }

  @NotNull
  @Override
  public Map<Integer, Collection<TraitFieldDescriptor>> map(@NotNull FileContent inputData) {
    try {
      return mapInner(inputData);
    }
    catch (Exception e) {
      LOG.warn(e);
      return Collections.emptyMap();
    }
  }

  @NotNull
  private static Map<Integer, Collection<TraitFieldDescriptor>> mapInner(@NotNull FileContent inputData) {
    final int key = FileBasedIndex.getFileId(inputData.getFile());
    final Map<Integer, Collection<TraitFieldDescriptor>> result = ContainerUtil.newHashMap();
    new ClassReader(inputData.getContent()).accept(new ClassVisitor(ASM5) {

      @Override
      public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        processField(access, name, desc, signature);
        return null;
      }

      private void processField(int access, String name, String desc, String signature) {
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

        final String typeString = TypeInfo.createTypeText(StubBuildingVisitor.fieldType(desc, signature));
        if (typeString == null) return;

        final int delimiter = name.indexOf(DELIMITER);
        if (delimiter > -1) {
          name = name.substring(delimiter + DELIMITER.length());
        }

        Collection<TraitFieldDescriptor> values = result.get(key);
        if (values == null) {
          result.put(key, (values = ContainerUtil.newArrayList()));
        }

        values.add(new TraitFieldDescriptor(isStatic, isPublic, typeString, name));
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
    }, ClassReader.SKIP_FRAMES);
    return result;
  }

  @Override
  public void save(@NotNull DataOutput out, Collection<TraitFieldDescriptor> values) throws IOException {
    writeINT(out, values.size());
    for (TraitFieldDescriptor descriptor : values) {
      writeSINT(out, descriptor.isStatic ? 1 : 0);
      writeSINT(out, descriptor.isPublic ? 1 : 0);
      writeUTF(out, descriptor.typeString);
      writeUTF(out, descriptor.name);
    }
  }

  @Override
  public Collection<TraitFieldDescriptor> read(@NotNull DataInput in) throws IOException {
    int size = readINT(in);
    final Collection<TraitFieldDescriptor> result = ContainerUtil.newArrayListWithCapacity(size);
    while (size-- > 0) {
      result.add(new TraitFieldDescriptor(
        readSINT(in) > 0,
        readSINT(in) > 0,
        readUTF(in),
        readUTF(in)));
    }
    return result;
  }

  public static class TraitFieldDescriptor {
    public final boolean isStatic;
    public final boolean isPublic;
    public final @NotNull String typeString;
    public final @NotNull String name;

    TraitFieldDescriptor(boolean aStatic, boolean aPublic, @NotNull String typeString, @NotNull String name) {
      isStatic = aStatic;
      isPublic = aPublic;
      this.typeString = typeString;
      this.name = name;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      TraitFieldDescriptor that = (TraitFieldDescriptor)o;

      if (isStatic != that.isStatic) return false;
      if (isPublic != that.isPublic) return false;
      if (!typeString.equals(that.typeString)) return false;
      if (!name.equals(that.name)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = (isStatic ? 1 : 0);
      result = 31 * result + (isPublic ? 1 : 0);
      result = 31 * result + typeString.hashCode();
      result = 31 * result + name.hashCode();
      return result;
    }
  }
}
