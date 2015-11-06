/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.noReturnMethod.MissingReturnInspection;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.codeStyle.GrReferenceAdjuster;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Maxim.Medvedev
 */
public class ClosureGenerator {
  private static final Logger LOG = Logger.getInstance(ClosureGenerator.class);

  public static final String[] MODIFIERS = new String[]{PsiModifier.PUBLIC};

  private final StringBuilder builder;
  private final ExpressionContext context;

  public ClosureGenerator(@NotNull StringBuilder builder, @NotNull ExpressionContext context) {
    this.builder = builder;
    this.context = context;
  }

  public void generate(@NotNull GrClosableBlock closure) {
    builder.append("new ");
    TypeWriter.writeTypeForNew(builder, closure.getType(), closure);
    builder.append('(');

    final CharSequence owner = getOwner(closure);
    builder.append(owner);
    builder.append(", ");
    builder.append(owner);

    builder.append(") {\n");

    generateClosureMainMethod(closure);

    final ClassItemGeneratorImpl generator = new ClassItemGeneratorImpl(context);
    final GrMethod method = generateClosureMethod(closure);
    final GrReflectedMethod[] reflectedMethods = method.getReflectedMethods();

    if (reflectedMethods.length > 0) {
      for (GrReflectedMethod reflectedMethod : reflectedMethods) {
        if (reflectedMethod.getSkippedParameters().length > 0) {
          generator.writeMethod(builder, reflectedMethod);
          builder.append('\n');
        }
      }
    }
    builder.append('}');
  }

  private void generateClosureMainMethod(@NotNull GrClosableBlock block) {
    builder.append("public ");
    final PsiType returnType = context.typeProvider.getReturnType(block);
    TypeWriter.writeType(builder, returnType, block);
    builder.append(" doCall");
    final GrParameter[] parameters = block.getAllParameters();
    GenerationUtil.writeParameterList(builder, parameters, new GeneratorClassNameProvider(), context);

    Collection<GrStatement> myExitPoints = !PsiType.VOID.equals(returnType) ? ControlFlowUtils.collectReturns(block) : Collections.<GrStatement>emptySet();
    boolean shouldInsertReturnNull = !(returnType instanceof PsiPrimitiveType) &&
                                     MissingReturnInspection.methodMissesSomeReturns(block, MissingReturnInspection.ReturnStatus.shouldNotReturnValue);

    new CodeBlockGenerator(builder, context.extend(), myExitPoints).generateCodeBlock(block, shouldInsertReturnNull);
    builder.append('\n');
  }

  @NotNull
  private GrMethod generateClosureMethod(@NotNull GrClosableBlock block) {
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(context.project);
    final GrMethod method = factory.createMethodFromText("def doCall(){}", block);

    GrReferenceAdjuster.shortenAllReferencesIn(method.setReturnType(context.typeProvider.getReturnType(block)));
    if (block.hasParametersSection()) {
      method.getParameterList().replace(block.getParameterList());
    }
    else {
      final GrParameter[] allParameters = block.getAllParameters();
      LOG.assertTrue(allParameters.length == 1);
      final GrParameter itParameter = allParameters[0];
      final GrParameter parameter = factory.createParameter("it", itParameter.getType().getCanonicalText(), "null", block);
      method.getParameterList().add(parameter);
    }
    ((GroovyFileImpl)method.getContainingFile()).setContextNullable(null);
    return method;
  }

  @NonNls
  @NotNull
  private CharSequence getOwner(@NotNull GrClosableBlock closure) {
    final GroovyPsiElement context = PsiTreeUtil.getParentOfType(closure, GrMember.class, GroovyFile.class);
    LOG.assertTrue(context != null);

    final PsiClass contextClass;
    if (context instanceof GroovyFile) {
      contextClass = ((GroovyFile)context).getScriptClass();
    }
    else if (context instanceof PsiClass) {
      contextClass = (PsiClass)context;
    }
    else if (context instanceof GrMember) {
      if (((GrMember)context).hasModifierProperty(PsiModifier.STATIC)) {
        contextClass = null; //no context class
      }
      else {
        contextClass = ((GrMember)context).getContainingClass();
      }
    }
    else {
      contextClass = null;
    }

    if (contextClass == null) return "null";

    final PsiElement implicitClass = GenerationUtil.getWrappingImplicitClass(closure);
    if (implicitClass == null) {
      return "this";
    }
    else {
      final StringBuilder buffer = new StringBuilder();
      GenerationUtil.writeThisReference(contextClass, buffer, this.context);
      return buffer;
    }
  }
}
