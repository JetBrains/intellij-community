/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.siyeh.ig.classlayout;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.reference.*;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiType;
import com.siyeh.ig.BaseGlobalInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class FieldCanBeMovedToSubclassInspection extends BaseGlobalInspection {

  @NotNull
  @Override
  public String getDisplayName() {
    return null;
    //return InspectionGadgetsBundle.message("field.can.be.moved.so.subclass.display.name");
  }

  @Override
  @Nullable
  public CommonProblemDescriptor[] checkElement(
    @NotNull RefEntity refEntity, @NotNull AnalysisScope analysisScope,
    @NotNull InspectionManager inspectionManager,
    @NotNull GlobalInspectionContext globalInspectionContext) {
    if (!(refEntity instanceof RefField)) {
      return null;
    }
    final RefField refField = (RefField)refEntity;
    final PsiField field = refField.getElement();
    if (field == null) {
      return null;
    }
    final PsiType type = field.getType();
    if (!type.equals(PsiType.BOOLEAN)) {
      return null;
    }

    final RefClass fieldClass = refField.getOwnerClass();
    final Collection<RefElement> inReferences = refField.getInReferences();
    final RefJavaUtil refUtil = RefJavaUtil.getInstance();
    final Set<RefClass> classesUsed = new HashSet<>();
    for (RefElement inReference : inReferences) {
      final RefClass referringClass = refUtil.getOwnerClass(inReference);
      if (referringClass == null) {
        return null;
      }
      if (referringClass.equals(fieldClass)) {
        return null;
      }
      classesUsed.add(referringClass);
      if (classesUsed.size() > 1) {
        return null;
      }
    }
    if (classesUsed.size() != 1) {
      return null;
    }
    final RefClass referencingClass = classesUsed.iterator().next();
    //TODO: check that referencing class is a subclass of the field class
    final String errorString = "Field " + refEntity.getName() + " is only accessed in subclass " + referencingClass.getName();
    return new CommonProblemDescriptor[]{inspectionManager.createProblemDescriptor(errorString)};
  }
}
