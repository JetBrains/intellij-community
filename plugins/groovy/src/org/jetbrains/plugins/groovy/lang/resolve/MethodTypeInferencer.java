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
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.List;

/**
 * @author ven
 */
public class MethodTypeInferencer implements Computable<PsiType> {
  private final GrCodeBlock myBlock;

  public MethodTypeInferencer(GrCodeBlock block) {
    myBlock = block;
  }

  @Nullable
  public PsiType compute() {
    List<GrStatement> returns = ControlFlowUtils.collectReturns(myBlock);

    PsiType result = null;
    PsiManager manager = myBlock.getManager();
    for (GrStatement returnStatement : returns) {
      GrExpression value = null;
      if (returnStatement instanceof GrReturnStatement) {
        value = ((GrReturnStatement)returnStatement).getReturnValue();
      }
      else if (returnStatement instanceof GrExpression) {
        value = (GrExpression)returnStatement;
      }

      if (value != null) {
        result = TypesUtil.getLeastUpperBoundNullable(result, value.getType(), manager);
      }
    }

    if (returns.size() == 0) return TypesUtil.getJavaLangObject(myBlock);
    return result;
  }
}
