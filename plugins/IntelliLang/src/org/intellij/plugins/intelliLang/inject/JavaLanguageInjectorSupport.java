/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.intellij.plugins.intelliLang.inject;

import com.intellij.lang.Language;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.FileContentUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.config.MethodParameterInjection;
import org.intellij.plugins.intelliLang.inject.config.ui.AbstractInjectionPanel;
import org.intellij.plugins.intelliLang.inject.config.ui.MethodParameterPanel;
import org.intellij.plugins.intelliLang.inject.config.ui.configurables.MethodParameterInjectionConfigurable;
import org.intellij.plugins.intelliLang.util.AnnotationUtilEx;
import org.intellij.plugins.intelliLang.util.PsiUtilEx;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Gregory.Shrago
 */
public class JavaLanguageInjectorSupport implements LanguageInjectorSupport {

  private static boolean isMine(final PsiLanguageInjectionHost psiElement) {
    return PsiUtilEx.isStringOrCharacterLiteral(psiElement);
  }

  public boolean addInjectionInPlace(final Language language, final PsiLanguageInjectionHost psiElement) {
    if (!isMine(psiElement)) return false;
    return doInjectInJava(psiElement.getProject(), psiElement, language.getID());
  }

  public boolean removeInjectionInPlace(final PsiLanguageInjectionHost psiElement) {
    if (!isMine(psiElement)) return false;
    final Configuration configuration = Configuration.getInstance();
    final HashMap<MethodParameterInjection, Object> injectionsMap = new HashMap<MethodParameterInjection, Object>();
    final ArrayList<PsiAnnotation> annotations = new ArrayList<PsiAnnotation>();
    final PsiLiteralExpression host = (PsiLiteralExpression)psiElement;
    final Project project = host.getProject();
    collectInjections(host, configuration, injectionsMap, annotations);
    if (injectionsMap.isEmpty() && annotations.isEmpty()) return false;
    final ArrayList<MethodParameterInjection> originalInjections = new ArrayList<MethodParameterInjection>(injectionsMap.keySet());
    final List<MethodParameterInjection> newInjections = ContainerUtil.mapNotNull(originalInjections, new NullableFunction<MethodParameterInjection, MethodParameterInjection>() {
      public MethodParameterInjection fun(final MethodParameterInjection injection) {
        final Object key = injectionsMap.get(injection);
        final MethodParameterInjection newInjection = injection.copy();
        for (MethodParameterInjection.MethodInfo info : newInjection.getMethodInfos()) {
          Configuration.clearMethodParameterFlag(info, (Trinity<String, Integer, Integer>)key);
        }
        for (MethodParameterInjection.MethodInfo info : newInjection.getMethodInfos()) {
          if (info.isEnabled()) return newInjection;
        }
        return null;
      }
    });
    addRemoveInjections(project, configuration, newInjections, originalInjections, annotations);
    return true;
  }

  private static void addRemoveInjections(final Project project, final Configuration configuration, final List<MethodParameterInjection> newInjections,
                                   final List<MethodParameterInjection> originalInjections, final List<PsiAnnotation> annotations) {
    final List<PsiFile> psiFiles = ContainerUtil.mapNotNull(annotations, new NullableFunction<PsiAnnotation, PsiFile>() {
      public PsiFile fun(final PsiAnnotation psiAnnotation) {
        return psiAnnotation instanceof PsiCompiledElement ? null : psiAnnotation.getContainingFile();
      }
    });
    final UndoableAction action = new UndoableAction() {
      public void undo() throws UnexpectedUndoException {
        addRemoveInjectionsInner(project, configuration, originalInjections, newInjections);
      }

      public void redo() throws UnexpectedUndoException {
        addRemoveInjectionsInner(project, configuration, newInjections, originalInjections);
      }

      public DocumentReference[] getAffectedDocuments() {
        return DocumentReference.EMPTY_ARRAY;
      }

      public boolean isComplex() {
        return true;
      }
    };
    new WriteCommandAction.Simple(project, psiFiles.toArray(new PsiFile[psiFiles.size()])) {
      public void run() {
        for (PsiAnnotation annotation : annotations) {
          annotation.delete();
        }
        addRemoveInjectionsInner(project, configuration, newInjections, originalInjections);
        UndoManager.getInstance(project).undoableActionPerformed(action);
      }
    }.execute();
  }

  private static void addRemoveInjectionsInner(final Project project, final Configuration configuration,
                                          final List<MethodParameterInjection> newInjections, final List<MethodParameterInjection> originalInjections) {
    configuration.getParameterInjections().removeAll(originalInjections);
    configuration.getParameterInjections().addAll(newInjections);
    configuration.configurationModified();
    FileContentUtil.reparseFiles(project, Collections.<VirtualFile>emptyList(), true);
  }

  public boolean editInjectionInPlace(final PsiLanguageInjectionHost psiElement) {
    if (!isMine(psiElement)) return false;
    final Configuration configuration = Configuration.getInstance();
    final HashMap<MethodParameterInjection, Object> injectionsMap = new HashMap<MethodParameterInjection, Object>();
    final ArrayList<PsiAnnotation> annotations = new ArrayList<PsiAnnotation>();
    final PsiLiteralExpression host = (PsiLiteralExpression)psiElement;
    final Project project = host.getProject();
    collectInjections(host, configuration, injectionsMap, annotations);
    if (injectionsMap.isEmpty() || !annotations.isEmpty()) return false;
    final ArrayList<MethodParameterInjection> injections = new ArrayList<MethodParameterInjection>(injectionsMap.keySet());

    final MethodParameterInjection originalInjection = injections.get(0);
    final MethodParameterInjection newInjection = originalInjection.copy();
    final AbstractInjectionPanel panel = new MethodParameterPanel(newInjection, project);
    panel.reset();
    final DialogBuilder builder = new DialogBuilder(project);
    builder.addOkAction();
    builder.addCancelAction();
    builder.setCenterPanel(panel.getComponent());
    builder.setTitle(EditInjectionSettingsAction.EDIT_INJECTION_TITLE);
    builder.setOkOperation(new Runnable() {
      public void run() {
        panel.apply();
        builder.getDialogWrapper().close(DialogWrapper.OK_EXIT_CODE);
      }
    });
    if (builder.show() == DialogWrapper.OK_EXIT_CODE) {
      final List<MethodParameterInjection> newInjections =
        ContainerUtil.find(newInjection.getMethodInfos(), new Condition<MethodParameterInjection.MethodInfo>() {
          public boolean value(final MethodParameterInjection.MethodInfo info) {
            return info.isEnabled();
          }
        }) == null ? Collections.<MethodParameterInjection>emptyList() : Collections.singletonList(newInjection);
      addRemoveInjections(project, configuration, newInjections, Collections.singletonList(originalInjection),
                          Collections.<PsiAnnotation>emptyList());
    }
    return true;

  }

  static boolean doInjectInJava(final Project project, final PsiElement host, final String languageId) {
    PsiElement target = host;
    PsiElement parent = target.getParent();
    for (; parent != null; target = parent, parent = target.getParent()) {
      if (parent instanceof PsiBinaryExpression) continue;
      if (parent instanceof PsiParenthesizedExpression) continue;
      if (parent instanceof PsiConditionalExpression && ((PsiConditionalExpression)parent).getCondition() != target) continue;
      break;
    }
    if (parent instanceof PsiReturnStatement ||
        parent instanceof PsiMethod ||
        parent instanceof PsiNameValuePair) {
      return doInjectInJavaMethod(project, findPsiMethod(parent), -1, languageId);
    }
    else if (parent instanceof PsiExpressionList && parent.getParent() instanceof PsiMethodCallExpression) {
      return doInjectInJavaMethod(project, findPsiMethod(parent), findParameterIndex(target, (PsiExpressionList)parent), languageId);
    }
    else if (parent instanceof PsiAssignmentExpression) {
      final PsiExpression psiExpression = ((PsiAssignmentExpression)parent).getLExpression();
      if (psiExpression instanceof PsiReferenceExpression) {
        final PsiElement element = ((PsiReferenceExpression)psiExpression).resolve();
        if (element != null) {
          return doInjectInJava(project, element, languageId);
        }
      }
    }
    else if (parent instanceof PsiVariable) {
      if (doAddLanguageAnnotation(project, (PsiModifierListOwner)parent, languageId)) return true;
    }
    return false;
  }

  static boolean doAddLanguageAnnotation(final Project project, final PsiModifierListOwner modifierListOwner,
                                                 final String languageId) {
    if (modifierListOwner.getModifierList() == null || !PsiUtil.getLanguageLevel(modifierListOwner).hasEnumKeywordAndAutoboxing()) return false;
    new WriteCommandAction(project, modifierListOwner.getContainingFile()) {
      protected void run(final Result result) throws Throwable {
        final String annotationName = org.intellij.lang.annotations.Language.class.getName();
        final PsiAnnotation annotation = JavaPsiFacade.getInstance(project).getElementFactory()
            .createAnnotationFromText("@" + annotationName + "(\"" + languageId + "\")", modifierListOwner);
        final PsiModifierList list = modifierListOwner.getModifierList();
        assert list != null;
        final PsiAnnotation existingAnnotation = list.findAnnotation(annotationName);
        if (existingAnnotation != null) existingAnnotation.replace(annotation);
        else list.addAfter(annotation, null);
        JavaCodeStyleManager.getInstance(getProject()).shortenClassReferences(list);
      }
    }.execute();
    return true;
  }

  static boolean doInjectInJavaMethod(final Project project, final PsiMethod psiMethod, final int parameterIndex,
                                              final String languageId) {
    if (psiMethod == null) return false;
    if (parameterIndex < -1) return false;
    if (parameterIndex >= psiMethod.getParameterList().getParametersCount()) return false;
    final PsiModifierList methodModifiers = psiMethod.getModifierList();
    if (methodModifiers.hasModifierProperty(PsiModifier.PRIVATE) || methodModifiers.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
      return doAddLanguageAnnotation(project, parameterIndex >0? psiMethod.getParameterList().getParameters()[parameterIndex - 1] : psiMethod, languageId);
    }
    final PsiClass containingClass = psiMethod.getContainingClass();
    assert containingClass != null;
    final PsiModifierList classModifiers = containingClass.getModifierList();
    if (classModifiers != null && (classModifiers.hasModifierProperty(PsiModifier.PRIVATE) || classModifiers.hasModifierProperty(PsiModifier.PACKAGE_LOCAL))) {
      return doAddLanguageAnnotation(project, parameterIndex >0? psiMethod.getParameterList().getParameters()[parameterIndex - 1] : psiMethod, languageId);
    }

    final String className = containingClass.getQualifiedName();
    assert className != null;
    final MethodParameterInjection injection = new MethodParameterInjection();
    injection.setInjectedLanguageId(languageId);
    injection.setClassName(className);
    injection.setApplyInHierarchy(true);
    final MethodParameterInjection.MethodInfo info = MethodParameterInjection.createMethodInfo(psiMethod);
    if (parameterIndex < 0) info.setReturnFlag(true);
    else info.getParamFlags()[parameterIndex] = true;
    injection.setMethodInfos(Collections.singletonList(info));
    doEditInjection(project, injection);
    return true;
  }

  static int findParameterIndex(final PsiElement target, final PsiExpressionList parent) {
    final int idx = Arrays.<PsiElement>asList(parent.getExpressions()).indexOf(target);
    return idx < 0? -2 : idx;
  }

  @Nullable
  static PsiMethod findPsiMethod(final PsiElement parent) {
    if (parent instanceof PsiNameValuePair) {
      final PsiAnnotation annotation = PsiTreeUtil.getParentOfType(parent, PsiAnnotation.class);
      if (annotation != null) {
        final PsiJavaCodeReferenceElement referenceElement = annotation.getNameReferenceElement();
        if (referenceElement != null) {
          PsiElement resolved = referenceElement.resolve();
          if (resolved != null) {
            PsiMethod[] methods = ((PsiClass)resolved).findMethodsByName(((PsiNameValuePair)parent).getName(), false);
            if (methods.length == 1) {
              return methods[0];
            }
          }
        }
      }
    }
    final PsiMethod first;
    if (parent.getParent() instanceof PsiMethodCallExpression) {
      first = ((PsiMethodCallExpression)parent.getParent()).resolveMethod();
    }
    else {
      first = PsiTreeUtil.getParentOfType(parent, PsiMethod.class, false);
    }
    if (first == null || first.getContainingClass() == null) return null;
    final LinkedList<PsiMethod> methods = new LinkedList<PsiMethod>();
    methods.add(first);
    while (!methods.isEmpty()) {
      final PsiMethod method = methods.removeFirst();
      final PsiClass psiClass = method.getContainingClass();
      if (psiClass != null && psiClass.getQualifiedName() != null) {
        return method;
      }
      else {
        methods.addAll(Arrays.asList(method.findSuperMethods()));
      }
    }
    return null;
  }

  static void doEditInjection(final Project project, final MethodParameterInjection template) {
    final Configuration configuration = Configuration.getInstance();
    final MethodParameterInjection originalInjection = configuration.findExistingInjection(template);
    final MethodParameterInjection newInjection = originalInjection == null ? template : originalInjection.copy();
    if (originalInjection != null) {
      // merge method infos
      boolean found = false;
      final MethodParameterInjection.MethodInfo curInfo = template.getMethodInfos().iterator().next();
      for (MethodParameterInjection.MethodInfo info : newInjection.getMethodInfos()) {
        if (Comparing.equal(info.getMethodSignature(), curInfo.getMethodSignature())) {
          found = true;
          final boolean[] flags = curInfo.getParamFlags();
          for (int i = 0; i < flags.length; i++) {
            if (flags[i]) {
              info.getParamFlags()[i] = true;
            }
          }
          if (!info.isReturnFlag() && curInfo.isReturnFlag()) info.setReturnFlag(true);
        }
      }
      if (!found) {
        final ArrayList<MethodParameterInjection.MethodInfo> methodInfos = new ArrayList<MethodParameterInjection.MethodInfo>(newInjection.getMethodInfos());
        methodInfos.add(curInfo);
        newInjection.setMethodInfos(methodInfos);
      }
    }
    if (InjectLanguageAction.doEditConfigurable(project, new MethodParameterInjectionConfigurable(newInjection, null, project))) {
      addRemoveInjections(project, configuration, Collections.singletonList(newInjection), Collections.singletonList(originalInjection), Collections.<PsiAnnotation>emptyList());
    }
  }


  private static void collectInjections(final PsiLiteralExpression host, final Configuration configuration,
                                       final Map<MethodParameterInjection, Object> injectionsToRemove,
                                       final ArrayList<PsiAnnotation> annotationsToRemove) {
    ConcatenationInjector.processLiteralExpressionInjectionsInner(configuration, new Processor<ConcatenationInjector.Info>() {
      public boolean process(final ConcatenationInjector.Info info) {
        final PsiAnnotation[] annotations = AnnotationUtilEx.getAnnotationFrom(info.owner, configuration.getLanguageAnnotationPair(), true);
        annotationsToRemove.addAll(Arrays.asList(annotations));
        for (MethodParameterInjection injection : info.injections) {
          if (injection.isApplicable(info.method)) {
            injectionsToRemove.put(injection, info.key);
          }
        }
        return true;
      }
    }, host);
  }


}