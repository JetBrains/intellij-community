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
package com.siyeh.ig.security;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;

/**
 * @author Bas Leijdekkers
 */
public class SerializableDeserializableClassInSecureContextInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreThrowable = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("serializable.class.in.secure.context.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final Boolean serializable = (Boolean)infos[0];
    final Boolean deserializable = (Boolean)infos[1];
    if (serializable.booleanValue()) {
      return deserializable.booleanValue()
             ? InspectionGadgetsBundle.message("serializable.deserializable.class.in.secure.context.problem.descriptor")
             : InspectionGadgetsBundle.message("serializable.class.in.secure.context.problem.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message("deserializable.class.in.secure.context.problem.descriptor");
    }
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final Boolean serializable = (Boolean)infos[0];
    final Boolean deserializable = (Boolean)infos[1];
    final PsiClass aClass = (PsiClass)infos[2];
    final boolean addReadObjectMethod = deserializable.booleanValue() && !hasOwnReadObjectMethod(aClass);
    final boolean addWriteObjectMethod = serializable.booleanValue() && !hasOwnWriteObjectMethod(aClass);
    if (!addReadObjectMethod && !addWriteObjectMethod) {
      return null;
    }
    return new AddReadWriteObjectMethodsFix(addReadObjectMethod, addWriteObjectMethod);
  }

  private static boolean hasOwnReadObjectMethod(PsiClass aClass) {
    return Arrays.stream(aClass.findMethodsByName("readObject", false)).anyMatch(SerializationUtils::isReadObject);
  }

  private static boolean hasOwnWriteObjectMethod(PsiClass aClass) {
    return Arrays.stream(aClass.findMethodsByName("writeObject", false)).anyMatch(SerializationUtils::isWriteObject);
  }

  private static class AddReadWriteObjectMethodsFix extends InspectionGadgetsFix {

    private final boolean myReadObject;
    private final boolean myWriteObject;

    AddReadWriteObjectMethodsFix(boolean readObject, boolean writeObject) {
      myReadObject = readObject;
      myWriteObject = writeObject;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      if (myReadObject) {
        return myWriteObject ? getFamilyName() : "Add 'readObject()' method which always throws exception";
      }
      return "Add 'writeObject()' methods which always throws exception";
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Add 'readObject()' and 'writeObject()' methods which always throw exception";
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiClass containingClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
      if (containingClass == null) {
        return;
      }
      final String className = containingClass.getQualifiedName();
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      if (myReadObject) {
        final PsiMethod readObjectMethod = factory.createMethodFromText(
          "private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, java.lang.ClassNotFoundException {" +
          "  throw new java.io.NotSerializableException(\"" + className + "\");" +
          "}", containingClass);
        containingClass.add(readObjectMethod);
      }
      if (myWriteObject) {
        final PsiMethod writeObjectMethod = factory.createMethodFromText(
          "private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {" +
          "  throw new java.io.NotSerializableException(\"" + className + "\");" +
          "}", containingClass);
        containingClass.add(writeObjectMethod);
      }
    }
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(
      InspectionGadgetsBundle.message("ignore.classes.extending.throwable.option"), this, "ignoreThrowable");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SerializableDeserializableClassInSecureContextVisitor();
  }

  private class SerializableDeserializableClassInSecureContextVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (aClass.isInterface() || aClass.isAnnotationType()) {
        return;
      }
      if (aClass instanceof PsiTypeParameter || !SerializationUtils.isSerializable(aClass)) {
        return;
      }
      if (ignoreThrowable && InheritanceUtil.isInheritor(aClass, false, "java.lang.Throwable")) {
        return;
      }
      if (!hasSerializableState(aClass)) {
        // doesn't matter, class has no state anyway.
        return;
      }
      final boolean serializable = !hasWriteObjectMethodWhichAlwaysThrowsException(aClass);
      final boolean deserializable = !hasReadObjectMethodWhichAlwaysThrowsException(aClass);
      if (!serializable && !deserializable) {
        return;
      }
      registerClassError(aClass, Boolean.valueOf(serializable), Boolean.valueOf(deserializable), aClass);
    }

    private boolean hasSerializableState(PsiClass aClass) {
      return Arrays.stream(aClass.getFields())
        .filter(f -> !f.hasModifierProperty(PsiModifier.STATIC))
        .filter(f -> !f.hasModifierProperty(PsiModifier.TRANSIENT))
        .anyMatch(f -> !(f instanceof PsiEnumConstant));
    }

    private boolean hasReadObjectMethodWhichAlwaysThrowsException(PsiClass aClass) {
      for (final PsiMethod method : aClass.findMethodsByName("readObject", true)) {
        if (SerializationUtils.isReadObject(method)) {
          return ControlFlowUtils.methodAlwaysThrowsException((PsiMethod)method.getNavigationElement());
        }
      }
      return false;
    }

    private boolean hasWriteObjectMethodWhichAlwaysThrowsException(PsiClass aClass) {
      for (final PsiMethod method : aClass.findMethodsByName("writeObject", true)) {
        if (SerializationUtils.isWriteObject(method)) {
          return ControlFlowUtils.methodAlwaysThrowsException((PsiMethod)method.getNavigationElement());
        }
      }
      return false;
    }
  }

  @Override
  public String getAlternativeID() {
    return "serial";
  }
}