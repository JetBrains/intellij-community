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

import com.google.common.collect.ImmutableSortedSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.noReturnMethod.MissingReturnInspection;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumConstantInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.*;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import java.util.*;

/**
 * @author Maxim.Medvedev
 */
public class ClassItemGeneratorImpl implements ClassItemGenerator {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.convertToJava.ClassItemGeneratorImpl");

  private final ClassNameProvider classNameProvider;
  private final ExpressionContext context;

  public ClassItemGeneratorImpl(@NotNull ExpressionContext context) {
    classNameProvider = new GeneratorClassNameProvider();
    this.context = context;
  }

  @Override
  public void writeEnumConstant(StringBuilder builder, GrEnumConstant constant) {
    GenerationUtil.writeDocComment(builder, constant, false);
    builder.append(constant.getName());

    final GrArgumentList argumentList = constant.getArgumentList();
    if (argumentList != null) {
      final GroovyResolveResult resolveResult = constant.advancedResolve();
      GrClosureSignature signature = GrClosureSignatureUtil.createSignature(resolveResult);
      new ArgumentListGenerator(builder, context.extend()).generate(
        signature,
        argumentList.getExpressionArguments(),
        argumentList.getNamedArguments(),
        GrClosableBlock.EMPTY_ARRAY,
        constant
      );
    }

    final GrEnumConstantInitializer anonymousBlock = constant.getInitializingClass();
    if (anonymousBlock != null) {
      builder.append("{\n");
      new ClassGenerator(classNameProvider, this).writeMembers(builder, anonymousBlock);
      builder.append("\n}");
    }
  }

  @Override
  public void writeConstructor(StringBuilder text, PsiMethod constructor, boolean isEnum) {
    writeMethod(text, constructor);
  }

  @Override
  public void writeMethod(StringBuilder builder, PsiMethod method) {
    if (method == null) return;

    GenerationUtil.writeDocComment(builder, method, true);

    String name = method.getName();

    boolean isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);

    PsiModifierList modifierList = method.getModifierList();

    final PsiClass containingClass = method.getContainingClass();
    if (method.isConstructor() && containingClass != null && containingClass.isEnum()) {
      ModifierListGenerator.writeModifiers(builder, modifierList, ModifierListGenerator.ENUM_CONSTRUCTOR_MODIFIERS);
    }
    else {
      ModifierListGenerator.writeModifiers(builder, modifierList);
    }


    if (method.hasTypeParameters()) {
      GenerationUtil.writeTypeParameters(builder, method, classNameProvider);
      builder.append(' ');
    }

    //append return type
    if (!method.isConstructor()) {
      PsiType retType = context.typeProvider.getReturnType(method);
      TypeWriter.writeType(builder, retType, method, classNameProvider);
      builder.append(' ');
    }
    builder.append(name);

    if (method instanceof GroovyPsiElement) {
      context.searchForLocalVarsToWrap((GroovyPsiElement)method);
    }
    GenerationUtil.writeParameterList(builder, method.getParameterList().getParameters(), classNameProvider, context);

    if (method instanceof GrAnnotationMethod) {
      GrAnnotationMemberValue defaultValue = ((GrAnnotationMethod)method).getDefaultValue();
      if (defaultValue != null) {
        builder.append("default ");
        defaultValue.accept(new AnnotationGenerator(builder, context));
      }
    }


    GenerationUtil.writeThrowsList(builder, method.getThrowsList(), getMethodExceptions(method), classNameProvider);

    if (!isAbstract) {
      /************* body **********/
      if (method instanceof GrMethod) {
        if (method instanceof GrReflectedMethod && ((GrReflectedMethod)method).getSkippedParameters().length > 0) {
          builder.append("{\n").append(generateDelegateCall((GrReflectedMethod)method)).append("\n}\n");
        }
        else {
          new CodeBlockGenerator(builder, context.extend()).generateMethodBody((GrMethod)method);
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
      builder.append(';');
    }
  }

  private StringBuilder generateDelegateCall(GrReflectedMethod method) {
    final GrParameter[] actualParams = method.getParameterList().getParameters();

    final GrParameter[] parameters = method.getBaseMethod().getParameters();

    Set<String> actual = new HashSet<>(actualParams.length);
    for (GrParameter param : actualParams) {
      actual.add(param.getName());
    }
    
    StringBuilder builder = new StringBuilder();
    if (method.isConstructor()) {
      builder.append("this");
    }
    else {
      if (!PsiType.VOID.equals(context.typeProvider.getReturnType(method))) {
        builder.append("return ");
      }
      builder.append(method.getName());
    }
    builder.append('(');
    for (GrParameter parameter : parameters) {
      if (actual.contains(parameter.getName())) {
        builder.append(parameter.getName());
      }
      else {
        LOG.assertTrue(parameter.isOptional());
        final GrExpression initializer = parameter.getInitializerGroovy();
        LOG.assertTrue(initializer != null);
        builder.append(initializer.getText());
      }
      builder.append(", ");
    }
    builder.delete(builder.length()-2, builder.length());
    //builder.removeFromTheEnd(2);
    builder.append(')');
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(context.project);
    final GrStatement delegateCall;

    if (method.isConstructor()) {
      delegateCall = factory.createConstructorInvocation(builder.toString(), method);
    }
    else {
      delegateCall = factory.createStatementFromText(builder.toString(), method);
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
      .visitStatementOwner((GroovyFile)scriptFile, MissingReturnInspection
        .methodMissesSomeReturns((GroovyFile)scriptFile, MissingReturnInspection.ReturnStatus.mustReturnValue));
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
  public void writeVariableDeclarations(StringBuilder mainBuilder, GrVariableDeclaration variableDeclaration) {
    ExpressionContext extended = context.extend();
    GrVariable[] variables = variableDeclaration.getVariables();

    if (variables.length > 0 && variables[0] instanceof PsiField) {
      GenerationUtil.writeDocComment(mainBuilder, ((PsiField)variables[0]), true);
    }

    StringBuilder builder = new StringBuilder();
    StringBuilder initBuilder = new StringBuilder();
    initBuilder.append("{\n");
    for (GrVariable variable : variables) {
      PsiType type = extended.typeProvider.getVarType(variable);
      ModifierListGenerator.writeModifiers(builder, variable.getModifierList());

      TypeWriter.writeType(builder, type, variable);
      builder.append(' ');

      builder.append(variable.getName());
      final GrExpression initializer = variable.getInitializerGroovy();

      if (initializer != null) {
        int count = extended.myStatements.size();
        StringBuilder initializerBuilder = new StringBuilder();
        extended.searchForLocalVarsToWrap(initializer);
        initializer.accept(new ExpressionGenerator(initializerBuilder, extended));
        if (extended.myStatements.size() == count) { //didn't use extra statements
          builder.append(" = ").append(initializerBuilder);
        }
        else {
          StringBuilder assignment = new StringBuilder().append(variable.getName()).append(" = ").append(initializerBuilder).append(';');
          GenerationUtil.writeStatement(initBuilder, assignment, null, extended);
        }
      }

      builder.append(";\n");
    }

    if (!extended.myStatements.isEmpty()) {
      initBuilder.append("}\n");
      mainBuilder.append(initBuilder);
    }

     mainBuilder.append(builder);
  }

  @Override
  public Collection<PsiMethod> collectMethods(PsiClass typeDefinition) {
    List<PsiMethod> result = new ArrayList<>(Arrays.asList(typeDefinition.getMethods()));

    if (typeDefinition instanceof GroovyScriptClass) {
      final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(context.project);
      final String name = typeDefinition.getName();
      GrTypeDefinition tempClass = factory.createTypeDefinition("class " + name + " extends groovy.lang.Script {\n" +
                                                                "  def " + name + "(groovy.lang.Binding binding){\n" +
                                                                "    super(binding);\n" +
                                                                "  }\n" +
                                                                "  def " + name + "(){\n" +
                                                                "    super();\n" +
                                                                "  }\n" +
                                                                "}");
      ContainerUtil.addAll(result, tempClass.getCodeConstructors());
    }
    return result;
  }

  @Override
  public boolean generateAnnotations() {
    return true;
  }

  @Override
  public void writePostponed(StringBuilder builder, PsiClass psiClass) {
    if (psiClass.getContainingClass() != null) return;
    if (psiClass instanceof PsiAnonymousClass) return;

    Map<PsiMethod, String> setters = context.getSetters();
    Set<Map.Entry<PsiMethod, String>> entries = setters.entrySet();
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      entries = ImmutableSortedSet.copyOf((o1, o2) -> o1.getValue().compareTo(o2.getValue()), entries);
    }
    for (Map.Entry<PsiMethod, String> entry : entries) {
      new SetterWriter(builder, psiClass, entry.getKey(), entry.getValue(), classNameProvider, context).write();
    }

    final String name = context.getRefSetterName();
    if (name != null) {
      builder.append("private static <T> T ").append(name).
        append("(groovy.lang.Reference<T> ref, T newValue) {\nref.set(newValue);\nreturn newValue;\n}");
    }
  }



  @Override
  public void writeImplementsList(StringBuilder text, PsiClass typeDefinition) {
    final Collection<PsiClassType> implementsTypes = new LinkedHashSet<>();
    Collections.addAll(implementsTypes, typeDefinition.getImplementsListTypes());

    if (implementsTypes.isEmpty()) return;
    if (implementsTypes.size() == 1 && shouldSkipInImplements(typeDefinition, implementsTypes.iterator().next())) return;

    text.append(typeDefinition.isInterface() ? "extends " : "implements ");
    for (PsiClassType implementsType : implementsTypes) {
      if (shouldSkipInImplements(typeDefinition, implementsType)) {
        continue;
      }
      TypeWriter.writeType(text, implementsType, typeDefinition, classNameProvider);
      text.append(", ");
    }
    if (!implementsTypes.isEmpty()) text.delete(text.length() - 2, text.length());
    text.append(' ');
  }

  private static boolean shouldSkipInImplements(PsiClass typeDefinition, PsiClassType implementsType) {
    return implementsType.equalsToText(GroovyCommonClassNames.GROOVY_OBJECT) &&
        typeDefinition instanceof GrTypeDefinition &&
        !typeDefinition.isInterface() &&
        !GenerationSettings.implementGroovyObjectAlways &&
        !isInList(implementsType, ((GrTypeDefinition)typeDefinition).getImplementsClause()) &&
        !containsMethodsOf((GrTypeDefinition)typeDefinition, GroovyCommonClassNames.GROOVY_OBJECT);
  }

  @Override
  public void writeExtendsList(StringBuilder text, PsiClass typeDefinition) {
    final PsiClassType[] extendsClassesTypes = typeDefinition.getExtendsListTypes();

    if (extendsClassesTypes.length > 0) {
      PsiClassType type = extendsClassesTypes[0];

      if (type.equalsToText(GroovyCommonClassNames.GROOVY_OBJECT_SUPPORT) &&
          typeDefinition instanceof GrTypeDefinition &&
          !GenerationSettings.implementGroovyObjectAlways &&
          !isInList(type, ((GrTypeDefinition)typeDefinition).getExtendsClause()) &&
          !containsMethodsOf((GrTypeDefinition)typeDefinition, GroovyCommonClassNames.GROOVY_OBJECT)) {
        return;
      }

      text.append("extends ");
      TypeWriter.writeType(text, type, typeDefinition, classNameProvider);
      text.append(' ');
    }
  }

  private static boolean isInList(@NotNull PsiClassType type, @Nullable GrReferenceList list) {
    if (list == null) return false;

    PsiClass resolved = type.resolve();
    if (resolved == null) return true;

    PsiManager manager = list.getManager();
    GrCodeReferenceElement[] elements = list.getReferenceElementsGroovy();
    for (GrCodeReferenceElement element : elements) {
      if (manager.areElementsEquivalent(resolved, element.resolve())) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsMethodsOf(@NotNull GrTypeDefinition aClass, @NotNull final String fqn) {
    PsiClass classToSearch = JavaPsiFacade.getInstance(aClass.getProject()).findClass(fqn, aClass.getResolveScope());
    if (classToSearch == null) return true;

    Set<String> methodsToFind = new HashSet<>();
    for (PsiMethod method : classToSearch.getMethods()) {
      methodsToFind.add(method.getName());
    }

    for (GrMethod method : aClass.getCodeMethods()) {
      if (!methodsToFind.contains(method.getName())) continue;

      for (HierarchicalMethodSignature superSignature : method.getHierarchicalMethodSignature().getSuperSignatures()) {
        PsiClass superClass = superSignature.getMethod().getContainingClass();
        if (superClass != null && fqn.equals(superClass.getQualifiedName())) return true;
      }
    }

    return false;
  }
}
