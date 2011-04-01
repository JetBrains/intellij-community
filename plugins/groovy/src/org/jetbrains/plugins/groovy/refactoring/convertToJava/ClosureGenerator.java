/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE;
import static org.jetbrains.plugins.groovy.refactoring.convertToJava.GenerationUtil.writeType;

/**
 * @author Maxim.Medvedev
 */
public class ClosureGenerator {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.convertToJava.ClosureGenerator");

  StringBuilder builder;
  ExpressionContext context;

  public ClosureGenerator(StringBuilder builder, ExpressionContext context) {
    this.builder = builder;
    this.context = context;
  }

  public void generate(GrClosableBlock closure) {
    final String owner = getOwner(closure);
    final PsiType returnType = closure.getReturnType();
    builder.append("new ").append(GROOVY_LANG_CLOSURE);
    if (returnType != null) {
      builder.append("<");
      writeType(builder, returnType);
      builder.append(">(");
    }
    builder.append(owner).append(", ").append(owner).append(") {\n");
    builder.append("public ").append(JAVA_LANG_OBJECT).append(" doCall(");

    final GrParameter[] parameters = closure.getAllParameters();
    for (GrParameter parameter : parameters) {
      final String name = parameter.getName();
    }

    final GrStatement[] statements = closure.getStatements();

  }

  private static String getOwner(GrClosableBlock closure) {
    final GroovyPsiElement context = PsiTreeUtil.getParentOfType(closure, GrMember.class, GrClosableBlock.class, GroovyFile.class);
    LOG.assertTrue(context != null);

    if (context instanceof GrTypeDefinition) {
      LOG.assertTrue(false, "closure must have member parent");
      return "this";
    }
    if (context instanceof GrMember && ((GrMember)context).hasModifierProperty(GrModifier.STATIC)) {
      return "null";
    }
    return "this";
  }
}
