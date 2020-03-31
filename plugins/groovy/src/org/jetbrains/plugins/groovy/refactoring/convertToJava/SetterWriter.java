// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;

/**
 * @author Max Medvedev
 */
public class SetterWriter {

  private final StringBuilder myBuffer;
  private final PsiClass myClass;
  private final PsiMethod mySetter;
  private final String myName;
  private final ClassNameProvider myClassNameProvider;
  private final ExpressionContext myContext;

  public SetterWriter(@NotNull StringBuilder builder,
                      @NotNull PsiClass psiClass,
                      @NotNull PsiMethod setter,
                      @NotNull String name,
                      @NotNull ClassNameProvider classNameProvider,
                      @NotNull ExpressionContext context) {
    myBuffer = builder;
    myClass = psiClass;
    myClassNameProvider = classNameProvider;
    myContext = context;
    myName = name;

    if (setter instanceof PsiCompiledElement) setter = (PsiMethod)((PsiCompiledElement)setter).getMirror();
    mySetter = setter;
  }


  public void write() {
    final boolean isStatic = mySetter.hasModifierProperty(PsiModifier.STATIC);

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(myContext.project);

    PsiParameter[] parameters = mySetter.getParameterList().getParameters();
    PsiParameter parameter = parameters[parameters.length - 1];
    final PsiType parameterType = myContext.typeProvider.getParameterType(parameter);

    myBuffer.append("private static ");
    processTypeParameters(parameterType);

    myBuffer.append(myName);

    if (!(parameterType instanceof PsiPrimitiveType)) {
      parameter = factory.createParameter(parameter.getName(), "Value", null);
    }


    PsiParameter[] actual = inferActualParameters(isStatic, parameters, parameter);
    final GroovyPsiElement place = createStubMethod(actual);
    GenerationUtil.writeParameterList(myBuffer, actual, myClassNameProvider, myContext);
    writeBody(isStatic, parameters, parameter, place);
  }

  private void writeBody(boolean aStatic,
                         PsiParameter @NotNull [] parameters,
                         @NotNull PsiParameter parameter, final GroovyPsiElement place) {
    //method body
    myBuffer.append("{\n");

    //arg initialization
    myContext.myUsedVarNames.add("propOwner");
    final GrExpression[] args = generateArguments(parameters, place);

    new ExpressionGenerator(myBuffer, myContext).invokeMethodOn(
      mySetter,
      aStatic ? null : GroovyPsiElementFactory.getInstance(myContext.project).createExpressionFromText("propOwner", place),
      args,
      GrNamedArgument.EMPTY_ARRAY,
      GrClosableBlock.EMPTY_ARRAY,
      PsiSubstitutor.EMPTY,
      place
    );
    myBuffer.append(";\n");
    myBuffer.append("return ").append(parameter.getName()).append(";\n");
    myBuffer.append("}\n");
  }

  private GrExpression @NotNull [] generateArguments(PsiParameter @NotNull [] parameters, @NotNull GroovyPsiElement place) {
    final GrExpression[] args = new GrExpression[parameters.length];
    for (int i = 0; i < parameters.length; i++) {
      args[i] = GroovyPsiElementFactory.getInstance(myContext.project).createExpressionFromText(parameters[i].getName(), place);
      myContext.myUsedVarNames.add(parameters[i].getName());
    }
    return args;
  }

  private GroovyPsiElement createStubMethod(PsiParameter @NotNull [] parameters) {
    StringBuilder methodText = new StringBuilder("def ").append(myName).append('(');
    for (PsiParameter parameter : parameters) {
      methodText.append(parameter.getType().getCanonicalText()).append(' ').append(parameter.getName()).append(',');
    }
    if (parameters.length > 0) methodText.deleteCharAt(methodText.length() - 1);
    methodText.append("){}");
    return GroovyPsiElementFactory.getInstance(myContext.project).createMethodFromText(methodText.toString(), mySetter);
  }

  private PsiParameter @NotNull [] inferActualParameters(boolean aStatic,
                                                         PsiParameter @NotNull [] parameters,
                                                         @NotNull PsiParameter parameter) {
    //parameters
    parameters[parameters.length - 1] = parameter;
    PsiParameter[] actual;
    if (aStatic) {
      actual = parameters;
    }
    else {
      final String typeText;
      final PsiClass containingClass = mySetter.getContainingClass();
      if (containingClass == null) {
        if (mySetter instanceof GrGdkMethod) {
          typeText = ((GrGdkMethod)mySetter).getReceiverType().getCanonicalText();
        }
        else {
          typeText = CommonClassNames.JAVA_LANG_OBJECT;
        }
      }
      else {
        typeText = containingClass.getQualifiedName();
      }

      final GrParameter propOwner = GroovyPsiElementFactory.getInstance(myContext.project).createParameter("propOwner", typeText, null);

      actual = new PsiParameter[parameters.length + 1];
      actual[0] = propOwner;
      System.arraycopy(parameters, 0, actual, 1, parameters.length);
    }
    return actual;
  }

  private void processTypeParameters(PsiType parameterType) {
    //type parameters
    if (mySetter.hasTypeParameters()) {
      GenerationUtil.writeTypeParameters(myBuffer, mySetter, myClassNameProvider);
    }

    if (parameterType instanceof PsiPrimitiveType) {
      myBuffer.append(parameterType.getCanonicalText()).append(' ');
    }
    else {
      if (mySetter.hasTypeParameters()) {
        myBuffer.delete(myBuffer.length() - 1, myBuffer.length());
        //builder.removeFromTheEnd(1);
        myBuffer.append(", ");
      }
      else {
        myBuffer.append('<');
      }
      myBuffer.append("Value");
      if (!parameterType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
        myBuffer.append(" extends ");
        TypeWriter.writeType(myBuffer, parameterType, myClass, myClassNameProvider);
      }
      myBuffer.append('>');
      myBuffer.append("Value ");
    }
  }
}
