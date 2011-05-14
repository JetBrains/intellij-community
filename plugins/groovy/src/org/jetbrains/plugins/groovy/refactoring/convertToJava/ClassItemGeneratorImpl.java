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
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.noReturnMethod.MissingReturnInspection;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrDefaultAnnotationValue;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumConstantInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.*;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrClosureSignatureUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.jetbrains.plugins.groovy.refactoring.convertToJava.GenerationUtil.*;

/**
 * @author Maxim.Medvedev
 */
public class ClassItemGeneratorImpl implements ClassItemGenerator {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.convertToJava.ClassItemGeneratorImpl");
  private ClassNameProvider classNameProvider;
  private ExpressionContext context;

  public ClassItemGeneratorImpl(Project project) {
    this(new ExpressionContext(project));
  }

  public ClassItemGeneratorImpl(@NotNull ExpressionContext context) {
    classNameProvider = new GeneratorClassNameProvider();
    this.context = context;
  }

  @Override
  public void writeEnumConstant(StringBuilder builder, GrEnumConstant constant) {
    builder.append(constant.getName());

    final GrArgumentList argumentList = constant.getArgumentList();
    if (argumentList != null) {
      final GroovyResolveResult resolveResult = constant.resolveConstructorGenerics();
      GrClosureSignature signature = GrClosureSignatureUtil.createSignature(resolveResult);
      new ArgumentListGenerator(builder, context.extend()).generate(
        signature,
        argumentList.getExpressionArguments(),
        argumentList.getNamedArguments(),
        GrClosableBlock.EMPTY_ARRAY,
        constant
      );
    }

    final GrEnumConstantInitializer anonymousBlock = constant.getConstantInitializer();
    if (anonymousBlock != null) {
      builder.append("{\n");
      new ClassGenerator(classNameProvider, this).writeMembers(builder, anonymousBlock, true);
      builder.append("\n}");
    }
  }

  @Override
  public void writeConstructor(StringBuilder text, GrConstructor constructor, int skipParams, boolean isEnum) {
    writeMethod(text, constructor, skipParams);
  }

  @Override
  public void writeMethod(StringBuilder builder, PsiMethod method, int skipOptional) {
    if (method == null) return;
    String name = method.getName();

    boolean isAbstract = GenerationUtil.isAbstractInJava(method);

    PsiModifierList modifierList = method.getModifierList();

    final PsiClass containingClass = method.getContainingClass();
    if (method.isConstructor() && containingClass != null && containingClass.isEnum()) {
      ModifierListGenerator.writeModifiers(builder, modifierList, ModifierListGenerator.ENUM_CONSTRUCTOR_MODIFIERS);
    }
    else {
      ModifierListGenerator.writeModifiers(builder, modifierList);
    }


    if (method.hasTypeParameters()) {
      writeTypeParameters(builder, method, classNameProvider);
      builder.append(" ");
    }

    //append return type
    if (!method.isConstructor()) {
      PsiType retType = context.typeProvider.getReturnType(method);

      /*
      if (!method.hasModifierProperty(PsiModifier.STATIC)) {
        final List<MethodSignatureBackedByPsiMethod> superSignatures = method.findSuperMethodSignaturesIncludingStatic(true);
        for (MethodSignatureBackedByPsiMethod superSignature : superSignatures) {
          final PsiType superType = superSignature.getSubstitutor().substitute(superSignature.getMethod().getReturnType());
          if (superType != null &&
              !superType.isAssignableFrom(retType) &&
              !(PsiUtil.resolveClassInType(superType) instanceof PsiTypeParameter)) {
            retType = superType;
          }
        }
      }
      */

      writeType(builder, retType, method, classNameProvider);
      builder.append(" ");
    }
    builder.append(name);

    if (method instanceof GroovyPsiElement) {
      context.searchForLocalVarsToWrap((GroovyPsiElement)method);
    }
    final ArrayList<GrParameter> actualParams;
    if (method instanceof GrMethod) {
      actualParams = getActualParams((GrMethod)method, skipOptional);
      GenerationUtil.writeParameterList(builder, actualParams.toArray(new GrParameter[actualParams.size()]), classNameProvider, context);
    }
    else {
      LOG.assertTrue(skipOptional == 0);
      GenerationUtil.writeParameterList(builder, method.getParameterList().getParameters(), classNameProvider, context);
      actualParams = null;
    }

    if (method instanceof GrAnnotationMethod) {
      GrDefaultAnnotationValue defaultAnnotationValue = ((GrAnnotationMethod)method).getDefaultAnnotationValue();
      if (defaultAnnotationValue!=null) {
        builder.append("default ");
        GrAnnotationMemberValue defaultValue = defaultAnnotationValue.getDefaultValue();
        if (defaultValue != null) {
          defaultValue.accept(new AnnotationGenerator(builder, context));
        }
      }
    }


    GenerationUtil.writeThrowsList(builder, method.getThrowsList(), getMethodExceptions(method), classNameProvider);

    if (!isAbstract) {
      /************* body **********/
      if (method instanceof GrMethod) {
        if (skipOptional == 0) {
          new CodeBlockGenerator(builder, context.extend()).generateMethodBody((GrMethod)method);
        }
        else {
          builder.append("{\n").append(generateDelegateCall((GrMethod)method, actualParams)).append("\n}\n");
        }
      }
      else if (method instanceof GrAccessorMethod) {
        writeAccessorBody(builder, method);
      }
      else if (method instanceof LightMethodBuilder && containingClass instanceof GroovyScriptClass) {
        if ("main".equals(method.getName())) {
          writeMainScriptMethodBody(builder, method);
        }
        else if ("run".equals(method.getName())) {
          writeRunScriptMethodBody(builder, method);
        }
      }
      else {
        builder.append("{//todo\n}");
      }
    }
    else {
      builder.append(";");
    }
  }

  private StringBuilder generateDelegateCall(GrMethod method, ArrayList<GrParameter> actualParams) {
    final GrParameter[] parameters = method.getParameterList().getParameters();
    StringBuilder builder = new StringBuilder();
    if (method.isConstructor()) {
      builder.append("this");
    }
    else {
      if (context.typeProvider.getReturnType(method) != PsiType.VOID) {
        builder.append("return ");
      }
      builder.append(method.getName());
    }
    builder.append("(");
    for (GrParameter parameter : parameters) {
      if (actualParams.contains(parameter)) {
        builder.append(parameter.getName());
      }
      else {
        LOG.assertTrue(parameter.isOptional());
        final GrExpression initializer = parameter.getDefaultInitializer();
        LOG.assertTrue(initializer != null);
        builder.append(initializer.getText());
      }
      builder.append(", ");
    }
    builder.delete(builder.length() - 2, builder.length());
    builder.append(")");
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(context.project);
    final GrStatement delegateCall;

    PsiElement context = method.getContainingClass() == null ? method : method.getContainingClass();
    if (method.isConstructor()) {
      delegateCall = factory.createConstructorInvocation(builder.toString(), context);
    }
    else {
      delegateCall = factory.createStatementFromText(builder.toString(), context);
    }

    final StringBuilder result = new StringBuilder();
    delegateCall.accept(new CodeBlockGenerator(result, this.context.extend()));
    return result;
  }

  @SuppressWarnings({"MethodMayBeStatic"})
  private void writeMainScriptMethodBody(StringBuilder builder, PsiMethod method) {
    final PsiClass containingClass = method.getContainingClass();
    LOG.assertTrue(containingClass instanceof GroovyScriptClass);
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    LOG.assertTrue(parameters.length == 1);
    builder.append("{\nnew ").append(containingClass.getQualifiedName()).append("(new groovy.lang.Binding(").append(parameters[0].getName())
      .append(")).run();\n}\n");
  }

  private void writeRunScriptMethodBody(StringBuilder builder, PsiMethod method) {
    builder.append("{\n");
    final PsiClass containingClass = method.getContainingClass();
    LOG.assertTrue(containingClass instanceof GroovyScriptClass);
    final PsiFile scriptFile = containingClass.getContainingFile();
    LOG.assertTrue(scriptFile instanceof GroovyFile);
    LOG.assertTrue(((GroovyFile)scriptFile).isScript());
    final List<GrStatement> exitPoints = ControlFlowUtils.collectReturns(scriptFile);


    ExpressionContext extended = context.extend();
    extended.searchForLocalVarsToWrap((GroovyPsiElement)scriptFile);
    new CodeBlockGenerator(builder, extended, exitPoints)
      .visitStatementOwner((GroovyFile)scriptFile, MissingReturnInspection.methodMissesSomeReturns((GroovyFile)scriptFile, true));
    builder.append("\n}\n");
  }

  private static void writeAccessorBody(StringBuilder builder, PsiMethod method) {
    final String propName = ((GrAccessorMethod)method).getProperty().getName();
    if (((GrAccessorMethod)method).isSetter()) {
      final String paramName = method.getParameterList().getParameters()[0].getName();
      builder.append("{\n");
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass != null) {
          builder.append(containingClass.getName());
          builder.append('.');
        }
      }
      else {
        builder.append("this.");
      }
      builder.append(propName);
      builder.append(" = ");
      builder.append(paramName);
      builder.append(";\n}");
    }
    else {
      builder.append("{\n return ");
      builder.append(propName);
      builder.append(";\n}");
    }
  }

  @SuppressWarnings({"MethodMayBeStatic"})
  private PsiClassType[] getMethodExceptions(PsiMethod method) {
    return method.getThrowsList().getReferencedTypes();

    //todo find method exceptions!
  }

  @Override
  public void writeVariableDeclarations(StringBuilder builder, GrVariableDeclaration variableDeclaration) {
    GenerationUtil.writeSimpleVarDeclaration(variableDeclaration, builder, context.extend());
    builder.append('\n');
  }

  @Override
  public Collection<PsiMethod> collectMethods(PsiClass typeDefinition, boolean classDef) {
    List<PsiMethod> result = new ArrayList<PsiMethod>(Arrays.asList(typeDefinition.getMethods()));

    if (typeDefinition instanceof GroovyScriptClass) {
      final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(context.project);
      final String name = typeDefinition.getName();
      result.add(factory.createConstructorFromText(name, new String[]{"groovy.lang.Binding"}, new String[]{"binding"}, "{super(binding);}",
                                                   typeDefinition));
      result.add(
        factory.createConstructorFromText(name, ArrayUtil.EMPTY_STRING_ARRAY, ArrayUtil.EMPTY_STRING_ARRAY, "{super();}", typeDefinition));
    }
    return result;
  }

  @Override
  public boolean generateAnnotations() {
    return true;
  }
}
