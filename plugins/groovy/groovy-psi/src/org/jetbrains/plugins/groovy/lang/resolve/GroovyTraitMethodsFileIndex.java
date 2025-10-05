// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.compiled.ClassFileStubBuilder;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.impl.compiled.OutOfOrderInnerClassException;
import com.intellij.psi.impl.compiled.StubBuildingVisitor;
import com.intellij.psi.impl.java.stubs.PsiJavaFileStub;
import com.intellij.psi.impl.java.stubs.PsiMethodStub;
import com.intellij.psi.impl.java.stubs.impl.PsiJavaFileStubImpl;
import com.intellij.psi.stubs.*;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.FileBasedIndex.InputFilter;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.*;
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.psi.impl.compiled.ClsFileImpl.EMPTY_ATTRIBUTES;
import static org.jetbrains.org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.jetbrains.org.objectweb.asm.Opcodes.ACC_SYNTHETIC;

public class GroovyTraitMethodsFileIndex extends SingleEntryFileBasedIndexExtension<ByteArraySequence> {
  private static final Logger LOG = Logger.getInstance(GroovyTraitMethodsFileIndex.class);
  private static final ID<Integer, ByteArraySequence> INDEX_ID = ID.create("groovy.trait.methods");
  private static final @NonNls String HELPER_SUFFIX = "$Trait$Helper.class";

  private final @NotNull StubTreeSerializer myStubTreeSerializer;

  public GroovyTraitMethodsFileIndex() {
    myStubTreeSerializer = new ShareableStubTreeSerializer();
  }

  @Override
  public @NotNull ID<Integer, ByteArraySequence> getName() {
    return INDEX_ID;
  }

  @Override
  public int getVersion() {
    return ClassFileStubBuilder.STUB_VERSION + 6;
  }

  @Override
  public @NotNull InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(JavaClassFileType.INSTANCE) {
      @Override
      public boolean acceptInput(@NotNull VirtualFile file) {
        return StringUtil.endsWith(file.getNameSequence(), HELPER_SUFFIX);
      }
    };
  }

  @Override
  public @NotNull SingleEntryIndexer<ByteArraySequence> getIndexer() {
    return new SingleEntryIndexer<>(false) {
      @Override
      protected ByteArraySequence computeValue(@NotNull FileContent inputData) {
        @Nullable PsiJavaFileStub stub = index(inputData.getFile(), inputData.getContent());
        if (stub == null) return null;
        BufferExposingByteArrayOutputStream buffer = new BufferExposingByteArrayOutputStream();
        myStubTreeSerializer.serialize(stub, buffer);
        return buffer.toByteArraySequence();
      }
    };
  }

  @Override
  public @NotNull DataExternalizer<ByteArraySequence> getValueExternalizer() {
    return new DataExternalizer<>() {
      @Override
      public void save(@NotNull DataOutput out, ByteArraySequence value) throws IOException {
        int length = value.length();
        DataInputOutputUtil.writeINT(out, length);
        out.write(value.getInternalBuffer(), value.getOffset(), length);
      }

      @Override
      public ByteArraySequence read(@NotNull DataInput in) throws IOException {
        int length = DataInputOutputUtil.readINT(in);
        byte[] buf = new byte[length];
        in.readFully(buf);
        return ByteArraySequence.create(buf);
      }
    };
  }

  public static @Nullable PsiJavaFileStub index(@NotNull VirtualFile file, byte @NotNull [] content) {
    try {
      PsiJavaFileStub root = new PsiJavaFileStubImpl(true);
      new ClassReader(content).accept(new GrTraitMethodVisitor(file, root), EMPTY_ATTRIBUTES, ClassReader.SKIP_CODE);
      new StubTree(root); // to ensure stubs are stored in DFS order
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

  private static class GrTraitMethodVisitor extends StubBuildingVisitor<VirtualFile> {
    GrTraitMethodVisitor(VirtualFile file, StubElement root) {
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

    @Override
    public @Nullable FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
      return null;
    }

    @Override
    public @Nullable MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
      if ((access & ACC_SYNTHETIC) == 0 && (access & ACC_STATIC) != 0 && name != null) {
        Type[] args = Type.getArgumentTypes(desc);
        if (args.length > 0 && args[0].getSort() == Type.OBJECT && CommonClassNames.JAVA_LANG_CLASS.equals(args[0].getClassName())) {
          return super.visitMethod(access, name, desc, signature, exceptions);
        }
      }

      return null;
    }
  }

  public static @NotNull Collection<PsiMethod> getStaticTraitMethods(@NotNull ClsClassImpl trait) {
    PsiFile psiFile = trait.getContainingFile();
    if (!(psiFile instanceof PsiJavaFile)) return Collections.emptyList();

    VirtualFile traitFile = psiFile.getVirtualFile();
    if (traitFile == null) return Collections.emptyList();

    VirtualFile helperFile = traitFile.getParent().findChild(trait.getName() + HELPER_SUFFIX);
    if (helperFile == null) return Collections.emptyList();

    ByteArraySequence byteSequence = GrTraitUtil.GROOVY_TRAIT_METHODS_GIST.getFileData(trait.getProject(), helperFile);
    if (byteSequence == null) return Collections.emptyList();

    StubTreeSerializer serializer = new ShareableStubTreeSerializer();

    List<PsiMethod> result = new ArrayList<>();
    Stub root;
    try {
      root = serializer.deserialize(byteSequence.toInputStream());
      ((PsiJavaFileStubImpl)root).setPsi((PsiJavaFile)psiFile);
    }
    catch (SerializerNotFoundException e) {
      LOG.warn(e);
      return result;
    }
    for (Object childStub : root.getChildrenStubs().get(0).getChildrenStubs()) {
      if (childStub instanceof PsiMethodStub) {
        result.add(((PsiMethodStub)childStub).getPsi());
      }
    }
    return result;
  }
}