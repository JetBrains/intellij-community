/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.inject;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.DataManager;
import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.FileContentUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.config.MethodParameterInjection;
import org.intellij.plugins.intelliLang.inject.config.XmlAttributeInjection;
import org.intellij.plugins.intelliLang.inject.config.XmlTagInjection;
import org.intellij.plugins.intelliLang.inject.config.ui.configurables.MethodParameterInjectionConfigurable;
import org.intellij.plugins.intelliLang.inject.config.ui.configurables.XmlAttributeInjectionConfigurable;
import org.intellij.plugins.intelliLang.inject.config.ui.configurables.XmlTagInjectionConfigurable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class InjectLanguageAction implements IntentionAction {
  @NotNull
  public String getText() {
    return "Inject Language";
  }

  @NotNull
  public String getFamilyName() {
    return "Inject Language";
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final PsiLanguageInjectionHost host = findInjectionHost(editor, file);
    if (host == null) {
      return false;
    }
    else {
      final List<Pair<PsiElement, TextRange>> injectedPsi = host.getInjectedPsi();
      if (host instanceof XmlText) {
        final XmlTag tag = ((XmlText)host).getParentTag();
        if (tag == null || tag.getValue().getTextElements().length > 1 || tag.getSubTags().length > 0) {
          return false;
        }
      }
      if (injectedPsi == null || injectedPsi.size() == 0) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static PsiLanguageInjectionHost findInjectionHost(Editor editor, PsiFile file) {
    final int offset = editor.getCaretModel().getOffset();
    final PsiLanguageInjectionHost host =
        PsiTreeUtil.getParentOfType(file.findElementAt(offset), PsiLanguageInjectionHost.class, false, true);
    if (host == null) {
      return null;
    }
    if (host instanceof PsiLiteralExpression) {
      final PsiType type = ((PsiLiteralExpression)host).getType();
      if (type == null || !type.equalsToText("java.lang.String")) {
        return null;
      }
    }
    else if (host instanceof XmlAttributeValue) {
      final PsiElement p = host.getParent();
      if (p instanceof XmlAttribute) {
        final String s = ((XmlAttribute)p).getName();
        if (s.equals("xmlns") || s.startsWith("xmlns:")) {
          return null;
        }
      }
    }
    return host;
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    final PsiLanguageInjectionHost host = findInjectionHost(editor, file);
    assert host != null;
    doChooseLanguageToInject(new Processor<String>() {
      public boolean process(final String languageId) {
        if (!(host instanceof XmlAttributeValue && doInjectInAttributeValue(project, (XmlAttributeValue)host, languageId) ||
            host instanceof XmlText && doInjectInXmlText(project, (XmlText)host, languageId) ||
            host.getLanguage() == StdLanguages.JAVA && doInjectInJava(project, host, languageId))) {
          CustomLanguageInjector.getInstance(project).addTempInjection(host, InjectedLanguage.create(languageId));
        }
        FileContentUtil.reparseFiles(project, Collections.<VirtualFile>emptyList(), true);
        return false;
      }
    });
  }

  private static boolean doInjectInJava(final Project project, final PsiElement host, final String languageId) {
    PsiElement target = host;
    PsiElement parent = target.getParent();
    for (; parent != null; target = parent, parent = target.getParent()) {
      if (parent instanceof PsiBinaryExpression) continue;
      if (parent instanceof PsiParenthesizedExpression) continue;
      if (parent instanceof PsiConditionalExpression && ((PsiConditionalExpression)parent).getCondition() != target) continue;
      break;
    }
    if (parent instanceof PsiReturnStatement) {
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

  private static boolean doAddLanguageAnnotation(final Project project, final PsiModifierListOwner modifierListOwner,
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
      }
    }.execute();
    return true;
  }

  private static boolean doInjectInJavaMethod(final Project project, final PsiMethod psiMethod, final int parameterIndex,
                                              final String languageId) {
    if (psiMethod == null) return false;
    if (parameterIndex < -1) return false;
     final PsiModifierList modifiers = psiMethod.getModifierList();
    if (modifiers.hasModifierProperty(PsiModifier.PRIVATE) || modifiers.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
      return doAddLanguageAnnotation(project, parameterIndex >0? psiMethod.getParameterList().getParameters()[parameterIndex - 1] : psiMethod,
                                     languageId);
    }
    final PsiClass containingClass = psiMethod.getContainingClass();
    assert containingClass != null;
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

  private static int findParameterIndex(final PsiElement target, final PsiExpressionList parent) {
    final int idx = ContainerUtil.findByEquals(Arrays.asList(parent.getExpressions()), target);
    return idx < 0? -2 : idx;
  }

  @Nullable
  private static PsiMethod findPsiMethod(final PsiElement parent) {
    final PsiMethod first;
    if (parent.getParent() instanceof PsiMethodCallExpression) {
      first = ((PsiMethodCallExpression)parent.getParent()).resolveMethod();
    }
    else {
      first = PsiTreeUtil.getParentOfType(parent, PsiMethod.class, true);
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

  private static boolean doInjectInXmlText(final Project project, final XmlText host, final String languageId) {
    final XmlTag tag = host.getParentTag();
    if (tag != null) {
      final XmlTagInjection injection = new XmlTagInjection();
      injection.setInjectedLanguageId(languageId);
      injection.setTagName(tag.getName());
      injection.setTagNamespace(tag.getNamespace());
      doEditInjection(project, injection);
      return true;
    }
    return false;
  }

  private static boolean doInjectInAttributeValue(final Project project, final XmlAttributeValue host, final String languageId) {
    final XmlAttribute attribute = PsiTreeUtil.getParentOfType(host, XmlAttribute.class, true);
    final XmlTag tag = attribute == null? null : attribute.getParent();
    if (tag != null) {
      final XmlAttributeInjection injection = new XmlAttributeInjection();
      injection.setInjectedLanguageId(languageId);
      injection.setAttributeName(attribute.getName());
      injection.setAttributeNamespace(attribute.getNamespace());
      injection.setTagName(tag.getName());
      injection.setTagNamespace(tag.getNamespace());
      doEditInjection(project, injection);
      return true;
    }
    return false;
  }

  private static boolean doChooseLanguageToInject(final Processor<String> onChosen) {
    final String[] langIds = InjectedLanguage.getAvailableLanguageIDs();
    Arrays.sort(langIds);

    final Map<String, List<String>> map = new LinkedHashMap<String, List<String>>();
    buildLanguageTree(langIds, map);

    final BaseListPopupStep<String> step = new MyPopupStep(map, new ArrayList<String>(map.keySet()), onChosen);

    final ListPopup listPopup = JBPopupFactory.getInstance().createListPopup(step);
    listPopup.showInBestPositionFor(DataManager.getInstance().getDataContext());
    return true;
  }

  private static void doEditInjection(final Project project, final XmlAttributeInjection injection) {
    final Configuration configuration = Configuration.getInstance();
    final XmlAttributeInjection existing = configuration.findExistingInjection(injection);
    if (doEditConfigurable(project, new XmlAttributeInjectionConfigurable(existing == null ? injection : existing, null, project))) {
      if (existing == null) {
        configuration.getAttributeInjections().add(injection);
        configuration.configurationModified();
      }
    }
  }

  private static void doEditInjection(final Project project, final XmlTagInjection injection) {
    final Configuration configuration = Configuration.getInstance();
    final XmlTagInjection existing = configuration.findExistingInjection(injection);
    if (doEditConfigurable(project, new XmlTagInjectionConfigurable(existing == null? injection : existing, null, project))) {
      if (existing == null) {
        configuration.getTagInjections().add(injection);
        configuration.configurationModified();
      }
    }
  }

  private static void doEditInjection(final Project project, final MethodParameterInjection injection) {
    final Configuration configuration = Configuration.getInstance();
    final MethodParameterInjection existing = configuration.findExistingInjection(injection);
    if (existing != null) {
      // merge method infos
      boolean found = false;
      final MethodParameterInjection.MethodInfo curInfo = injection.getMethodInfos().iterator().next();
      for (MethodParameterInjection.MethodInfo info : existing.getMethodInfos()) {
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
        final ArrayList<MethodParameterInjection.MethodInfo> methodInfos = new ArrayList<MethodParameterInjection.MethodInfo>(existing.getMethodInfos());
        methodInfos.add(curInfo);
        existing.setMethodInfos(methodInfos);
      }
    }
    if (doEditConfigurable(project, new MethodParameterInjectionConfigurable(existing == null? injection : existing, null, project))) {
      if (existing == null) {
        configuration.getParameterInjections().add(injection);
      }
      configuration.configurationModified();
    }
  }


  private static boolean doEditConfigurable(final Project project, final Configurable configurable) {
    return true; //ShowSettingsUtil.getInstance().editConfigurable(project, configurable);
  }

  private static void buildLanguageTree(String[] langIds, Map<String, List<String>> map) {
    for (final String id : langIds) {
      if (!map.containsKey(id)) {
        map.put(id, new ArrayList<String>());
      }
    }
  }

  public boolean startInWriteAction() {
    return false;
  }

  private static class MyPopupStep extends BaseListPopupStep<String> {
    private final Map<String, List<String>> myMap;
    private final Processor<String> myFinalStepProcessor;

    public MyPopupStep(final Map<String, List<String>> map, final List<String> values, final Processor<String> finalStepProcessor) {
      super("Choose Language", values);
      myMap = map;
      myFinalStepProcessor = finalStepProcessor;
    }

    @Override
    public PopupStep onChosen(final String selectedValue, boolean finalChoice) {
      if (finalChoice) {
        myFinalStepProcessor.process(selectedValue);
        return FINAL_CHOICE;
      }
      return new MyPopupStep(myMap, myMap.get(selectedValue), myFinalStepProcessor);
    }

    @Override
    public boolean hasSubstep(String selectedValue) {
      return myMap.containsKey(selectedValue) && !myMap.get(selectedValue).isEmpty();
    }

    @Override
    public Icon getIconFor(String aValue) {
      final Language language = InjectedLanguage.findLanguageById(aValue);
      assert language != null;
      final FileType ft = language.getAssociatedFileType();
      return ft != null ? ft.getIcon() : new EmptyIcon(16);
    }

    @NotNull
    @Override
    public String getTextFor(String value) {
      final Language language = InjectedLanguage.findLanguageById(value);
      assert language != null;
      final FileType ft = language.getAssociatedFileType();
      return value + (ft != null ? " ("+ft.getDescription()+")" : "");
    }
  }
}
