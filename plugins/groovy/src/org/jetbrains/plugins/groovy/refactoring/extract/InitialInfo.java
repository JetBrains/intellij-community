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
package org.jetbrains.plugins.groovy.refactoring.extract;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.VariableInfo;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class InitialInfo implements ExtractInfoHelper {
  private final ParameterInfo[] myParameterInfos;
  private final VariableInfo[] myOutputNames;
  private final PsiType myOutputType;
  private final PsiElement[] myInnerElements;
  private final Project myProject;
  private final GrStatement[] myStatements;
  private final boolean myHasReturnValue;
  private String[] myArgumentNames;

  public InitialInfo(VariableInfo[] inputInfos,
                     VariableInfo[] outputInfos,
                     PsiElement[] innerElements,
                     GrStatement[] statements,
                     ArrayList<GrStatement> returnStatements) {
    myInnerElements = innerElements;
    myStatements = statements;
    myOutputNames = outputInfos;

    myHasReturnValue = ContainerUtil.find(returnStatements, new Condition<GrStatement>() {
      @Override
      public boolean value(GrStatement statement) {
        return statement instanceof GrReturnStatement && ((GrReturnStatement)statement).getReturnValue() != null;
      }
    }) != null;

    assert myStatements.length > 0;
    myProject = myStatements[0].getProject();

    myParameterInfos = new ParameterInfo[inputInfos.length];
    myArgumentNames = new String[inputInfos.length];
    for (int i = 0; i < inputInfos.length; i++) {
      VariableInfo info = inputInfos[i];
      PsiType type = info.getType();
      myParameterInfos[i] = new ParameterInfo(info.getName(), i, type);
      myArgumentNames[i] = info.getName();
    }

    PsiType outputType = inferOutputType(outputInfos, statements, returnStatements, myHasReturnValue);
    myOutputType = outputType != null ? outputType : PsiType.VOID;
  }

  @Nullable
  private PsiType inferOutputType(VariableInfo[] outputInfos,
                                  GrStatement[] statements,
                                  ArrayList<GrStatement> returnStatements,
                                  boolean hasReturnValue) {
    PsiType outputType = PsiType.VOID;
    if (outputInfos.length > 0) {
      if (outputInfos.length == 1) {
        outputType = outputInfos[0].getType();
      }
      else {
        outputType = JavaPsiFacade.getElementFactory(myProject).createTypeFromText(CommonClassNames.JAVA_UTIL_LIST, getContext());
      }
    }
    else if (ExtractUtil.isSingleExpression(statements)) {
      final GrStatement single = statements[0];
      if (!(single.getParent() instanceof GrCodeBlock)) {
        outputType = ((GrExpression)single).getType();
      }
    }
    else if (hasReturnValue) {
      assert returnStatements.size() > 0;
      List<PsiType> types = new ArrayList<PsiType>(returnStatements.size());
      for (GrStatement statement : returnStatements) {
        if (statement instanceof GrReturnStatement) {
          GrExpression returnValue = ((GrReturnStatement)statement).getReturnValue();
          if (returnValue != null) {
            types.add(returnValue.getType());
          }
        }
        else if (statement instanceof GrExpression) {
          types.add(((GrExpression)statement).getType());
        }
      }
      outputType = TypesUtil.getLeastUpperBoundNullable(types, getContext().getManager());
    }

    return outputType;
  }

  @Override
  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  @Override
  public ParameterInfo[] getParameterInfos() {
    return myParameterInfos;
  }

  @Override
  @NotNull
  public VariableInfo[] getOutputVariableInfos() {
    return myOutputNames;
  }

  /**
   * Get old names of parameters to be pasted as method call arguments
   *
   * @return array of argument names
   */
  @NotNull
  @Override
  public String[] getArgumentNames() {
    return myArgumentNames;
  }

  @Override
  @NotNull
  public PsiType getOutputType() {
    return myOutputType;
  }

  @Override
  @NotNull
  public PsiElement[] getInnerElements() {
    return myInnerElements;
  }

  @Override
  @NotNull
  public GrStatement[] getStatements() {
    return myStatements;
  }

  public boolean hasReturnValue() {
    return myHasReturnValue;
  }

  @Override
  public String getName() {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiElement getContext() {
    return myStatements[0];
  }
}
