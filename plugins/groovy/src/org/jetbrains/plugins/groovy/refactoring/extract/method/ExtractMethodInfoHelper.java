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

package org.jetbrains.plugins.groovy.refactoring.extract.method;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrMemberOwner;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.VariableInfo;
import org.jetbrains.plugins.groovy.refactoring.extract.ExtractInfoHelper;
import org.jetbrains.plugins.groovy.refactoring.extract.ExtractUtil;
import org.jetbrains.plugins.groovy.refactoring.extract.InitialInfo;
import org.jetbrains.plugins.groovy.refactoring.extract.ParameterInfo;

/**
 * @author ilyas
 */
public class ExtractMethodInfoHelper implements ExtractInfoHelper {
  private final InitialInfo myInitialInfo;

  private final boolean myIsStatic;
  private boolean mySpecifyType = true;
  private String myVisibility;
  private String myName;

  public ExtractMethodInfoHelper(InitialInfo initialInfo, String name) {
    myInitialInfo = initialInfo;

    myVisibility = PsiModifier.PRIVATE;
    myName = name;

    myIsStatic = ExtractUtil.canBeStatic(initialInfo.getStatements()[0]);
  }

  @Override
  @NotNull
  public Project getProject() {
    return myInitialInfo.getProject();
  }

  @NotNull
  @Override
  public ParameterInfo[] getParameterInfos() {
    return myInitialInfo.getParameterInfos();
  }

  @Override
  @NotNull
  public VariableInfo[] getOutputNames() {
    return myInitialInfo.getOutputNames();
  }

  /**
   * Get old names of parameters to be pasted as method call arguments
   *
   * @return array of argument names
   */
  @NotNull
  @Override
  public String[] getArgumentNames() {
    return myInitialInfo.getArgumentNames();
  }

  @Override
  @NotNull
  public PsiType getOutputType() {
    return myInitialInfo.getOutputType();
  }

  @Override
  @NotNull
  public PsiElement[] getInnerElements() {
    return myInitialInfo.getInnerElements();
  }

  @Override
  @NotNull
  public GrStatement[] getStatements() {
    return myInitialInfo.getStatements();
  }

  public boolean isStatic() {
    return myIsStatic;
  }

  public String getVisibility() {
    return myVisibility;
  }

  public void setVisibility(String visibility) {
    myVisibility = visibility;
  }

  public boolean specifyType() {
    return mySpecifyType;
  }

  public void setSpecifyType(boolean specifyType) {
    mySpecifyType = specifyType;
  }

  @Override
  @NotNull
  public GrMemberOwner getOwner() {
    return myInitialInfo.getOwner();
  }

  public boolean hasReturnValue() {
    return myInitialInfo.hasReturnValue();
  }

  public String getName() {
    return myName;
  }

  public void setName(String name) {
    myName = name;
  }
}
