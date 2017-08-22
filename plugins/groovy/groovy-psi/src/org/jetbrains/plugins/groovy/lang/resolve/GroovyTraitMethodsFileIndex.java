/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.impl.compiled.ClassFileStubBuilder;
import com.intellij.psi.impl.compiled.OutOfOrderInnerClassException;
import com.intellij.psi.impl.compiled.StubBuildingVisitor;
import com.intellij.psi.impl.java.stubs.PsiJavaFileStub;
import com.intellij.psi.impl.java.stubs.impl.PsiJavaFileStubImpl;
import com.intellij.psi.stubs.SerializationManagerEx;
import com.intellij.psi.stubs.SerializerNotFoundException;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.FileBasedIndex.InputFilter;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.*;

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static com.intellij.psi.impl.compiled.ClsFileImpl.EMPTY_ATTRIBUTES;
import static org.jetbrains.org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.jetbrains.org.objectweb.asm.Opcodes.ACC_SYNTHETIC;

public class GroovyTraitMethodsFileIndex extends SingleEntryFileBasedIndexExtension<PsiJavaFileStub> implements DataExternalizer<PsiJavaFileStub> {
  public static final ID<Integer, PsiJavaFileStub> INDEX_ID = ID.create("groovy.trait.methods");
  public static final String HELPER_SUFFIX = "$Trait$Helper.class";

  private static final Logger LOG = Logger.getInstance(GroovyTraitMethodsFileIndex.class);

  private final InputFilter myFilter;
  private final SingleEntryIndexer<PsiJavaFileStub> myIndexer;

  public GroovyTraitMethodsFileIndex() {
    myFilter = new DefaultFileTypeSpecificInputFilter(JavaClassFileType.INSTANCE) {
      @Override
      public boolean acceptInput(@NotNull VirtualFile file) {
        return StringUtil.endsWith(file.getNameSequence(), HELPER_SUFFIX);
      }
    };
    myIndexer = new SingleEntryIndexer<PsiJavaFileStub>(true) {
      @Override
      protected PsiJavaFileStub computeValue(@NotNull FileContent inputData) {
        return index(inputData.getFile(), inputData.getContent());
      }
    };
  }

  @NotNull
  @Override
  public ID<Integer, PsiJavaFileStub> getName() {
    return INDEX_ID;
  }

  @Override
  public int getVersion() {
    return ClassFileStubBuilder.STUB_VERSION + 1;
  }

  @NotNull
  @Override
  public InputFilter getInputFilter() {
    return myFilter;
  }

  @NotNull
  @Override
  public SingleEntryIndexer<PsiJavaFileStub> getIndexer() {
    return myIndexer;
  }

  @NotNull
  @Override
  public DataExternalizer<PsiJavaFileStub> getValueExternalizer() {
    return this;
  }

  private static PsiJavaFileStub index(VirtualFile file, byte[] content) {
    try {
      PsiJavaFileStub root = new PsiJavaFileStubImpl("", true);
      new ClassReader(content).accept(new GrTraitMethodVisitor(file, root), EMPTY_ATTRIBUTES, ClassReader.SKIP_CODE);
      return root;
    }
    catch (OutOfOrderInnerClassException e) {
      if (LOG.isTraceEnabled()) LOG.trace(file.getPath());
      return null;
    }
    catch (Exception e) {
      LOG.info(file.getPath(), e);
      return null;
    }
  }

  @Override
  public void save(@NotNull DataOutput out, PsiJavaFileStub value) throws IOException {
    BufferExposingByteArrayOutputStream buffer = new BufferExposingByteArrayOutputStream();
    SerializationManagerEx.getInstanceEx().serialize(value, buffer);
    out.writeInt(buffer.size());
    out.write(buffer.getInternalBuffer(), 0, buffer.size());
  }

  @Override
  public PsiJavaFileStub read(@NotNull DataInput in) throws IOException {
    try {
      byte[] buffer = new byte[in.readInt()];
      in.readFully(buffer);
      return (PsiJavaFileStub)SerializationManagerEx.getInstanceEx().deserialize(new ByteArrayInputStream(buffer));
    }
    catch (SerializerNotFoundException e) {
      throw new IOException(e);
    }
  }

  private static class GrTraitMethodVisitor extends StubBuildingVisitor<VirtualFile> {
    public GrTraitMethodVisitor(VirtualFile file, StubElement root) {
      super(file, null, root, 0, null);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
      super.visit(version, access, "gr_trait_helper", null, null, null);
    }

    @Override
    public void visitSource(String source, String debug) { }

    @Override
    public void visitOuterClass(String owner, String name, String desc) { }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      return null;
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) { }

    @Nullable
    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
      return null;
    }

    @Nullable
    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
      if ((access & ACC_SYNTHETIC) == 0 && (access & ACC_STATIC) != 0 && name != null) {
        Type[] args = Type.getArgumentTypes(desc);
        if (args.length > 0 && args[0].getSort() == Type.OBJECT && CommonClassNames.JAVA_LANG_CLASS.equals(args[0].getClassName())) {
          return super.visitMethod(access, name, desc, signature, exceptions);
        }
      }

      return null;
    }
  }
}