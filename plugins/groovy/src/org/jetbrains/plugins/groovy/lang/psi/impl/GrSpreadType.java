/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

/**
 * @author Max Medvedev
 */
public class GrSpreadType extends GrLiteralClassType {

  private PsiType myType;

  public GrSpreadType(PsiType original, PsiType containerType, GlobalSearchScope scope) {
    this(original, containerType, LanguageLevel.JDK_1_5, scope, JavaPsiFacade.getInstance(scope.getProject()));
  }

  public GrSpreadType(PsiType original, PsiType containerType, LanguageLevel languageLevel, GlobalSearchScope scope, JavaPsiFacade facade) {
    super(languageLevel, scope, facade);

    final Project project = facade.getProject();
    myType = TypesUtil.createSimilarCollection(containerType, project, original);
  }

  @NotNull
  @Override
  protected String getJavaClassName() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @NotNull
  @Override
  public String getClassName() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @NotNull
  @Override
  public PsiType[] getParameters() {
    return new PsiType[0];  //To change body of implemented methods use File | Settings | File Templates.
  }

  @NotNull
  @Override
  public PsiClassType setLanguageLevel(@NotNull LanguageLevel languageLevel) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public String getInternalCanonicalText() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public boolean isValid() {
    return false;  //To change body of implemented methods use File | Settings | File Templates.
  }
}
