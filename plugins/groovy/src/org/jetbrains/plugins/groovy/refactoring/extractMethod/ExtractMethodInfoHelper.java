/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.refactoring.extractMethod;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrMemberOwner;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.VariableInfo;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author ilyas
 */
public class ExtractMethodInfoHelper {

  private final Map<String, ParameterInfo> myInputNamesMap = new HashMap<String, ParameterInfo>();
  private final String myOutputName;
  private final PsiType myOutputType;
  private final GrMemberOwner myTargetClass;
  private final boolean myIsStatic;
  private final boolean myIsReturnStatement;
  private boolean mySpecifyType;
  private final PsiElement[] myInnerElements;
  private String myVisibility;
  private final Project myProject;
  private final GrStatement[] myStatements;

  public ExtractMethodInfoHelper(VariableInfo[] inputInfos,
                                 VariableInfo outputInfo,
                                 PsiElement[] innerElements,
                                 GrStatement[] statements,
                                 GrMemberOwner targetClass,
                                 boolean isStatic,
                                 boolean isReturnStatement) {
    myInnerElements = innerElements;
    myStatements = statements;
    myTargetClass = targetClass;
    myIsStatic = isStatic;
    myIsReturnStatement = isReturnStatement;
    myVisibility = PsiModifier.PRIVATE;
    assert myStatements.length > 0;
    myProject = myStatements[0].getProject();
    int i = 0;
    for (VariableInfo info : inputInfos) {
      PsiType type = info.getType();
      ParameterInfo pInfo = new ParameterInfo(info.getName(), i, type);
      myInputNamesMap.put(info.getName(), pInfo);
      i++;
    }

    PsiType outputType = PsiType.VOID;
    if (outputInfo != null) {
      myOutputName = outputInfo.getName();
      outputType = outputInfo.getType();
    }
    else if (ExtractMethodUtil.isSingleExpression(statements)) {
      final GrStatement lastExpr = statements[statements.length - 1];
      if (!(lastExpr.getParent() instanceof GrCodeBlock)) {
        outputType = ((GrExpression)lastExpr).getType();
      }
      myOutputName = null;
    }
    else {
      myOutputName = null;
      if (isReturnStatement) {
        assert myStatements.length > 0;
        GrStatement finalStatement = myStatements[myStatements.length - 1];
        if (finalStatement instanceof GrExpression) {
          outputType = ((GrExpression)finalStatement).getType();
          if (outputType != null) {
            outputType = TypeConversionUtil.erasure(outputType);
          }
        }
      }
    }
    myOutputType = outputType != null ? outputType : PsiType.VOID;
    mySpecifyType = !(PsiType.VOID.equals(outputType) || myOutputType.equalsToText("java.lang.Object"));
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  public ParameterInfo[] getParameterInfos() {
    Collection<ParameterInfo> collection = myInputNamesMap.values();
    ParameterInfo[] infos = new ParameterInfo[collection.size()];
    for (ParameterInfo info : collection) {
      int position = info.getPosition();
      assert position < infos.length && infos[position] == null;
      infos[position] = info;
    }
    return infos;
  }

  @Nullable
  public String getOutputName() {
    return myOutputName;
  }

  /**
   * Get old names of parameters to be pasted as method call arguments
   *
   * @return array of argument names
   */
  public String[] getArgumentNames() {
    Collection<ParameterInfo> infos = myInputNamesMap.values();
    String[] argNames = new String[infos.size()];
    for (ParameterInfo info : infos) {
      int position = info.getPosition();
      assert position < argNames.length;
      argNames[position] = info.passAsParameter() ? info.getOldName() : "";
    }
    return argNames;
  }

  @NotNull
  public PsiType getOutputType() {
    return myOutputType;
  }

  @NotNull
  public PsiElement[] getInnerElements() {
    return myInnerElements;
  }

  @NotNull
  public GrStatement[] getStatements() {
    return myStatements;
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

  @NotNull
  public GrMemberOwner getOwner() {
    return myTargetClass;
  }

  public boolean isReturnStatement() {
    return myIsReturnStatement;
  }
}
