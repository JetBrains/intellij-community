// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.references;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.execution.Executor;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.references.PomService;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.ProjectIconsAccessor;
import com.intellij.util.CommonProcessors;
import com.intellij.util.PairProcessor;
import com.intellij.util.SmartList;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.xml.DomTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.ActionOrGroup;
import org.jetbrains.idea.devkit.dom.OverrideText;
import org.jetbrains.idea.devkit.dom.impl.ActionOrGroupResolveConverter;
import org.jetbrains.idea.devkit.dom.index.IdeaPluginRegistrationIndex;
import org.jetbrains.idea.devkit.util.PsiUtil;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UastUtils;

import javax.swing.*;
import java.util.List;
import java.util.Objects;

final class ActionOrGroupIdReference extends PsiPolyVariantReferenceBase<PsiElement> implements PluginConfigReference {
  private final String myId;

  private final ThreeState myIsAction;

  ActionOrGroupIdReference(@NotNull PsiElement element, TextRange range, String id, ThreeState isAction) {
    super(element, range);
    myIsAction = isAction;
    myId = id;
  }

  @Override
  public @NotNull ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    Project project = getElement().getProject();

    final GlobalSearchScope domSearchScope = getProductionWithLibrariesScope(project);

    CommonProcessors.CollectUniquesProcessor<ActionOrGroup> processor = new CommonProcessors.CollectUniquesProcessor<>();
    collectDomResults(myId, domSearchScope, processor);

    if (myIsAction != ThreeState.NO && processor.getResults().isEmpty()) {
      List<PsiElement> executors = new SmartList<>();
      PairProcessor<String, PsiClass> pairProcessor = (id, psiClass) -> {
        if (StringUtil.equals(id, getValue())) {
          executors.add(psiClass);
        }
        return true;
      };
      processExecutors(pairProcessor);
      if (!executors.isEmpty()) {
        return PsiElementResolveResult.createResults(executors);
      }
    }

    // action|group.ActionId.<override-text@place>.text
    if (processor.getResults().isEmpty()) {
      String place = StringUtil.substringAfterLast(myId, ".");
      if (StringUtil.isEmpty(place)) return ResolveResult.EMPTY_ARRAY;

      String idWithoutPlaceSuffix = StringUtil.substringBeforeLast(myId, ".");

      collectDomResults(idWithoutPlaceSuffix, domSearchScope, processor);

      for (ActionOrGroup result : processor.getResults()) {
        for (OverrideText overrideText : result.getOverrideTexts()) {
          if (place.equals(overrideText.getPlace().getStringValue())) {
            final DomTarget overrideTarget = DomTarget.getTarget(overrideText, overrideText.getPlace());
            assert overrideTarget != null;
            return PsiElementResolveResult.createResults(PomService.convertToPsi(overrideTarget));
          }
        }
      }
      return ResolveResult.EMPTY_ARRAY;
    }

    final List<PsiElement> psiElements =
      JBIterable.from(processor.getResults())
        .map(actionOrGroup -> {
          final DomTarget target = DomTarget.getTarget(actionOrGroup);
          return target == null ? null : PomService.convertToPsi(project, target);
        }).filter(Objects::nonNull).toList();
    return PsiElementResolveResult.createResults(psiElements);
  }

  @Override
  public Object @NotNull [] getVariants() {
    ActionOrGroupResolveConverter converter;
    if (myIsAction == ThreeState.YES) {
      converter = new ActionOrGroupResolveConverter.OnlyActions();
    }
    else if (myIsAction == ThreeState.NO) {
      converter = new ActionOrGroupResolveConverter.OnlyGroups();
    }
    else {
      converter = new ActionOrGroupResolveConverter();
    }

    List<ActionOrGroup> domVariants =
      converter.getVariants(getElement().getProject(), ModuleUtilCore.findModuleForPsiElement(getElement()));
    List<LookupElement> domLookupElements = ContainerUtil.map(domVariants, actionOrGroup -> converter.createLookupElement(actionOrGroup));
    if (myIsAction == ThreeState.NO) {
      return domLookupElements.toArray();
    }

    List<LookupElement> executorLookupElements = new SmartList<>();
    processExecutors((id, psiClass) -> {
      if (id != null) {
        LookupElementBuilder builder = LookupElementBuilder.create(psiClass, id)
          .bold()
          .withIcon(computeIcon(psiClass))
          .withTypeText(psiClass.getQualifiedName(), true);
        executorLookupElements.add(builder);
      }
      return true;
    });
    return ContainerUtil.concat(domLookupElements, executorLookupElements).toArray();
  }

  @Override
  public @NotNull String getUnresolvedMessagePattern() {
    return myIsAction == ThreeState.NO ? DevKitBundle.message("plugin.xml.action.group.cannot.resolve", myId) :
           DevKitBundle.message("plugin.xml.action.cannot.resolve", myId);
  }

  private void collectDomResults(String id,
                                 GlobalSearchScope scope,
                                 CommonProcessors.CollectUniquesProcessor<ActionOrGroup> processor) {
    Project project = getElement().getProject();
    if (myIsAction != ThreeState.NO) {
      IdeaPluginRegistrationIndex.processAction(project, id, scope, processor);
    }
    if (myIsAction != ThreeState.YES) {
      IdeaPluginRegistrationIndex.processGroup(project, id, scope, processor);
    }
  }

  private void processExecutors(PairProcessor<String, PsiClass> processor) {
    Project project = getElement().getProject();
    // not necessarily in the element's resolve scope
    GlobalSearchScope scope = getProductionWithLibrariesScope(project);
    PsiClass executorClass = JavaPsiFacade.getInstance(project).findClass(Executor.class.getName(), scope);
    if (executorClass == null) return;

    for (PsiClass inheritor : ClassInheritorsSearch.search(executorClass, scope, true)) {
      String id = computeConstantReturnValue(inheritor, "getId");
      if (!processor.process(id, inheritor)) return;

      String contextActionId = computeConstantReturnValue(inheritor, "getContextActionId");
      if (!processor.process(contextActionId, inheritor)) return;
    }
  }

  private static Icon computeIcon(PsiClass executor) {
    final ProjectIconsAccessor iconsAccessor = ProjectIconsAccessor.getInstance(executor.getProject());
    final UExpression icon = getReturnExpression(executor, "getIcon");
    final VirtualFile iconFile = iconsAccessor.resolveIconFile(icon);
    return iconFile == null ? null : iconsAccessor.getIcon(iconFile);
  }

  private static @Nullable String computeConstantReturnValue(PsiClass psiClass,
                                                             String methodName) {
    final UExpression expression = getReturnExpression(psiClass, methodName);
    if (expression == null) return null;

    return UastUtils.evaluateString(expression);
  }

  private static @Nullable UExpression getReturnExpression(PsiClass psiClass, String methodName) {
    final PsiMethod[] methods = psiClass.findMethodsByName(methodName, false);
    if (methods.length != 1) {
      return null;
    }

    return PsiUtil.getReturnedExpression(methods[0]);
  }

  private static @NotNull GlobalSearchScope getProductionWithLibrariesScope(Project project) {
    return GlobalSearchScopesCore.projectProductionScope(project).union(ProjectScope.getLibrariesScope(project));
  }
}
