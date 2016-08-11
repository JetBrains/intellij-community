/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.patterns.compiler.PatternCompiler;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.SimpleColoredText;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.intellij.plugins.intelliLang.AdvancedSettingsUI;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.AbstractLanguageInjectionSupport;
import org.intellij.plugins.intelliLang.inject.EditInjectionSettingsAction;
import org.intellij.plugins.intelliLang.inject.InjectLanguageAction;
import org.intellij.plugins.intelliLang.inject.InjectorUtils;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.intellij.plugins.intelliLang.inject.config.InjectionPlace;
import org.intellij.plugins.intelliLang.inject.config.MethodParameterInjection;
import org.intellij.plugins.intelliLang.inject.config.ui.AbstractInjectionPanel;
import org.intellij.plugins.intelliLang.inject.config.ui.MethodParameterPanel;
import org.intellij.plugins.intelliLang.util.ContextComputationProcessor;
import org.intellij.plugins.intelliLang.util.PsiUtilEx;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
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

  @NonNls public static final String JAVA_SUPPORT_ID = "java";

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

  public Configurable[] createSettings(final Project project, final Configuration configuration) {
    return new Configurable[]{new AdvancedSettingsUI(project, configuration)};
  }

  @Override
  public boolean isApplicableTo(PsiLanguageInjectionHost host) {
    return host instanceof PsiLiteralExpression;
  }

  public boolean addInjectionInPlace(final Language language, final PsiLanguageInjectionHost psiElement) {
    if (!isMine(psiElement)) return false;
    return doInjectInJava(psiElement.getProject(), psiElement, psiElement, language.getID());
  }

  public boolean removeInjectionInPlace(final PsiLanguageInjectionHost psiElement) {
    if (!isMine(psiElement)) return false;
    final HashMap<BaseInjection, Pair<PsiMethod, Integer>> injectionsMap = new HashMap<>();
    final ArrayList<PsiElement> annotations = new ArrayList<>();
    final PsiLiteralExpression host = (PsiLiteralExpression)psiElement;
    final Project project = host.getProject();
    final Configuration configuration = Configuration.getProjectInstance(project);
    collectInjections(host, configuration, this, injectionsMap, annotations);

    if (injectionsMap.isEmpty() && annotations.isEmpty()) return false;
    final ArrayList<BaseInjection> originalInjections = new ArrayList<>(injectionsMap.keySet());
    final List<BaseInjection> newInjections = ContainerUtil.mapNotNull(originalInjections,
                                                                       (NullableFunction<BaseInjection, BaseInjection>)injection -> {
                                                                         final Pair<PsiMethod, Integer> pair = injectionsMap.get(injection);
                                                                         final String placeText = getPatternStringForJavaPlace(pair.first, pair.second);
                                                                         final BaseInjection newInjection = injection.copy();
                                                                         newInjection.setPlaceEnabled(placeText, false);
                                                                         return InjectorUtils.canBeRemoved(newInjection)? null : newInjection;
                                                                       });
    configuration.replaceInjectionsWithUndo(project, newInjections, originalInjections, annotations);
    return true;
  }

  public boolean editInjectionInPlace(final PsiLanguageInjectionHost psiElement) {
    if (!isMine(psiElement)) return false;
    final HashMap<BaseInjection, Pair<PsiMethod, Integer>> injectionsMap = new HashMap<>();
    final ArrayList<PsiElement> annotations = new ArrayList<>();
    final PsiLiteralExpression host = (PsiLiteralExpression)psiElement;
    final Project project = host.getProject();
    final Configuration configuration = Configuration.getProjectInstance(project);
    collectInjections(host, configuration, this, injectionsMap, annotations);
    if (injectionsMap.isEmpty() || !annotations.isEmpty()) return false;

    final BaseInjection originalInjection = injectionsMap.keySet().iterator().next();
    final MethodParameterInjection methodParameterInjection = createFrom(psiElement.getProject(), originalInjection,
                                                                         injectionsMap.get(originalInjection).first, false);
    final MethodParameterInjection copy = methodParameterInjection.copy();
    final BaseInjection newInjection = showInjectionUI(project, methodParameterInjection);
    if (newInjection != null) {
      newInjection.mergeOriginalPlacesFrom(copy, false);
      newInjection.mergeOriginalPlacesFrom(originalInjection, true);
      configuration.replaceInjectionsWithUndo(
        project, Collections.singletonList(newInjection), Collections.singletonList(originalInjection), Collections.<PsiAnnotation>emptyList());
    }
    return true;

  }

  private static BaseInjection showInjectionUI(final Project project, final MethodParameterInjection methodParameterInjection) {
    final AbstractInjectionPanel panel = new MethodParameterPanel(methodParameterInjection, project);
    panel.reset();
    final DialogBuilder builder = new DialogBuilder(project);
    builder.setHelpId("reference.settings.injection.language.injection.settings.java.parameter");
    builder.addOkAction();
    builder.addCancelAction();
    builder.setCenterPanel(panel.getComponent());
    builder.setTitle(EditInjectionSettingsAction.EDIT_INJECTION_TITLE);
    builder.setOkOperation(() -> {
      panel.apply();
      builder.getDialogWrapper().close(DialogWrapper.OK_EXIT_CODE);
    });
    if (builder.show() == DialogWrapper.OK_EXIT_CODE) {
      return new BaseInjection(methodParameterInjection.getSupportId()).copyFrom(methodParameterInjection);
    }
    return null;
  }

  public BaseInjection createInjection(final Element element) {
    return new BaseInjection(JAVA_SUPPORT_ID);
  }

  private static boolean doInjectInJava(final Project project,
                                        @NotNull final PsiElement psiElement,
                                        PsiLanguageInjectionHost host,
                                        final String languageId) {
    final PsiElement target = ContextComputationProcessor.getTopLevelInjectionTarget(psiElement);
    final PsiElement parent = target.getParent();
    if (parent instanceof PsiReturnStatement ||
        parent instanceof PsiMethod ||
        parent instanceof PsiNameValuePair) {
      return doInjectInJavaMethod(project, findPsiMethod(parent), -1, host, languageId);
    }
    else if (parent instanceof PsiExpressionList && parent.getParent() instanceof PsiCallExpression) {
      return doInjectInJavaMethod(project, findPsiMethod(parent), findParameterIndex(target, (PsiExpressionList)parent), host, languageId);
    }
    else if (parent instanceof PsiAssignmentExpression) {
      final PsiExpression psiExpression = ((PsiAssignmentExpression)parent).getLExpression();
      if (psiExpression instanceof PsiReferenceExpression) {
        final PsiElement element = ((PsiReferenceExpression)psiExpression).resolve();
        if (element != null) {
          return doInjectInJava(project, element, host, languageId);
        }
      }
    }
    else if (parent instanceof PsiVariable) {
      if (doAddLanguageAnnotation(project, (PsiModifierListOwner)parent, host, languageId)) return true;
    }
    else if (target instanceof PsiVariable) {
      if (doAddLanguageAnnotation(project, (PsiModifierListOwner)target, host, languageId)) return true;
    }
    return false;
  }

  public static boolean doAddLanguageAnnotation(final Project project,
                                                final PsiModifierListOwner modifierListOwner,
                                                @NotNull PsiLanguageInjectionHost host,
                                                final String languageId) {
    return doAddLanguageAnnotation(project, modifierListOwner, host, languageId, host1 -> {
      final Configuration.AdvancedConfiguration configuration = Configuration.getProjectInstance(project).getAdvancedConfiguration();
      boolean allowed = configuration.isSourceModificationAllowed();
      configuration.setSourceModificationAllowed(true);
      try {
        return doInjectInJava(project, host1, host1, languageId);
      }
      finally {
        configuration.setSourceModificationAllowed(allowed);
      }
    });
  }

  private static boolean isAnnotationsJarInPath(Module module) {
    if (module == null) return false;
    return JavaPsiFacade.getInstance(module.getProject())
             .findClass(AnnotationUtil.LANGUAGE, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)) != null;
  }

  public static boolean doAddLanguageAnnotation(Project project,
                                                @Nullable final PsiModifierListOwner modifierListOwner,
                                                @NotNull final PsiLanguageInjectionHost host,
                                                final String languageId,
                                                Processor<PsiLanguageInjectionHost> annotationFixer) {
    final boolean addAnnotation = isAnnotationsJarInPath(ModuleUtilCore.findModuleForPsiElement(modifierListOwner))
      && PsiUtil.isLanguageLevel5OrHigher(modifierListOwner)
      && modifierListOwner.getModifierList() != null;
    final PsiStatement statement = PsiTreeUtil.getParentOfType(host, PsiStatement.class);
    if (!addAnnotation && statement == null) return false;

    Configuration.AdvancedConfiguration configuration = Configuration.getProjectInstance(project).getAdvancedConfiguration();
    if (!configuration.isSourceModificationAllowed()) {
      host.putUserData(InjectLanguageAction.FIX_KEY, annotationFixer);
      return false;
    }

    new WriteCommandAction(modifierListOwner.getProject(), modifierListOwner.getContainingFile()) {
      protected void run(@NotNull Result result) throws Throwable {
        PsiElementFactory javaFacade = JavaPsiFacade.getElementFactory(getProject());
        if (addAnnotation) {
          JVMElementFactory factory = ObjectUtils.chooseNotNull(JVMElementFactories.getFactory(modifierListOwner.getLanguage(), getProject()), javaFacade);
          PsiAnnotation annotation = factory.createAnnotationFromText("@" + AnnotationUtil.LANGUAGE + "(\"" + languageId + "\")", modifierListOwner);
          PsiModifierList list = ObjectUtils.assertNotNull(modifierListOwner.getModifierList());
          final PsiAnnotation existingAnnotation = list.findAnnotation(AnnotationUtil.LANGUAGE);
          if (existingAnnotation != null) {
            existingAnnotation.replace(annotation);
          }
          else {
            list.addAfter(annotation, null);
          }
          JavaCodeStyleManager.getInstance(getProject()).shortenClassReferences(list);
        }
        else {
          statement.getParent().addBefore(javaFacade.createCommentFromText("//language=" + languageId, host), statement);
        }
      }
    }.execute();
    return true;
  }

  public static boolean doInjectInJavaMethod(@NotNull final Project project,
                                             @Nullable final PsiMethod psiMethod,
                                             final int parameterIndex,
                                             @NotNull PsiLanguageInjectionHost host, @NotNull final String languageId) {
    if (psiMethod == null) return false;
    if (parameterIndex < -1) return false;
    if (parameterIndex >= psiMethod.getParameterList().getParametersCount()) return false;
    final PsiModifierList methodModifiers = psiMethod.getModifierList();
    if (methodModifiers.hasModifierProperty(PsiModifier.PRIVATE) || methodModifiers.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
      return doAddLanguageAnnotation(project, parameterIndex >= 0? psiMethod.getParameterList().getParameters()[parameterIndex] : psiMethod,
                                     host, languageId);
    }
    final PsiClass containingClass = psiMethod.getContainingClass();
    assert containingClass != null;
    final PsiModifierList classModifiers = containingClass.getModifierList();
    if (classModifiers != null && (classModifiers.hasModifierProperty(PsiModifier.PRIVATE) || classModifiers.hasModifierProperty(PsiModifier.PACKAGE_LOCAL))) {
      return doAddLanguageAnnotation(project, parameterIndex >= 0? psiMethod.getParameterList().getParameters()[parameterIndex] : psiMethod,
                                     host, languageId);
    }

    final String className = containingClass.getQualifiedName();
    assert className != null;
    final MethodParameterInjection injection = new MethodParameterInjection();
    injection.setInjectedLanguageId(languageId);
    injection.setClassName(className);
    final MethodInfo info = createMethodInfo(psiMethod);
    if (parameterIndex < 0) {
      info.setReturnFlag(true);
    }
    else {
      info.getParamFlags()[parameterIndex] = true;
    }
    injection.setMethodInfos(Collections.singletonList(info));
    injection.generatePlaces();
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
            final String name = ((PsiNameValuePair)parent).getName();
            PsiMethod[] methods = ((PsiClass)resolved).findMethodsByName(name == null? PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME : name, false);
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
    final LinkedList<PsiMethod> methods = new LinkedList<>();
    methods.add(first);
    while (!methods.isEmpty()) {
      final PsiMethod method = methods.removeFirst();
      final PsiClass psiClass = method.getContainingClass();
      if (psiClass != null && psiClass.getQualifiedName() != null) {
        return method;
      }
      else {
        ContainerUtil.addAll(methods, method.findSuperMethods());
      }
    }
    return null;
  }

  private static void doEditInjection(final Project project, final MethodParameterInjection template, final PsiMethod contextMethod) {
    final Configuration configuration = InjectorUtils.getEditableInstance(project);
    final BaseInjection baseTemplate = new BaseInjection(template.getSupportId()).copyFrom(template);
    final MethodParameterInjection allMethodParameterInjection = createFrom(project, baseTemplate, contextMethod, true);
    // find existing injection for this class.
    final BaseInjection originalInjection = configuration.findExistingInjection(allMethodParameterInjection);
    final MethodParameterInjection methodParameterInjection;
    if (originalInjection == null) {
      methodParameterInjection = template;
    }
    else {
      final BaseInjection originalCopy = originalInjection.copy();
      final InjectionPlace currentPlace = template.getInjectionPlaces()[0];
      originalCopy.mergeOriginalPlacesFrom(template, true);
      originalCopy.setPlaceEnabled(currentPlace.getText(), true);
      methodParameterInjection = createFrom(project, originalCopy, contextMethod, false);
    }
    mergePlacesAndAddToConfiguration(project, configuration, methodParameterInjection, originalInjection);
  }

  private static void mergePlacesAndAddToConfiguration(@NotNull Project project,
                                                       @NotNull Configuration configuration,
                                                       @NotNull MethodParameterInjection injection,
                                                       @Nullable BaseInjection originalInjection) {
    BaseInjection newInjection = new BaseInjection(injection.getSupportId()).copyFrom(injection);
    if (originalInjection != null) {
      newInjection.mergeOriginalPlacesFrom(originalInjection, true);
    }
    configuration.replaceInjectionsWithUndo(
      project, Collections.singletonList(newInjection),
      ContainerUtil.createMaybeSingletonList(originalInjection),
      Collections.<PsiElement>emptyList());
  }

  private static void collectInjections(PsiLiteralExpression host,
                                        Configuration configuration,
                                        JavaLanguageInjectionSupport support,
                                        final HashMap<BaseInjection, Pair<PsiMethod, Integer>> injectionsMap,
                                        final ArrayList<PsiElement> annotations) {
    new ConcatenationInjector.InjectionProcessor(configuration, support, host) {

      @Override
      protected boolean processCommentInjectionInner(PsiElement comment, BaseInjection injection) {
        ContainerUtil.addAll(annotations, comment);
        return true;
      }

      @Override
      protected boolean processAnnotationInjectionInner(PsiModifierListOwner owner, PsiAnnotation[] annos) {
        ContainerUtil.addAll(annotations, annos);
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
    final PsiClass[] classes;
    final String className;
    if (contextMethod != null) {
      final PsiClass psiClass = contextMethod.getContainingClass();
      className = psiClass == null ? "" : StringUtil.notNullize(psiClass.getQualifiedName());
      classes = psiClass == null? PsiClass.EMPTY_ARRAY : new PsiClass[] {psiClass};
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
      classes = found != null && project.isInitialized()? JavaPsiFacade.getInstance(project).findClasses(found, GlobalSearchScope
        .allScope(project)) : PsiClass.EMPTY_ARRAY;
      className = StringUtil.notNullize(classes.length == 0 ? found : classes[0].getQualifiedName());
    }
    final MethodParameterInjection result = new MethodParameterInjection();
    result.copyFrom(injection);
    result.setInjectionPlaces(InjectionPlace.EMPTY_ARRAY);
    result.setClassName(className);
    final ArrayList<MethodInfo> infos = new ArrayList<>();
    if (classes.length > 0) {
      final THashSet<String> visitedSignatures = new THashSet<>();
      final PatternCompiler<PsiElement> compiler = injection.getCompiler();
      for (PsiClass psiClass : classes) {
        for (PsiMethod method : psiClass.getMethods()) {
          final PsiModifierList modifiers = method.getModifierList();
          if (modifiers.hasModifierProperty(PsiModifier.PRIVATE) || modifiers.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) continue;
          boolean add = false;
          final MethodInfo methodInfo = createMethodInfo(method);
          if (!visitedSignatures.add(methodInfo.getMethodSignature())) continue;
          if (isInjectable(method.getReturnType(), method.getProject())) {
            final int parameterIndex = -1;
            int index = ArrayUtilRt.find(injection.getInjectionPlaces(), new InjectionPlace(
              compiler.compileElementPattern(getPatternStringForJavaPlace(method, parameterIndex)), true));
            final InjectionPlace place = index > -1 ? injection.getInjectionPlaces()[index] : null;
            methodInfo.setReturnFlag(place != null && place.isEnabled() || includeAllPlaces);
            add = true;
          }
          final PsiParameter[] parameters = method.getParameterList().getParameters();
          for (int i = 0; i < parameters.length; i++) {
            final PsiParameter p = parameters[i];
            if (isInjectable(p.getType(), p.getProject())) {
              int index = ArrayUtilRt.find(injection.getInjectionPlaces(),
                                           new InjectionPlace(compiler.compileElementPattern(getPatternStringForJavaPlace(method, i)),
                                                              true));
              final InjectionPlace place = index > -1 ? injection.getInjectionPlaces()[index] : null;
              methodInfo.getParamFlags()[i] = place != null && place.isEnabled() || includeAllPlaces;
              add = true;
            }
          }
          if (add) {
            infos.add(methodInfo);
          }
        }
      }
    }
//    else {
      // todo tbd
      //for (InjectionPlace place : injection.getInjectionPlaces()) {
      //  final Matcher matcher = pattern.matcher(place.getText());
      //  if (matcher.matches()) {
      //
      //  }
      //}
//    }
    result.setMethodInfos(infos);
    result.generatePlaces();
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
      new AnAction("Java Parameter", null, PlatformIcons.PARAMETER_ICON) {
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

  @Override
  public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {

  }

  @Override
  public String getHelpId() {
    return "reference.settings.injection.language.injection.settings.java.parameter";
  }
}
