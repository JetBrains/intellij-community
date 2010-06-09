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

package org.intellij.plugins.intelliLang.inject.java;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.SimpleColoredText;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Consumer;
import com.intellij.util.Icons;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.plugins.intelliLang.AdvancedSettingsUI;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.AbstractLanguageInjectionSupport;
import org.intellij.plugins.intelliLang.inject.EditInjectionSettingsAction;
import org.intellij.plugins.intelliLang.inject.InjectLanguageAction;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.intellij.plugins.intelliLang.inject.config.InjectionPlace;
import org.intellij.plugins.intelliLang.inject.config.MethodParameterInjection;
import org.intellij.plugins.intelliLang.inject.config.ui.AbstractInjectionPanel;
import org.intellij.plugins.intelliLang.inject.config.ui.MethodParameterPanel;
import org.intellij.plugins.intelliLang.inject.config.ui.configurables.MethodParameterInjectionConfigurable;
import org.intellij.plugins.intelliLang.util.AnnotationUtilEx;
import org.intellij.plugins.intelliLang.util.ContextComputationProcessor;
import org.intellij.plugins.intelliLang.util.PsiUtilEx;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.intellij.plugins.intelliLang.inject.config.MethodParameterInjection.*;

/**
 * @author Gregory.Shrago
 */
public class JavaLanguageInjectionSupport extends AbstractLanguageInjectionSupport {

  private static boolean isMine(final PsiLanguageInjectionHost psiElement) {
    return PsiUtilEx.isStringOrCharacterLiteral(psiElement);
  }

  @NotNull
  public String getId() {
    return JAVA_SUPPORT_ID;
  }

  @NotNull
  public Class[] getPatternClasses() {
    return new Class[] { PsiJavaPatterns.class };
  }

  public boolean useDefaultInjector(final PsiElement host) {
    return false;
  }

  public Configurable[] createSettings(final Project project, final Configuration configuration) {
    return new Configurable[]{new AdvancedSettingsUI(project, configuration)};
  }

  public boolean addInjectionInPlace(final Language language, final PsiLanguageInjectionHost psiElement) {
    if (!isMine(psiElement)) return false;
    return doInjectInJava(psiElement.getProject(), psiElement, language.getID());
  }

  public boolean removeInjectionInPlace(final PsiLanguageInjectionHost psiElement) {
    if (!isMine(psiElement)) return false;
    final Configuration configuration = Configuration.getInstance();
    final HashMap<BaseInjection, Pair<PsiMethod, Integer>> injectionsMap = new HashMap<BaseInjection, Pair<PsiMethod, Integer>>();
    final ArrayList<PsiAnnotation> annotations = new ArrayList<PsiAnnotation>();
    final PsiLiteralExpression host = (PsiLiteralExpression)psiElement;
    final Project project = host.getProject();
    collectInjections(host, configuration, injectionsMap, annotations);

    if (injectionsMap.isEmpty() && annotations.isEmpty()) return false;
    final ArrayList<BaseInjection> originalInjections = new ArrayList<BaseInjection>(injectionsMap.keySet());
    final List<BaseInjection> newInjections = ContainerUtil.mapNotNull(originalInjections, new NullableFunction<BaseInjection, BaseInjection>() {
      public BaseInjection fun(final BaseInjection injection) {
        final Pair<PsiMethod, Integer> pair = injectionsMap.get(injection);
        final String placeText = getPatternStringForJavaPlace(pair.first, pair.second);
        final BaseInjection newInjection = injection.copy();
        newInjection.setPlaceEnabled(placeText, false);
        return newInjection;
      }
    });
    Configuration.getInstance().replaceInjectionsWithUndo(project, newInjections, originalInjections, annotations);
    return true;
  }

  public boolean editInjectionInPlace(final PsiLanguageInjectionHost psiElement) {
    if (!isMine(psiElement)) return false;
    final Configuration configuration = Configuration.getInstance();
    final HashMap<BaseInjection, Pair<PsiMethod, Integer>> injectionsMap = new HashMap<BaseInjection, Pair<PsiMethod, Integer>>();
    final ArrayList<PsiAnnotation> annotations = new ArrayList<PsiAnnotation>();
    final PsiLiteralExpression host = (PsiLiteralExpression)psiElement;
    final Project project = host.getProject();
    collectInjections(host, configuration, injectionsMap, annotations);
    if (injectionsMap.isEmpty() || !annotations.isEmpty()) return false;

    final BaseInjection originalInjection = injectionsMap.keySet().iterator().next();
    final MethodParameterInjection methodParameterInjection = createFrom(psiElement.getProject(), originalInjection, injectionsMap.get(originalInjection).first, false);
    final MethodParameterInjection copy = methodParameterInjection.copy();
    final BaseInjection newInjection = showInjectionUI(project, methodParameterInjection);
    if (newInjection != null) {
      copy.initializePlaces(false);
      newInjection.mergeOriginalPlacesFrom(copy, false);
      newInjection.mergeOriginalPlacesFrom(originalInjection, true);
      Configuration.getInstance().replaceInjectionsWithUndo(
        project, Collections.singletonList(newInjection), Collections.singletonList(originalInjection), Collections.<PsiAnnotation>emptyList());
    }
    return true;

  }

  private BaseInjection showInjectionUI(final Project project, final MethodParameterInjection methodParameterInjection) {
    final AbstractInjectionPanel panel = new MethodParameterPanel(methodParameterInjection, project);
    panel.reset();
    final DialogBuilder builder = new DialogBuilder(project);
    builder.setHelpId("reference.settings.injection.language.injection.settings.java.parameter");
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
      methodParameterInjection.initializePlaces(false);
      return new BaseInjection(methodParameterInjection.getSupportId()).copyFrom(methodParameterInjection);
    }
    return null;
  }

  public BaseInjection createInjection(final Element element) {
    if (element.getName().equals(MethodParameterInjection.class.getSimpleName())) {
      return new MethodParameterInjection();
    }
    else return new BaseInjection(JAVA_SUPPORT_ID);
  }

  private static boolean doInjectInJava(final Project project, final PsiElement host, final String languageId) {
    final PsiElement target = ContextComputationProcessor.getTopLevelInjectionTarget(host);
    final PsiElement parent = target.getParent();
    if (parent instanceof PsiReturnStatement ||
        parent instanceof PsiMethod ||
        parent instanceof PsiNameValuePair) {
      return doInjectInJavaMethod(project, findPsiMethod(parent), -1, languageId);
    }
    else if (parent instanceof PsiExpressionList && parent.getParent() instanceof PsiCallExpression) {
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
    if (!OrderEntryFix.ensureAnnotationsJarInPath(ModuleUtil.findModuleForPsiElement(modifierListOwner), AnnotationUtil.LANGUAGE)) return false;
    new WriteCommandAction(project, modifierListOwner.getContainingFile()) {
      protected void run(final Result result) throws Throwable {
        final PsiAnnotation annotation = JavaPsiFacade.getInstance(project).getElementFactory()
            .createAnnotationFromText("@" + AnnotationUtil.LANGUAGE + "(\"" + languageId + "\")", modifierListOwner);
        final PsiModifierList list = modifierListOwner.getModifierList();
        assert list != null;
        final PsiAnnotation existingAnnotation = list.findAnnotation(AnnotationUtil.LANGUAGE);
        if (existingAnnotation != null) {
          existingAnnotation.replace(annotation);
        }
        else {
          list.addAfter(annotation, null);
        }
        JavaCodeStyleManager.getInstance(getProject()).shortenClassReferences(list);
      }
    }.execute();
    return true;
  }

  private static boolean doInjectInJavaMethod(final Project project, final PsiMethod psiMethod, final int parameterIndex,
                                              final String languageId) {
    if (psiMethod == null) return false;
    if (parameterIndex < -1) return false;
    if (parameterIndex >= psiMethod.getParameterList().getParametersCount()) return false;
    final PsiModifierList methodModifiers = psiMethod.getModifierList();
    if (methodModifiers.hasModifierProperty(PsiModifier.PRIVATE) || methodModifiers.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
      return doAddLanguageAnnotation(project, parameterIndex >= 0? psiMethod.getParameterList().getParameters()[parameterIndex] : psiMethod, languageId);
    }
    final PsiClass containingClass = psiMethod.getContainingClass();
    assert containingClass != null;
    final PsiModifierList classModifiers = containingClass.getModifierList();
    if (classModifiers != null && (classModifiers.hasModifierProperty(PsiModifier.PRIVATE) || classModifiers.hasModifierProperty(PsiModifier.PACKAGE_LOCAL))) {
      return doAddLanguageAnnotation(project, parameterIndex >= 0? psiMethod.getParameterList().getParameters()[parameterIndex] : psiMethod, languageId);
    }

    final String className = containingClass.getQualifiedName();
    assert className != null;
    final MethodParameterInjection injection = new MethodParameterInjection();
    injection.setInjectedLanguageId(languageId);
    injection.setClassName(className);
    final MethodParameterInjection.MethodInfo info = createMethodInfo(psiMethod);
    if (parameterIndex < 0) {
      info.setReturnFlag(true);
    }
    else {
      info.getParamFlags()[parameterIndex] = true;
    }
    injection.setMethodInfos(Collections.singletonList(info));
    doEditInjection(project, injection, psiMethod);
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
    if (parent.getParent() instanceof PsiCallExpression) {
      first = ((PsiCallExpression)parent.getParent()).resolveMethod();
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

  private static void doEditInjection(final Project project, final MethodParameterInjection template, final PsiMethod contextMethod) {
    final Configuration configuration = Configuration.getInstance();
    template.initializePlaces(false);
    final BaseInjection baseTemplate = new BaseInjection(template.getSupportId()).copyFrom(template);
    final MethodParameterInjection allMethodParameterInjection = createFrom(project, baseTemplate, contextMethod, true);
    allMethodParameterInjection.initializePlaces(false);
    // find existing injection for this class.
    final BaseInjection originalInjection = configuration.findExistingInjection(allMethodParameterInjection);
    final MethodParameterInjection methodParameterInjection;
    if (originalInjection == null) {
      methodParameterInjection = template;
    }
    else {
      final BaseInjection originalCopy = originalInjection.copy();
      final InjectionPlace currentPlace = template.getInjectionPlaces().get(0);
      originalCopy.mergeOriginalPlacesFrom(template, true);
      originalCopy.setPlaceEnabled(currentPlace.getText(), true);
      methodParameterInjection = createFrom(project, originalCopy, contextMethod, false);
    }
    if (InjectLanguageAction.doEditConfigurable(project, new MethodParameterInjectionConfigurable(methodParameterInjection, null, project))) {
      methodParameterInjection.initializePlaces(false);
      final BaseInjection newInjection = new BaseInjection(methodParameterInjection.getSupportId()).copyFrom(methodParameterInjection);
      if (originalInjection != null) {
        newInjection.mergeOriginalPlacesFrom(originalInjection, true);
      }
      Configuration.getInstance().replaceInjectionsWithUndo(
        project, Collections.singletonList(newInjection),
        ContainerUtil.createMaybeSingletonList(originalInjection),
        Collections.<PsiElement>emptyList());
    }
  }

  private static void collectInjections(final PsiLiteralExpression host, final Configuration configuration,
                                        final HashMap<BaseInjection, Pair<PsiMethod, Integer>> injectionsMap,
                                        final ArrayList<PsiAnnotation> annotations) {
    new ConcatenationInjector.InjectionProcessor(configuration, host) {
      @Override
      protected boolean processAnnotationInjections(PsiModifierListOwner annoElement) {
        annotations.addAll(Arrays.asList(AnnotationUtilEx.getAnnotationFrom(annoElement, configuration.getLanguageAnnotationPair(), true)));
        return true;
      }

      @Override
      protected boolean processXmlInjections(BaseInjection injection, PsiModifierListOwner owner, PsiMethod method, int paramIndex) {
        injectionsMap.put(injection, Pair.create(method, paramIndex));
        return true;
      }
    }.processInjections();
  }

  private static MethodParameterInjection createFrom(final Project project,
                                                                         final BaseInjection injection,
                                                                         final PsiMethod contextMethod,
                                                                         final boolean includeAllPlaces) {
    final PsiClass containingClass;
    final String className;
    if (contextMethod != null) {
      containingClass = contextMethod.getContainingClass();
      className = containingClass == null ? "" : StringUtil.notNullize(containingClass.getQualifiedName());
    }
    else {
      String found = null;
      final Pattern pattern = Pattern.compile(".*definedInClass\\(\"([^\"]*)\"\\)+");
      for (InjectionPlace place : injection.getInjectionPlaces()) {
        final Matcher matcher = pattern.matcher(place.getText());
        if (matcher.matches()) {
          found = matcher.group(1);
        }
      }
      if (found == null) {
        // hack to guess at least the class name
        final Matcher matcher = ourPresentationPattern.matcher(injection.getDisplayName());
        if (matcher.matches()) {
          final String pkg = matcher.group(2);
          found = pkg.substring(1, pkg.length()-1)+"." + matcher.group(1);
        }
      }
      containingClass = found != null && project.isInitialized()? JavaPsiFacade.getInstance(project).findClass(found, GlobalSearchScope.allScope(project)) : null;
      className = StringUtil.notNullize(containingClass == null ? found : containingClass.getQualifiedName());
    }
    final MethodParameterInjection result = new MethodParameterInjection();
    result.copyFrom(injection);
    result.getInjectionPlaces().clear();
    result.setClassName(className);
    final ArrayList<MethodParameterInjection.MethodInfo> infos = new ArrayList<MethodParameterInjection.MethodInfo>();
    if (containingClass != null) {
      for (PsiMethod method : containingClass.getMethods()) {
        final PsiModifierList modifiers = method.getModifierList();
        if (modifiers.hasModifierProperty(PsiModifier.PRIVATE) || modifiers.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) continue;
        boolean add = false;
        final MethodParameterInjection.MethodInfo methodInfo = createMethodInfo(method);
        if (isInjectable(method.getReturnType(), method.getProject())) {
          final int parameterIndex = -1;
          final InjectionPlace place = injection.findPlaceByText(getPatternStringForJavaPlace(method, parameterIndex));
          methodInfo.setReturnFlag(place != null && place.isEnabled() || includeAllPlaces);
          add = true;
        }
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        for (int i = 0; i < parameters.length; i++) {
          final PsiParameter p = parameters[i];
          if (isInjectable(p.getType(), p.getProject())) {
            final InjectionPlace place = injection.findPlaceByText(getPatternStringForJavaPlace(method, i));
            methodInfo.getParamFlags()[i] = place != null && place.isEnabled() || includeAllPlaces;
            add = true;
          }
        }
        if (add) {
          infos.add(methodInfo);
        }
      }
    }
    else {
      // todo tbd
      //for (InjectionPlace place : injection.getInjectionPlaces()) {
      //  final Matcher matcher = pattern.matcher(place.getText());
      //  if (matcher.matches()) {
      //
      //  }
      //}
    }
    result.setMethodInfos(infos);
    return result;
  }

  public static String getPatternStringForJavaPlace(final PsiMethod method, final int parameterIndex) {
    final PsiClass psiClass = method.getContainingClass();
    final String className = psiClass == null ? "" : StringUtil.notNullize(psiClass.getQualifiedName());
    final String signature = createMethodInfo(method).getMethodSignature();
    return MethodParameterInjection.getPatternStringForJavaPlace(method.getName(), getParameterTypesString(signature), parameterIndex, className);
  }

  @Override
  public AnAction[] createAddActions(final Project project, final Consumer<BaseInjection> consumer) {
    return new AnAction[] {
      new AnAction("Java Parameter", null, Icons.PARAMETER_ICON) {
        @Override
        public void actionPerformed(final AnActionEvent e) {
          final BaseInjection injection = showInjectionUI(project, new MethodParameterInjection());
          if (injection != null) consumer.consume(injection);
        }
      }
    };
  }

  @Override
  public AnAction createEditAction(final Project project, final Factory<BaseInjection> producer) {
    return new AnAction() {
      @Override
      public void actionPerformed(final AnActionEvent e) {
        final BaseInjection originalInjection = producer.create();
        final MethodParameterInjection injection = createFrom(project, originalInjection, null, false);
        if (injection != null) {
          final boolean mergeEnabled = !project.isInitialized() || 
            JavaPsiFacade.getInstance(project).findClass(injection.getClassName(), GlobalSearchScope.allScope(project)) == null;
          final BaseInjection newInjection = showInjectionUI(project, injection);
          if (newInjection != null) {
            newInjection.mergeOriginalPlacesFrom(originalInjection, mergeEnabled);
            originalInjection.copyFrom(newInjection);
            originalInjection.initializePlaces(true);
          }
        }
        else {
          createDefaultEditAction(project, producer).actionPerformed(null);
        }
      }
    };
  }

  private final static Pattern ourPresentationPattern = Pattern.compile("(.+)(\\(\\S+(?:\\.\\S+)+\\))");
  @Override
  public void setupPresentation(final BaseInjection injection, final SimpleColoredText presentation, final boolean isSelected) {
    final Matcher matcher = ourPresentationPattern.matcher(injection.getDisplayName());
    if (matcher.matches()) {
      presentation.append(matcher.group(1), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      presentation.append(matcher.group(2), isSelected ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
    else {
      super.setupPresentation(injection, presentation, isSelected);
    }
  }
}
