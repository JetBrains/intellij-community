/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.naming;

import com.intellij.psi.PsiClass;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

import java.util.StringTokenizer;

public class ClassNamePrefixedWithPackageNameInspection
  extends BaseInspection {

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RenameFix();
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "class.name.prefixed.with.package.name.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "class.name.prefixed.with.package.name.problem.descriptor");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ClassNameBePrefixedWithPackageNameVisitor();
  }

  private static class ClassNameBePrefixedWithPackageNameVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // no call to super, so it doesn't drill down into inner classes
      final String className = aClass.getName();
      if (className == null) {
        return;
      }
      final PsiClass outerClass =
        ClassUtils.getOutermostContainingClass(aClass);
      final String qualifiedName = outerClass.getQualifiedName();
      if (qualifiedName == null) {
        return;
      }
      if (className.equals(qualifiedName)) {
        return;
      }
      final StringTokenizer tokenizer =
        new StringTokenizer(qualifiedName, ".");
      String currentPackageName = null;
      String lastPackageName = null;
      while (tokenizer.hasMoreTokens()) {
        lastPackageName = currentPackageName;
        currentPackageName = tokenizer.nextToken();
      }
      if (lastPackageName == null) {
        return;
      }
      final String lowercaseClassName = className.toLowerCase();
      final String lowercasePackageName = lastPackageName.toLowerCase();
      if (!lowercaseClassName.startsWith(lowercasePackageName)) {
        return;
      }
      registerClassError(aClass);
    }
  }
}