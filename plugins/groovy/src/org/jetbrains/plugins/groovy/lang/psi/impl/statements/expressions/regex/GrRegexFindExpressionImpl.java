/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.regex;

import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiType;

/**
 * @author ilyas
 */
public class GrRegexFindExpressionImpl extends GrRegexExpressionImpl {
  private static final String MATCHER_FQ_NAME = "java.util.regex.Matcher";

  public GrRegexFindExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public PsiType getType() {
    return getManager().getElementFactory().createTypeByFQClassName(MATCHER_FQ_NAME, getResolveScope());
  }

  public String toString() {
    return "RegexFindExpression";
  }
}