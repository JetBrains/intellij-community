// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.references;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.references.PomService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Plow;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.xml.DomTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.Action;
import org.jetbrains.idea.devkit.dom.ActionOrGroup;
import org.jetbrains.idea.devkit.dom.Group;
import org.jetbrains.idea.devkit.dom.OverrideText;
import org.jetbrains.idea.devkit.dom.index.IdeaPluginRegistrationIndex;

import java.util.List;
import java.util.Objects;

final class ActionOrGroupIdReference extends PsiPolyVariantReferenceBase<PsiElement> {

  private final String myId;
  private final ThreeState myIsAction;

  ActionOrGroupIdReference(@NotNull PsiElement element, TextRange range, String id, ThreeState isAction) {
    super(element, range);
    myIsAction = isAction;
    myId = id;
  }

  @NotNull
  @Override
  public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    Project project = getElement().getProject();

    final GlobalSearchScope scope = ProjectScope.getContentScope(project);

    CommonProcessors.CollectUniquesProcessor<ActionOrGroup> processor = new CommonProcessors.CollectUniquesProcessor<>();
    collectResults(myId, scope, processor);

    // action|group.ActionId.<override-text@place>.text
    if (processor.getResults().isEmpty()) {
      String place = StringUtil.substringAfterLast(myId, ".");
      if (StringUtil.isEmpty(place)) return ResolveResult.EMPTY_ARRAY;

      String idWithoutPlaceSuffix = StringUtil.substringBeforeLast(myId, ".");

      collectResults(idWithoutPlaceSuffix, scope, processor);

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
    Project project = getElement().getProject();
    final GlobalSearchScope scope = ProjectScope.getContentScope(project);
    return Plow.<ActionOrGroup>of(processor ->
                                    IdeaPluginRegistrationIndex.processAllActionOrGroup(project, scope, processor))
      .filter(aog ->
                myIsAction == ThreeState.UNSURE
                || myIsAction == ThreeState.YES && aog instanceof Action
                || myIsAction == ThreeState.NO && aog instanceof Group
      )
      .<LookupElement>map(action -> LookupElementBuilder.create(action.getId()))
      .toArray(LookupElement.EMPTY_ARRAY);
  }

  private void collectResults(String id,
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
}
