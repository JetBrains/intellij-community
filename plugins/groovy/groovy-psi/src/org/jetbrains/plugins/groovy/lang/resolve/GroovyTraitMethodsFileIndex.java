/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.psi.stubs.SerializationManagerEx;
import com.intellij.psi.stubs.SerializerNotFoundException;
import com.intellij.psi.stubs.Stub;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.FileBasedIndex.InputFilter;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.*;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.ByteArraySequenceExternalizer;

import java.io.ByteArrayInputStream;
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
  private static final String HELPER_SUFFIX = "$Trait$Helper.class";

  private final InputFilter myFilter;
  private final SingleEntryIndexer<ByteArraySequence> myIndexer;

  public GroovyTraitMethodsFileIndex() {
    myFilter = new DefaultFileTypeSpecificInputFilter(JavaClassFileType.INSTANCE) {
      @Override
      public boolean acceptInput(@NotNull VirtualFile file) {
        return StringUtil.endsWith(file.getNameSequence(), HELPER_SUFFIX);
      }
    };
    myIndexer = new SingleEntryIndexer<ByteArraySequence>(false) {
      @Override
      protected ByteArraySequence computeValue(@NotNull FileContent inputData) {
        return serialize(index(inputData.getFile(), inputData.getContent()));
      }
    };
  }

  @NotNull
  @Override
  public ID<Integer, ByteArraySequence> getName() {
    return INDEX_ID;
  }

  @Override
  public int getVersion() {
    return ClassFileStubBuilder.STUB_VERSION + 2;
  }

  @NotNull
  @Override
  public InputFilter getInputFilter() {
    return myFilter;
  }

  @NotNull
  @Override
  public SingleEntryIndexer<ByteArraySequence> getIndexer() {
    return myIndexer;
  }

  @NotNull
  @Override
  public DataExternalizer<ByteArraySequence> getValueExternalizer() {
    return ByteArraySequenceExternalizer.INSTANCE;
  }

  @Contract("null -> null")
  private static ByteArraySequence serialize(@Nullable PsiJavaFileStub stub) {
    if (stub == null) return null;
    BufferExposingByteArrayOutputStream buffer = new BufferExposingByteArrayOutputStream();
    ApplicationManager.getApplication().runReadAction(() -> SerializationManagerEx.getInstanceEx().serialize(stub, buffer));
    return new ByteArraySequence(buffer.getInternalBuffer(), 0, buffer.size());
  }

  @Nullable
  private static PsiJavaFileStub index(@NotNull VirtualFile file, @NotNull byte[] content) {
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

  @NotNull
  public static Collection<PsiMethod> getStaticTraitMethods(@NotNull ClsClassImpl trait) {
    PsiFile psiFile = trait.getContainingFile();
    if (!(psiFile instanceof PsiJavaFile)) return Collections.emptyList();

    VirtualFile traitFile = psiFile.getVirtualFile();
    if (traitFile == null) return Collections.emptyList();

    VirtualFile helperFile = traitFile.getParent().findChild(trait.getName() + HELPER_SUFFIX);
    if (helperFile == null) return Collections.emptyList();

    int key = FileBasedIndex.getFileId(helperFile);

    List<ByteArraySequence> byteSequences = FileBasedIndex.getInstance().getValues(INDEX_ID, key, trait.getResolveScope());
    if (byteSequences.isEmpty()) return Collections.emptyList();

    SerializationManagerEx manager = SerializationManagerEx.getInstanceEx();
    List<PsiMethod> result = new ArrayList<>();
    for (ByteArraySequence byteSequence : byteSequences) {
      Stub root;
      try {
        root = manager.deserialize(new ByteArrayInputStream(byteSequence.getBytes()));
        ((PsiJavaFileStubImpl)root).setPsi((PsiJavaFile)psiFile);
      }
      catch (SerializerNotFoundException e) {
        LOG.warn(e);
        continue;
      }
      for (Object childStub : root.getChildrenStubs().get(0).getChildrenStubs()) {
        if (childStub instanceof PsiMethodStub) {
          result.add(((PsiMethodStub)childStub).getPsi());
        }
      }
    }
    return result;
  }
}