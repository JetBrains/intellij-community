// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.references;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.execution.Executor;
import com.intellij.ide.actions.QualifiedNameProviderUtil;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.references.PomService;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ProjectIconsAccessor;
import com.intellij.util.CommonProcessors;
import com.intellij.util.PairProcessor;
import com.intellij.util.SmartList;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomTarget;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.ActionOrGroup;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.OverrideText;
import org.jetbrains.idea.devkit.dom.impl.ActionOrGroupResolveConverter;
import org.jetbrains.idea.devkit.dom.index.IdeaPluginRegistrationIndex;
import org.jetbrains.idea.devkit.util.DevKitDomUtil;
import org.jetbrains.idea.devkit.util.PluginRelatedLocatorsUtils;
import org.jetbrains.uast.UExpression;

import javax.swing.*;
import java.util.List;
import java.util.Objects;

import static org.jetbrains.idea.devkit.references.ActionOrGroupIdResolveUtil.ACTIVATE_TOOLWINDOW_ACTION_PREFIX;
import static org.jetbrains.idea.devkit.references.ActionOrGroupIdResolveUtil.ACTIVATE_TOOLWINDOW_ACTION_SUFFIX;

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

    final GlobalSearchScope domSearchScope = PluginRelatedLocatorsUtils.getCandidatesScope(project);

    CommonProcessors.CollectUniquesProcessor<ActionOrGroup> processor = new CommonProcessors.CollectUniquesProcessor<>();
    collectDomResults(myId, domSearchScope, processor);

    if (myIsAction != ThreeState.NO && processor.getResults().isEmpty()) {
      Ref<PsiElement> executor = Ref.create();
      PairProcessor<String, PsiClass> pairProcessor = (id, psiClass) -> {
        if (StringUtil.equals(id, myId)) {
          executor.set(psiClass);
          return false;
        }
        return true;
      };
      ActionOrGroupIdResolveUtil.processExecutors(project, pairProcessor);
      if (!executor.isNull()) {
        return PsiElementResolveResult.createResults(executor.get());
      }

      if (myId.startsWith(ACTIVATE_TOOLWINDOW_ACTION_PREFIX) && myId.endsWith(ACTIVATE_TOOLWINDOW_ACTION_SUFFIX)) {
        String toolwindowId = StringUtil.substringBeforeLast(
          Objects.requireNonNull(StringUtil.substringAfter(myId, ACTIVATE_TOOLWINDOW_ACTION_PREFIX)),
          ACTIVATE_TOOLWINDOW_ACTION_SUFFIX);

        // ToolWindow EPs - process all as we need to remove spaces from ID
        Ref<Extension> toolwindowExtension = Ref.create();
        ActionOrGroupIdResolveUtil.processActivateToolWindowActions(project, extension -> {
          String idValue = ActionOrGroupIdResolveUtil.getToolWindowIdValue(extension);
          if (Comparing.strEqual(toolwindowId, idValue)) {
            toolwindowExtension.set(extension);
            return false;
          }
          return true;
        });
        if (!toolwindowExtension.isNull()) {
          return PsiElementResolveResult.createResults(getDomTargetPsi(toolwindowExtension.get()));
        }

        // known (programmatic) ToolWindow IDs
        Ref<PsiField> knownToolWindowIdField = Ref.create();
        ActionOrGroupIdResolveUtil.processToolWindowId(project, (s, field) -> {
          if (Comparing.strEqual(s, toolwindowId)) {
            knownToolWindowIdField.set(field);
            return false;
          }
          return true;
        });
        if (!knownToolWindowIdField.isNull()) {
          return PsiElementResolveResult.createResults(knownToolWindowIdField.get());
        }
        return ResolveResult.EMPTY_ARRAY;
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
      ContainerUtil.mapNotNull(processor.getResults(), actionOrGroup -> getDomTargetPsi(actionOrGroup));
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

    Project project = getElement().getProject();
    List<ActionOrGroup> domVariants = converter.getVariants(project, ModuleUtilCore.findModuleForPsiElement(getElement()));
    List<LookupElement> domLookupElements = ContainerUtil.map(domVariants, actionOrGroup -> converter.createLookupElement(actionOrGroup));
    if (myIsAction == ThreeState.NO) {
      return domLookupElements.toArray();
    }

    List<LookupElement> executorLookupElements = new SmartList<>();
    ActionOrGroupIdResolveUtil.processExecutors(project, (id, psiClass) -> {
      LookupElementBuilder builder = LookupElementBuilder.create(psiClass, id)
        .bold()
        .withIcon(computeExecutorIcon(id, psiClass))
        .withTailText(computeExecutorTailText(id), true)
        .withTypeText(psiClass.getQualifiedName(), true);
      executorLookupElements.add(builder);
      return true;
    });


    List<LookupElement> activateToolWindowElements = new SmartList<>();
    ActionOrGroupIdResolveUtil.processActivateToolWindowActions(project, extension -> {
      GenericAttributeValue<?> factoryClass = DevKitDomUtil.getAttribute(extension, "factoryClass");

      LookupElementBuilder builder =
        LookupElementBuilder.create(getDomTargetPsi(extension),
                                    ACTIVATE_TOOLWINDOW_ACTION_PREFIX +
                                    ActionOrGroupIdResolveUtil.getToolWindowIdValue(extension) +
                                    ACTIVATE_TOOLWINDOW_ACTION_SUFFIX)
          .bold()
          .withTypeText(factoryClass != null ? factoryClass.getStringValue() : "", true);
      activateToolWindowElements.add(builder);
      return true;
    });

    ActionOrGroupIdResolveUtil.processToolWindowId(project, (value, field) -> {
      LookupElementBuilder builder =
        LookupElementBuilder.create(field,
                                    ACTIVATE_TOOLWINDOW_ACTION_PREFIX + value + ACTIVATE_TOOLWINDOW_ACTION_SUFFIX)
          .bold()
          .withStrikeoutness(field.isDeprecated())
          .withTypeText(QualifiedNameProviderUtil.getQualifiedName(field), true);
      activateToolWindowElements.add(builder);
      return true;
    });

    return ContainerUtil.concat(domLookupElements, executorLookupElements, activateToolWindowElements).toArray();
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

  private static PsiElement getDomTargetPsi(DomElement domElement) {
    DomTarget target = DomTarget.getTarget(domElement);
    assert target != null;
    return PomService.convertToPsi(target);
  }

  private static Icon computeExecutorIcon(@NotNull String id, PsiClass executor) {
    final ProjectIconsAccessor iconsAccessor = ProjectIconsAccessor.getInstance(executor.getProject());
    final UExpression icon = ActionOrGroupIdResolveUtil.getReturnExpression(executor, "getIcon");
    if (icon == null) {
      Executor executorById = findExecutor(id);
      return executorById != null ? executorById.getIcon() : null;
    }
    final VirtualFile iconFile = iconsAccessor.resolveIconFile(icon);
    return iconFile == null ? null : iconsAccessor.getIcon(iconFile);
  }

  private static String computeExecutorTailText(@NotNull String id) {
    Executor executorById = findExecutor(id);
    return executorById != null ? " \"" + executorById.getActionName() + "\"" : "";
  }

  private static @Nullable Executor findExecutor(String id) {
    for (Executor executor : Executor.EXECUTOR_EXTENSION_NAME.getExtensionList()) {
      if (executor.getId().equals(id) ||
          executor.getContextActionId().equals(id)) {
        return executor;
      }
    }
    return null;
  }
}
