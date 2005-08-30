/*
 * Copyright 2003-2005 Dave Griffith
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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.VariableInspection;
import com.siyeh.ig.fixes.RenameFix;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class StandardVariableNamesInspection extends VariableInspection {

    private static final Map<String,String> s_expectedTypes = new HashMap<String, String>(10);
    private final RenameFix fix = new RenameFix();

    static {
      initExpectedTypes();
    }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void initExpectedTypes() {
    s_expectedTypes.put("b", "byte");
    s_expectedTypes.put("c", "char");
    s_expectedTypes.put("ch", "char");
    s_expectedTypes.put("d", "double");
    s_expectedTypes.put("f", "float");
    s_expectedTypes.put("i", "int");
    s_expectedTypes.put("j", "int");
    s_expectedTypes.put("k", "int");
    s_expectedTypes.put("m", "int");
    s_expectedTypes.put("n", "int");
    s_expectedTypes.put("l", "long");
    s_expectedTypes.put("s", "java.lang.String");
    s_expectedTypes.put("str", "java.lang.String");
  }

  public String getDisplayName() {
      return "Standard variable names";
  }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    public String getGroupDisplayName() {
        return GroupNames.NAMING_CONVENTIONS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final String variableName = location.getText();
        final String expectedType = s_expectedTypes.get(variableName);
        return "Variable name '#ref' doesn't have type " + expectedType + " #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ExceptionNameDoesntEndWithExceptionVisitor();
    }

    private static class ExceptionNameDoesntEndWithExceptionVisitor extends BaseInspectionVisitor {

        public void visitVariable(@NotNull PsiVariable var) {
            super.visitVariable(var);
            final String variableName = var.getName();
            final String expectedType = s_expectedTypes.get(variableName);
            if (expectedType == null) {
                return;
            }
            final PsiType type = var.getType();
            if (type == null) {
                return;
            }
            final String typeText = type.getCanonicalText();
            if (typeText.equals(expectedType)) {
                return;
            }
            registerVariableError(var);
        }


    }

}
