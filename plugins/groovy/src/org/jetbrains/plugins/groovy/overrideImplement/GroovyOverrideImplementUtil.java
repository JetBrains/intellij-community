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
package org.jetbrains.plugins.groovy.overrideImplement;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.ide.util.MemberChooser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTopLevelDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.io.IOException;
import java.util.*;

/**
 * User: Dmitry.Krasilschikov
 * Date: 14.09.2007
 */
public class GroovyOverrideImplementUtil {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.overrideImplement.GroovyOverrideImplementUtil");
  private static final String JAVA_LANG_OVERRIDE = "java.lang.Override";

  private GroovyOverrideImplementUtil() {
  }

  public static void invokeOverrideImplement(final Editor editor, final PsiFile file, final boolean isImplement) {
    final int offset = editor.getCaretModel().getOffset();

    GrTypeDefinition aClass = PsiTreeUtil.findElementOfClassAtOffset(file, offset, GrTypeDefinition.class, false);
    if (aClass == null) {
      return;
    }

    if (isImplement && aClass.isInterface()) return;

    Collection<CandidateInfo> candidates = getMethodsToOverrideImplement(aClass, isImplement);
    Collection<CandidateInfo> secondary = isImplement ? Collections.<CandidateInfo>emptyList() : getMethodsToOverrideImplement(aClass, !isImplement);

    MemberChooser<PsiMethodMember> chooser = OverrideImplementUtil.showOverrideImplementChooser(editor, aClass, isImplement, candidates, secondary);
    if (chooser == null) return;

    final List<PsiMethodMember> selectedElements = chooser.getSelectedElements();
    if (selectedElements == null || selectedElements.size() == 0) return;

    for (PsiMethodMember methodMember : selectedElements) {
      if (!aClass.isValid()) {
        aClass = PsiTreeUtil.findElementOfClassAtOffset(file, offset, GrTypeDefinition.class, false);
        assert aClass != null;
      }
      generateImplementation(editor, aClass, methodMember.getElement(), methodMember.getSubstitutor());
    }
  }

  public static void generateImplementation(@Nullable final Editor editor,
                                            final GrTypeDefinition aClass,
                                            final PsiMethod method,
                                            final PsiSubstitutor substitutor) {

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final PsiElement anchor = findAnchor(aClass, editor);
        final GrMethod result = generateMethodPrototype(aClass, method, substitutor);
        final GrMethod addedMethod = aClass.addMemberDeclaration(result, anchor);

        PsiUtil.shortenReferences(addedMethod);
        PsiFile psiFile = aClass.getContainingFile();
        FileEditorManager fileEditorManager = FileEditorManager.getInstance(aClass.getProject());
        Editor editor = fileEditorManager.openTextEditor(new OpenFileDescriptor(psiFile.getProject(), psiFile.getVirtualFile()), false);
        if (editor == null) return;

        GenerateMembersUtil.positionCaret(editor, addedMethod, true);
        editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
      }
    });
  }

  public static GrMethod generateMethodPrototype(GrTypeDefinition aClass,
                                                 PsiMethod method,
                                                 PsiSubstitutor substitutor) {
    final Project project = aClass.getProject();
    final boolean isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);

    String templName = isAbstract ? JavaTemplateUtil.TEMPLATE_IMPLEMENTED_METHOD_BODY : JavaTemplateUtil.TEMPLATE_OVERRIDDEN_METHOD_BODY;
    final FileTemplate template = FileTemplateManager.getInstance().getCodeTemplate(templName);
    final GrMethod result = createOverrideImplementMethodSignature(project, method, substitutor, aClass);
    PsiModifierList modifierList = result.getModifierList();
    modifierList.setModifierProperty(PsiModifier.ABSTRACT, false);
    modifierList.setModifierProperty(PsiModifier.NATIVE, false);

    setupOverridingMethodBody(project, method, result, template, substitutor);
    if (OverrideImplementUtil.isInsertOverride(method, aClass)) {
      result.getModifierList().addAnnotation(JAVA_LANG_OVERRIDE);
    }
    return result;
  }

  @Nullable
  private static PsiElement findAnchor(GrTypeDefinition aClass, Editor editor) {
    PsiFile file = aClass.getContainingFile();
    final GrTypeDefinitionBody classBody = aClass.getBody();
    final PsiMethod[] methods = aClass.getMethods();

    PsiElement anchor = null;

    final PsiElement atCaret;
    final GrTopLevelDefinition previousTopLevelElement;
    if (editor == null) {
      atCaret = null;
      previousTopLevelElement = null;
    }
    else {
      final int caretPosition = editor.getCaretModel().getOffset();
      atCaret = file.findElementAt(caretPosition);

      previousTopLevelElement = PsiUtil.findPreviousTopLevelElementByThisElement(atCaret);
    }

    if (atCaret != null && atCaret.getParent() instanceof GrTypeDefinitionBody) {
      if (GroovyTokenTypes.mRCURLY.equals(atCaret.getNode().getElementType())) {
        anchor = atCaret.getPrevSibling();
      } else {
        anchor = atCaret;
      }

      if (GroovyTokenTypes.mLCURLY.equals(anchor.getNode().getElementType())) {
        anchor = anchor.getNextSibling();
      }

    } else if (previousTopLevelElement != null && previousTopLevelElement instanceof GrMethod) {
      final PsiElement nextElement = previousTopLevelElement.getNextSibling();
      if (nextElement != null) {
        anchor = nextElement;
      }
    } else if (methods.length != 0) {
      final PsiMethod lastMethod = methods[methods.length - 1];
      if (lastMethod != null) {
        final PsiElement nextSibling = lastMethod.getNextSibling();
        if (nextSibling != null) {
          anchor = nextSibling;
        }
      }

    } else {
      final PsiElement firstChild = classBody.getFirstChild();
      assert firstChild != null;
      final PsiElement nextElement = firstChild.getNextSibling();
      assert nextElement != null;

      anchor = nextElement;
    }
    return anchor;
  }

  public static Collection<CandidateInfo> getMethodsToOverrideImplement(GrTypeDefinition aClass, boolean isImplement) {
    return OverrideImplementUtil.getMethodsToOverrideImplement(aClass, isImplement);
  }

  private static boolean writeMethodModifiers(StringBuffer text, PsiModifierList modifierList, String[] modifiers) {
    boolean wasAddedModifiers = false;
    for (@Modifier String modifierType : modifiers) {
      if (modifierList.hasModifierProperty(modifierType) && modifierType != PsiModifier.PUBLIC) {
        text.append(modifierType);
        text.append(" ");
        wasAddedModifiers = true;
      }
    }
    return wasAddedModifiers;
  }


  private static final String[] GROOVY_MODIFIERS = new String[]{
      PsiModifier.PUBLIC,
      PsiModifier.PROTECTED,
      PsiModifier.PRIVATE,
      PsiModifier.STATIC,
      PsiModifier.ABSTRACT,
      PsiModifier.FINAL,
      PsiModifier.SYNCHRONIZED,
  };


  private static GrMethod createOverrideImplementMethodSignature(Project project, PsiMethod superMethod, PsiSubstitutor substitutor, PsiClass aClass) {
    StringBuffer buffer = new StringBuffer();
    final boolean hasModifiers = writeMethodModifiers(buffer, superMethod.getModifierList(), GROOVY_MODIFIERS);

    final PsiTypeParameter[] superTypeParameters = superMethod.getTypeParameters();
    final List<PsiTypeParameter> typeParameters = new ArrayList<PsiTypeParameter>();
    final Map<PsiTypeParameter, PsiType> map = substitutor.getSubstitutionMap();
    for (PsiTypeParameter parameter : superTypeParameters) {
      if (!map.containsKey(parameter)) {
        typeParameters.add(parameter);
      }
    }

    final PsiType returnType = substitutor.substitute(getSuperReturnType(superMethod));
    if (!hasModifiers && returnType == null || typeParameters.size() > 0) {
      buffer.append("def ");
    }

    if (typeParameters.size() > 0) {
      buffer.append('<');
      for (PsiTypeParameter parameter : typeParameters) {
        buffer.append(parameter.getText());
        buffer.append(", ");
      }
      buffer.replace(buffer.length() - 2, buffer.length(), ">");
    }

    if (superMethod.isConstructor()) {
      buffer.append(aClass.getName());
    }
    else {
      if (returnType != null) {
        buffer.append(returnType.getCanonicalText()).append(" ");
      }

      buffer.append(superMethod.getName());
    }
    buffer.append("(");
    final PsiParameter[] parameters = superMethod.getParameterList().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      if (i > 0) buffer.append(", ");
      PsiParameter parameter = parameters[i];
      final PsiType parameterType = substitutor.substitute(parameter.getType());
      buffer.append(parameterType.getCanonicalText());
      buffer.append(" ");
      final String paramName = parameter.getName();
      if (paramName != null) {
        buffer.append(paramName);
      }
      else if (parameter instanceof PsiCompiledElement) {
        buffer.append(((PsiParameter)((PsiCompiledElement)parameter).getMirror()).getName());
      }
    }

    buffer.append(") {}");

    return (GrMethod) GroovyPsiElementFactory.getInstance(project).createTopElementFromText(buffer.toString());
  }

  @Nullable
  private static PsiType getSuperReturnType(PsiMethod superMethod) {
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
    Properties properties = new Properties();

    properties.setProperty(FileTemplate.ATTRIBUTE_RETURN_TYPE, returnTypeText);
    properties.setProperty(FileTemplate.ATTRIBUTE_DEFAULT_RETURN_VALUE, PsiTypesUtil.getDefaultValueOfType(returnType));
    properties.setProperty(FileTemplate.ATTRIBUTE_CALL_SUPER, callSuper(method, resultMethod));
    JavaTemplateUtil.setClassAndMethodNameProperties(properties, method.getContainingClass(), resultMethod);

    try {
      String bodyText = StringUtil.replace(template.getText(properties), ";", "");
      final GrCodeBlock newBody = GroovyPsiElementFactory.getInstance(project).createMethodBodyFromText("\n" + bodyText + "\n");

      resultMethod.setBlock(newBody);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @NotNull
  private static String callSuper(PsiMethod superMethod, PsiMethod overriding) {
    @NonNls StringBuilder buffer = new StringBuilder();
    if (!superMethod.isConstructor() && superMethod.getReturnType() != PsiType.VOID) {
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

  public static Collection<CandidateInfo> getMethodsToImplement(PsiClass typeDefinition) {
    Collection<CandidateInfo> methodsToImplement = OverrideImplementUtil.getMethodsToOverrideImplement(typeDefinition, true);
    methodsToImplement = ContainerUtil.findAll(methodsToImplement, new Condition<CandidateInfo>() {
      public boolean value(CandidateInfo candidateInfo) {
        //noinspection ConstantConditions
        return !GroovyCommonClassNames.DEFAULT_BASE_CLASS_NAME
          .equals(((PsiMethod)candidateInfo.getElement()).getContainingClass().getQualifiedName());
      }
    });
    return methodsToImplement;
  }  
}
