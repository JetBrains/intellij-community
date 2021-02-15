// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.devkit.threadingModelHelper;

import com.intellij.compiler.instrumentation.FailSafeMethodVisitor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.org.objectweb.asm.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class TMHInstrumenter {
  static boolean instrument(ClassReader classReader, ClassVisitor classWriter, Set<TMHAssertionGenerator> generators) {
    AnnotatedMethodsCollector collector = new AnnotatedMethodsCollector(generators);
    classReader.accept(collector, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
    if (collector.annotatedMethods.isEmpty()) {
      return false;
    }
    Instrumenter instrumenter = new Instrumenter(classWriter, collector.annotatedMethods);
    classReader.accept(instrumenter, 0);
    return true;
  }

  public static boolean instrument(ClassReader classReader, ClassVisitor classWriter) {
    return instrument(classReader, classWriter, ContainerUtil.immutableSet(
      new TMHAssertionGenerator.AssertEdt(),
      new TMHAssertionGenerator.AssertBackgroundThread(),
      new TMHAssertionGenerator.AssertReadAccess(),
      new TMHAssertionGenerator.AssertWriteAccess()
    ));
  }

  private static class AnnotatedMethodsCollector extends ClassVisitor {
    final Set<TMHAssertionGenerator> assertionGenerators;
    final Map<MethodKey, TMHAssertionGenerator> annotatedMethods = new HashMap<>();

    AnnotatedMethodsCollector(Set<TMHAssertionGenerator> assertionGenerators) {
      super(Opcodes.API_VERSION);
      this.assertionGenerators = assertionGenerators;
    }

    @Override
    public MethodVisitor visitMethod(int access, final String name, final String methodDescriptor, String signature, String[] exceptions) {
      return new MethodVisitor(Opcodes.API_VERSION) {
        @Override
        public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
          for (TMHAssertionGenerator assertionGenerator : assertionGenerators) {
            if (assertionGenerator.isMyAnnotation(annotationDescriptor)) {
              return assertionGenerator.getAnnotationChecker(Opcodes.API_VERSION, () ->
                annotatedMethods.put(new MethodKey(name, methodDescriptor), assertionGenerator)
              );
            }
          }
          return super.visitAnnotation(annotationDescriptor, visible);
        }
      };
    }
  }

  private static class Instrumenter extends ClassVisitor {
    private final Map<MethodKey, TMHAssertionGenerator> myAnnotatedMethods;

    Instrumenter(ClassVisitor writer, Map<MethodKey, TMHAssertionGenerator> annotatedMethods) {
      super(Opcodes.API_VERSION, writer);
      myAnnotatedMethods = annotatedMethods;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
      TMHAssertionGenerator generator = myAnnotatedMethods.get(new MethodKey(name, descriptor));
      if (generator == null) {
        return super.visitMethod(access, name, descriptor, signature, exceptions);
      }
      return new FailSafeMethodVisitor(Opcodes.API_VERSION, super.visitMethod(access, name, descriptor, signature, exceptions)) {
        @Override
        public void visitCode() {
          generator.generateAssertion(mv);
          super.visitCode();
        }
      };
    }
  }

  private static class MethodKey {
    final String name;
    final String descriptor;

    private MethodKey(String name, String descriptor) {
      this.name = name;
      this.descriptor = descriptor;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + name.hashCode();
      result = 31 * result + descriptor.hashCode();
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      return obj == this ||
             obj instanceof MethodKey && ((MethodKey)obj).name.equals(name) && ((MethodKey)obj).descriptor.equals(descriptor);
    }
  }
}
