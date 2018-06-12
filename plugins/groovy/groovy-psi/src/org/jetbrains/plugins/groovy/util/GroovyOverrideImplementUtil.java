// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.featureStatistics.ProductivityFeatureNames;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.ide.util.MemberChooser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

public class GroovyOverrideImplementUtil {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.util.GroovyOverrideImplementUtil");

  private GroovyOverrideImplementUtil() {
  }

  public static GrMethod generateMethodPrototype(GrTypeDefinition aClass,
                                                 PsiMethod method,
                                                 PsiSubstitutor substitutor) {
    final Project project = aClass.getProject();
    final boolean isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);

    String templName = isAbstract ? JavaTemplateUtil.TEMPLATE_IMPLEMENTED_METHOD_BODY : JavaTemplateUtil.TEMPLATE_OVERRIDDEN_METHOD_BODY;
    final FileTemplate template = FileTemplateManager.getInstance(method.getProject()).getCodeTemplate(templName);
    final GrMethod result = (GrMethod)GenerateMembersUtil.substituteGenericMethod(method, substitutor, aClass);

    setupModifierList(result);
    setupOverridingMethodBody(project, method, result, template, substitutor);
    setupReturnType(result, method);

    setupAnnotations(aClass, method, result);

    GroovyChangeContextUtil.encodeContextInfo(result);
    return result;
  }

  public static GrMethod generateTraitMethodPrototype(GrTypeDefinition aClass, GrTraitMethod method, PsiSubstitutor substitutor) {
    final Project project = aClass.getProject();

    final GrMethod result = (GrMethod)GenerateMembersUtil.substituteGenericMethod(method, substitutor, aClass);

    setupModifierList(result);
    setupTraitMethodBody(project, result, method);
    setupReturnType(result, method);

    setupAnnotations(aClass, method, result);

    GroovyChangeContextUtil.encodeContextInfo(result);
    return result;
  }


  private static void setupReturnType(GrMethod result, PsiMethod method) {
    if (method instanceof GrMethod && ((GrMethod)method).getReturnTypeElementGroovy() == null) {
      result.setReturnType(null);
      GrModifierList modifierList = result.getModifierList();
      if (!modifierList.hasExplicitVisibilityModifiers()) {
        modifierList.setModifierProperty(GrModifier.DEF, true);
      }
    }
  }

  private static void setupAnnotations(@NotNull GrTypeDefinition aClass, @NotNull PsiMethod method, @NotNull GrMethod result) {
    if (OverrideImplementUtil.isInsertOverride(method, aClass)) {
      result.getModifierList().addAnnotation(CommonClassNames.JAVA_LANG_OVERRIDE);
    }

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(method.getProject());

    final PsiParameter[] originalParams = method.getParameterList().getParameters();

    GrParameter[] parameters = result.getParameters();
    for (int i = 0; i < parameters.length; i++) {
      GrParameter parameter = parameters[i];
      PsiParameter original = originalParams[i];

      for (PsiAnnotation annotation : original.getModifierList().getAnnotations()) {
        final GrModifierList modifierList = parameter.getModifierList();

        String qname = annotation.getQualifiedName();
        if (qname != null && !modifierList.hasAnnotation(qname)) {
          if (annotation instanceof GrAnnotation) {
            modifierList.add(annotation);
          }
          else {
            modifierList.add(factory.createAnnotationFromText(annotation.getText()));
          }
        }
      }
    }
  }

  private static void setupModifierList(GrMethod result) {
    PsiModifierList modifierList = result.getModifierList();
    modifierList.setModifierProperty(PsiModifier.ABSTRACT, false);
    modifierList.setModifierProperty(PsiModifier.NATIVE, false);
  }


  @Nullable
  private static PsiType getSuperReturnType(@NotNull PsiMethod superMethod) {
    if (superMethod instanceof GrMethod) {
      final GrTypeElement element = ((GrMethod)superMethod).getReturnTypeElementGroovy();
      return element != null ? element.getType() : null;
    }

    return superMethod.getReturnType();
  }

  private static void setupOverridingMethodBody(Project project,
                                                PsiMethod method,
                                                GrMethod resultMethod,
                                                FileTemplate template,
                                                PsiSubstitutor substitutor) {
    final PsiType returnType = substitutor.substitute(getSuperReturnType(method));

    String returnTypeText = "";
    if (returnType != null) {
      returnTypeText = returnType.getPresentableText();
    }
    Properties properties = FileTemplateManager.getInstance(project).getDefaultProperties();

    properties.setProperty(FileTemplate.ATTRIBUTE_RETURN_TYPE, returnTypeText);
    properties.setProperty(FileTemplate.ATTRIBUTE_DEFAULT_RETURN_VALUE, PsiTypesUtil.getDefaultValueOfType(returnType));
    properties.setProperty(FileTemplate.ATTRIBUTE_CALL_SUPER, callSuper(method, resultMethod));
    JavaTemplateUtil.setClassAndMethodNameProperties(properties, method.getContainingClass(), resultMethod);

    try {
      String bodyText = StringUtil.replace(template.getText(properties), ";", "");
      GroovyFile file = GroovyPsiElementFactory.getInstance(project).createGroovyFile("\n " + bodyText + "\n", false, null);

      GrOpenBlock block = resultMethod.getBlock();
      block.getNode().addChildren(file.getFirstChild().getNode(), null, block.getRBrace().getNode());
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private static void setupTraitMethodBody(Project project, GrMethod resultMethod, GrTraitMethod traitMethod) {
    PsiClass traitClass = traitMethod.getPrototype().getContainingClass();

    StringBuilder builder = new StringBuilder();
    builder.append("\nreturn ");
    builder.append(traitClass.getQualifiedName());
    builder.append(".super.");
    builder.append(traitMethod.getName());
    builder.append("(");
    GrParameter[] parameters = resultMethod.getParameters();
    for (GrParameter parameter : parameters) {
      builder.append(parameter.getName()).append(",");
    }
    if (parameters.length > 0) {
      builder.replace(builder.length() - 1, builder.length(), ")\n");
    }
    else {
      builder.append(")\n");
    }

    GroovyFile file = GroovyPsiElementFactory.getInstance(project).createGroovyFile(builder, false, null);

    GrOpenBlock block = resultMethod.getBlock();
    block.getNode().addChildren(file.getFirstChild().getNode(), null, block.getRBrace().getNode());
  }

  public static void chooseAndOverrideMethods(@NotNull Project project,
                                              @NotNull Editor editor,
                                              @NotNull GrTypeDefinition aClass){
    FeatureUsageTracker.getInstance().triggerFeatureUsed(ProductivityFeatureNames.CODEASSISTS_OVERRIDE_IMPLEMENT);
    chooseAndOverrideOrImplementMethods(project, editor, aClass, false);
  }

  public static void chooseAndImplementMethods(@NotNull Project project,
                                               @NotNull Editor editor,
                                               @NotNull GrTypeDefinition aClass){
    FeatureUsageTracker.getInstance().triggerFeatureUsed(ProductivityFeatureNames.CODEASSISTS_OVERRIDE_IMPLEMENT);
    chooseAndOverrideOrImplementMethods(project, editor, aClass, true);
  }

  public static void chooseAndOverrideOrImplementMethods(@NotNull Project project,
                                                         @NotNull final Editor editor,
                                                         @NotNull final GrTypeDefinition aClass,
                                                         boolean toImplement) {
    LOG.assertTrue(aClass.isValid());
    ApplicationManager.getApplication().assertReadAccessAllowed();

    Collection<CandidateInfo> candidates = GroovyOverrideImplementExploreUtil.getMethodsToOverrideImplement(aClass, toImplement);
    Collection<CandidateInfo> secondary = toImplement || aClass.isInterface() ? ContainerUtil.newArrayList()
                                                                              : GroovyOverrideImplementExploreUtil
                                            .getMethodsToOverrideImplement(aClass, true);

    if (toImplement) {
      for (Iterator<CandidateInfo> iterator = candidates.iterator(); iterator.hasNext(); ) {
        CandidateInfo candidate = iterator.next();
        PsiElement element = candidate.getElement();
        if (element instanceof GrMethod) {
          GrMethod method = (GrMethod)element;
          if (GrTraitUtil.isTrait(method.getContainingClass()) && !GrTraitUtil.isMethodAbstract(method)) {
            iterator.remove();
            secondary.add(candidate);
          }
        }
      }
    }

    final MemberChooser<PsiMethodMember> chooser =
      OverrideImplementUtil.showOverrideImplementChooser(editor, aClass, toImplement, candidates, secondary);
    if (chooser == null) return;

    final List<PsiMethodMember> selectedElements = chooser.getSelectedElements();
    if (selectedElements == null || selectedElements.isEmpty()) return;

    LOG.assertTrue(aClass.isValid());
    WriteCommandAction.writeCommandAction(project, aClass.getContainingFile()).run(() -> {
      OverrideImplementUtil.overrideOrImplementMethodsInRightPlace(editor, aClass, selectedElements, chooser.isCopyJavadoc(),
                                                                   chooser.isInsertOverrideAnnotation());
    });
  }

  @NotNull
  private static String callSuper(PsiMethod superMethod, PsiMethod overriding) {
    @NonNls StringBuilder buffer = new StringBuilder();
    if (!superMethod.isConstructor() && !PsiType.VOID.equals(superMethod.getReturnType())) {
      buffer.append("return ");
    }
    buffer.append("super");
    PsiParameter[] parms = overriding.getParameterList().getParameters();
    if (!superMethod.isConstructor()) {
      buffer.append(".");
      buffer.append(superMethod.getName());
    }
    buffer.append("(");
    for (int i = 0; i < parms.length; i++) {
      String name = parms[i].getName();
      if (i > 0) buffer.append(",");
      buffer.append(name);
    }
    buffer.append(")");
    return buffer.toString();
  }
}
