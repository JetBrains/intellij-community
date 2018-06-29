/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.serialization;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.SerialVersionUIDBuilder;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class SerializableCanHaveDefaultSerialUIDInspection extends SerializableInspectionBase {
  @Override
  public JComponent createOptionsPanel() {
    return SerializableInspectionUtil.createOptions(this);
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("inspection.serializable.can.have.default.serial.uid");
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new ChangeSerialUidToDefaultFix((Long)infos[0]);
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("inspection.serializable.can.have.default.serial.uid.message");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitField(PsiField field) {
        if (!"serialVersionUID".equals(field.getName())) return;
        PsiExpression initializer = field.getInitializer();
        Object initializerResult = ExpressionUtils.computeConstantExpression(initializer);
        if (!(initializerResult instanceof Long)) return;
        PsiClass aClass = field.getContainingClass();
        if (aClass == null) return;
        if (!InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_IO_SERIALIZABLE)) return;
        long suid = SerialVersionUIDBuilder.computeDefaultSUID(aClass);
        if (((Number)initializerResult).longValue() == suid) return;
        registerError(initializer, suid);
      }
    };
  }

  private static class ChangeSerialUidToDefaultFix extends InspectionGadgetsFix {
    private final long mySerialUid;

    private ChangeSerialUidToDefaultFix(long uid) {mySerialUid = uid;}

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return getFamilyName();
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.serializable.can.have.default.serial.uid.fix.name");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      new CommentTracker().replaceAndRestoreComments(descriptor.getStartElement(), String.valueOf(mySerialUid) + "L");
    }
  }
}
