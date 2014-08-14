/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.codeInspection.naming;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.codeInspection.GroovyQuickFixFactory;
import org.jetbrains.plugins.groovy.codeInspection.utils.LibraryUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

public class GroovyInstanceMethodNamingConventionInspection extends ConventionInspection {

  private static final int DEFAULT_MIN_LENGTH = 4;
  private static final int DEFAULT_MAX_LENGTH = 32;

  @Override
  @NotNull
  public String getDisplayName() {
    return "Instance method naming convention";
  }

  @Override
  protected GroovyFix buildFix(@NotNull PsiElement location) {
    return GroovyQuickFixFactory.getInstance().createRenameFix();
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  @NotNull
  public String buildErrorString(Object... args) {
    final String className = (String) args[0];
    if (className.length() < getMinLength()) {
      return "Instance method name '#ref' is too short";
    } else if (className.length() > getMaxLength()) {
      return "Instance method name '#ref' is too long";
    }
    return "Instance method name '#ref' doesn't match regex '" + getRegex() + "' #loc";
  }

  @Override
  protected String getDefaultRegex() {
    return "[a-z][A-Za-z\\d]*";
  }

  @Override
  protected int getDefaultMinLength() {
    return DEFAULT_MIN_LENGTH;
  }

  @Override
  protected int getDefaultMaxLength() {
    return DEFAULT_MAX_LENGTH;
  }

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NamingConventionsVisitor();
  }


  private class NamingConventionsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(GrMethod grMethod) {
      super.visitMethod(grMethod);
      if (grMethod.hasModifierProperty(PsiModifier.STATIC) || grMethod.isConstructor()) {
        return;
      }
      final String name = grMethod.getName();
      if (isValid(name)) {
        return;
      }
      if (LibraryUtil.isOverrideOfLibraryMethod(grMethod)) {
        return;
      }
      registerMethodError(grMethod, name);
    }
  }
}