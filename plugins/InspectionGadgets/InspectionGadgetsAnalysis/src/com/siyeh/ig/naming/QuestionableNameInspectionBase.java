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
package com.siyeh.ig.naming;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class QuestionableNameInspectionBase extends BaseInspection {
  public QuestionableNameInspectionBase() {
    parseString(nameString, nameList);
  }

  /**
   * @noinspection PublicField
   */
  @NonNls public String nameString = "aa,abc,bad,bar,bar2,baz,baz1,baz2," +
                                     "baz3,bb,blah,bogus,bool,cc,dd,defau1t,dummy,dummy2,ee,fa1se," +
                                     "ff,foo,foo1,foo2,foo3,foobar,four,fred,fred1,fred2,gg,hh,hello," +
                                     "hello1,hello2,hello3,ii,nu11,one,silly,silly2,string,two,that," +
                                     "then,three,whi1e,var";List<String> nameList = new ArrayList<>(32);

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "questionable.name.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "questionable.name.problem.descriptor");
  }

  @Override
  public void readSettings(@NotNull Element element) throws InvalidDataException {
    super.readSettings(element);
    parseString(nameString, nameList);
  }

  @Override
  public void writeSettings(@NotNull Element element) throws WriteExternalException {
    nameString = formatString(nameList);
    super.writeSettings(element);
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new QuestionableNameVisitor();
  }

  private class QuestionableNameVisitor extends BaseInspectionVisitor {

    private final Set<String> nameSet = new HashSet(nameList);

    @Override
    public void visitVariable(@NotNull PsiVariable variable) {
      final String name = variable.getName();
      if (nameSet.contains(name)) {
        registerVariableError(variable);
      }
    }

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      final String name = method.getName();
      if (nameSet.contains(name)) {
        registerMethodError(method);
      }
    }

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      final String name = aClass.getName();
      if (nameSet.contains(name)) {
        registerClassError(aClass);
      }
    }
  }
}
