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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

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
    builder.append("new ");
    writeType(builder, closure.getType(), closure);
    builder.append('(');
    builder.append(owner).append(", ").append(owner).append(") {\n");

    final GrMethod method = generateClosureMethod(closure);
    ClassGenerator.writeAllSignaturesOfMethod(builder, method, new ClassItemGeneratorImpl(context.project));
    builder.append("}");
  }

  private GrMethod generateClosureMethod(GrClosableBlock block) {
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(context.project);
    final GrMethod method = factory.createMethodFromText("def doCall(){}");

    method.setReturnType(block.getReturnType());
    final PsiElement first;
    if (block.hasParametersSection()) {
      method.getParameterList().replace(block.getParameterList());
      final PsiElement arrow = block.getArrow();
      LOG.assertTrue(arrow != null);
      first = arrow.getNextSibling();
    }
    else {
      final GrParameter[] allParameters = block.getAllParameters();
      LOG.assertTrue(allParameters.length == 1);
      final GrParameter itParameter = allParameters[0];
      final GrParameter parameter = factory.createParameter("it", itParameter.getType().getCanonicalText(), "null", block);
      method.getParameterList().addParameterToEnd(parameter);
      final PsiElement lBrace = block.getLBrace();
      LOG.assertTrue(lBrace != null);
      first = lBrace.getNextSibling();
    }

    final PsiElement rBrace = block.getRBrace();
    final PsiElement last = rBrace != null ? rBrace.getPrevSibling() : block.getLastChild();

    final GrOpenBlock methodBlock = method.getBlock();
    methodBlock.addRange(first, last);
    return method;
  }

  private static String getOwner(GrClosableBlock closure) {
    final GroovyPsiElement context = PsiTreeUtil.getParentOfType(closure, GrMember.class, GrClosableBlock.class, GroovyFile.class);
    LOG.assertTrue(context != null);

    if (context instanceof GrTypeDefinition) {
      LOG.assertTrue(false, "closure must have member parent");
      return "this";
    }
    if (context instanceof GrMember && ((GrMember)context).hasModifierProperty(PsiModifier.STATIC)) {
      return "null";
    }
    return "this";
  }
}
