/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ig.logging;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ClassWithMultipleLoggersInspectionBase extends BaseInspection {
  protected final List<String> loggerNames = new ArrayList();
  /**
   * @noinspection PublicField
   */
  @NonNls
  public String loggerNamesString = "java.util.logging.Logger" + ',' +
                                    "org.slf4j.Logger" + ',' +
                                    "org.apache.commons.logging.Log" + ',' +
                                    "org.apache.log4j.Logger";

  public ClassWithMultipleLoggersInspectionBase() {
    parseString(loggerNamesString, loggerNames);
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("multiple.loggers.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "multiple.loggers.problem.descriptor");
  }

  @Override
  public void readSettings(@NotNull Element element) throws InvalidDataException {
    super.readSettings(element);
    parseString(loggerNamesString, loggerNames);
  }

  @Override
  public void writeSettings(@NotNull Element element) throws WriteExternalException {
    loggerNamesString = formatString(loggerNames);
    super.writeSettings(element);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ClassWithMultipleLoggersVisitor();
  }

  private class ClassWithMultipleLoggersVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      //no recursion to avoid drilldown
      if (aClass.isInterface() || aClass.isEnum() ||
          aClass.isAnnotationType()) {
        return;
      }
      if (aClass instanceof PsiTypeParameter) {
        return;
      }
      if (aClass.getContainingClass() != null) {
        return;
      }
      int numLoggers = 0;
      final PsiField[] fields = aClass.getFields();
      for (PsiField field : fields) {
        if (isLogger(field)) {
          numLoggers++;
        }
      }
      if (numLoggers <= 1) {
        return;
      }
      registerClassError(aClass);
    }

    private boolean isLogger(PsiVariable variable) {
      final PsiType type = variable.getType();
      final String text = type.getCanonicalText();
      return loggerNames.contains(text);
    }
  }
}
