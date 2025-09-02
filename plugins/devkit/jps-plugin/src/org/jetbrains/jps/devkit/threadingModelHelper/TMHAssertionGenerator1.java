// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.devkit.threadingModelHelper;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.org.objectweb.asm.*;
import org.jetbrains.org.objectweb.asm.commons.Method;

import java.util.Set;

@ApiStatus.Internal
public class TMHAssertionGenerator1 implements TMHAssertionGenerator {
  private static final String DEFAULT_APPLICATION_MANAGER_CLASS_NAME = "com/intellij/openapi/application/ApplicationManager";
  private static final String DEFAULT_APPLICATION_CLASS_NAME = "com/intellij/openapi/application/Application";

  private static final String GENERATE_ASSERTION_PARAMETER = "generateAssertion";

  private static final String GET_APPLICATION_METHOD_NAME = "getApplication";
  private static final Type[] EMPTY = {};

  private final Type myAnnotationClass;
  private final Type myApplicationManagerClass;
  private final Type myApplicationClass;
  private final Method myGetApplicationMethod;
  private final Method myAssetionMethod;

  TMHAssertionGenerator1(Type annotationClass, Type applicationManagerClass, Type applicationClass, Method assetionMethod) {
    myAnnotationClass = annotationClass;
    myApplicationManagerClass = applicationManagerClass;
    myApplicationClass = applicationClass;
    myGetApplicationMethod = new Method(GET_APPLICATION_METHOD_NAME, applicationClass, EMPTY);
    myAssetionMethod = assetionMethod;
  }

  @Override
  public boolean isMyAnnotation(String annotationDescriptor) {
    return myAnnotationClass.getDescriptor().equals(annotationDescriptor);
  }

  @Override
  public AnnotationVisitor getAnnotationChecker(int api, Runnable onShouldGenerateAssertion) {
    return new AnnotationChecker(api, onShouldGenerateAssertion);
  }

  @Override
  public void generateAssertion(MethodVisitor writer, int methodStartLineNumber) {
    if (methodStartLineNumber != -1) {
      Label generatedCodeStart = new Label();
      writer.visitLabel(generatedCodeStart);
      writer.visitLineNumber(methodStartLineNumber, generatedCodeStart);
    }
    writer.visitMethodInsn(Opcodes.INVOKESTATIC, myApplicationManagerClass.getInternalName(), myGetApplicationMethod.getName(),
        myGetApplicationMethod.getDescriptor(), false);
    writer.visitMethodInsn(Opcodes.INVOKEINTERFACE, myApplicationClass.getInternalName(), myAssetionMethod.getName(),
        myAssetionMethod.getDescriptor(), true);
  }

  static final class AnnotationChecker extends AnnotationVisitor {
    private boolean myShouldGenerateAssertion = true;
    private final Runnable myOnShouldGenerateAssertion;

    private AnnotationChecker(int api, Runnable onShouldGenerateAssertion) {
      super(api);
      myOnShouldGenerateAssertion = onShouldGenerateAssertion;
    }

    @Override
    public void visit(String annotationParameterName, Object value) {
      if (GENERATE_ASSERTION_PARAMETER.equals(annotationParameterName) && Boolean.FALSE.equals(value)) {
        myShouldGenerateAssertion = false;
      }
    }

    @Override
    public void visitEnd() {
      if (myShouldGenerateAssertion) {
        myOnShouldGenerateAssertion.run();
      }
    }
  }

  // TODO avoid hardcoding annotation names
  @ApiStatus.Internal
  public static final class AssertEdt extends TMHAssertionGenerator1 {
    private static final String DEFAULT_ANNOTATION_CLASS_NAME = "com/intellij/util/concurrency/annotations/RequiresEdt";

    AssertEdt() {
      this(DEFAULT_ANNOTATION_CLASS_NAME, DEFAULT_APPLICATION_MANAGER_CLASS_NAME, DEFAULT_APPLICATION_CLASS_NAME);
    }

    @VisibleForTesting
    public AssertEdt(String annotationClassName, String applicationManagerClassName, String applicationClassName) {
      this(Type.getType("L" + annotationClassName + ";"),
          Type.getType("L" + applicationManagerClassName + ";"),
          Type.getType("L" + applicationClassName + ";"));
    }

    AssertEdt(Type annotationClass, Type applicationManagerClass, Type applicationClass) {
      super(annotationClass, applicationManagerClass, applicationClass, new Method("assertIsDispatchThread", "()V"));
    }
  }

  @ApiStatus.Internal
  public static final class AssertBackgroundThread extends TMHAssertionGenerator1 {
    private static final String DEFAULT_ANNOTATION_CLASS_NAME = "com/intellij/util/concurrency/annotations/RequiresBackgroundThread";

    AssertBackgroundThread() {
      this(DEFAULT_ANNOTATION_CLASS_NAME, DEFAULT_APPLICATION_MANAGER_CLASS_NAME, DEFAULT_APPLICATION_CLASS_NAME);
    }

    public AssertBackgroundThread(String annotationClassName, String applicationManagerClassName, String applicationClassName) {
      this(Type.getType("L" + annotationClassName + ";"),
           Type.getType("L" + applicationManagerClassName + ";"),
           Type.getType("L" + applicationClassName + ";"));
    }

    AssertBackgroundThread(Type annotationClass, Type applicationManagerClass, Type applicationClass) {
      super(annotationClass, applicationManagerClass, applicationClass, new Method("assertIsNonDispatchThread", "()V"));
    }
  }

  @ApiStatus.Internal
  public static final class AssertReadAccess extends TMHAssertionGenerator1 {
    private static final String DEFAULT_ANNOTATION_CLASS_NAME = "com/intellij/util/concurrency/annotations/RequiresReadLock";

    AssertReadAccess() {
      this(Type.getType("L" + DEFAULT_ANNOTATION_CLASS_NAME + ";"),
          Type.getType("L" + DEFAULT_APPLICATION_MANAGER_CLASS_NAME + ";"),
          Type.getType("L" + DEFAULT_APPLICATION_CLASS_NAME + ";"));
    }

    public AssertReadAccess(String annotationClassName, String applicationManagerClassName, String applicationClassName) {
      this(Type.getType("L" + annotationClassName + ";"),
           Type.getType("L" + applicationManagerClassName + ";"),
           Type.getType("L" + applicationClassName + ";"));
    }

    AssertReadAccess(Type annotationClass, Type applicationManagerClass, Type applicationClass) {
      super(annotationClass, applicationManagerClass, applicationClass, new Method("assertReadAccessAllowed", "()V"));
    }
  }

  @ApiStatus.Internal
  public static final class AssertWriteAccess extends TMHAssertionGenerator1 {
    private static final String DEFAULT_ANNOTATION_CLASS_NAME = "com/intellij/util/concurrency/annotations/RequiresWriteLock";

    AssertWriteAccess() {
      this(Type.getType("L" + DEFAULT_ANNOTATION_CLASS_NAME + ";"),
          Type.getType("L" + DEFAULT_APPLICATION_MANAGER_CLASS_NAME + ";"),
          Type.getType("L" + DEFAULT_APPLICATION_CLASS_NAME + ";"));
    }

    @VisibleForTesting
    public AssertWriteAccess(String annotationClassName, String applicationManagerClassName, String applicationClassName) {
      this(Type.getType("L" + annotationClassName + ";"),
           Type.getType("L" + applicationManagerClassName + ";"),
           Type.getType("L" + applicationClassName + ";"));
    }

    AssertWriteAccess(Type annotationClass, Type applicationManagerClass, Type applicationClass) {
      super(annotationClass, applicationManagerClass, applicationClass, new Method("assertWriteAccessAllowed", "()V"));
    }
  }

  @ApiStatus.Internal
  public static final class AssertNoReadAccess extends TMHAssertionGenerator1 {
    private static final String DEFAULT_ANNOTATION_CLASS_NAME = "com/intellij/util/concurrency/annotations/RequiresReadLockAbsence";

    AssertNoReadAccess() {
      this(DEFAULT_ANNOTATION_CLASS_NAME, DEFAULT_APPLICATION_MANAGER_CLASS_NAME, DEFAULT_APPLICATION_CLASS_NAME);
    }

    public AssertNoReadAccess(String annotationClassName, String applicationManagerClassName, String applicationClassName) {
      this(Type.getType("L" + annotationClassName + ";"),
           Type.getType("L" + applicationManagerClassName + ";"),
           Type.getType("L" + applicationClassName + ";"));
    }

    AssertNoReadAccess(Type annotationClass, Type applicationManagerClass, Type applicationClass) {
      super(annotationClass, applicationManagerClass, applicationClass, new Method("assertReadAccessNotAllowed", "()V"));
    }
  }

  @VisibleForTesting
  public static Set<? extends TMHAssertionGenerator> generators() {
    return Set.of(
      new TMHAssertionGenerator1.AssertEdt(),
      new TMHAssertionGenerator1.AssertBackgroundThread(),
      new TMHAssertionGenerator1.AssertReadAccess(),
      new TMHAssertionGenerator1.AssertWriteAccess(),
      new TMHAssertionGenerator1.AssertNoReadAccess()
    );
  }
}
