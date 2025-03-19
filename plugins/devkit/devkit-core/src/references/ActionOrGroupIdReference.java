// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.references;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupElementRenderer;
import com.intellij.execution.Executor;
import com.intellij.ide.actions.QualifiedNameProviderUtil;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.references.PomService;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ProjectIconsAccessor;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.*;
import org.jetbrains.idea.devkit.dom.Action;
import org.jetbrains.idea.devkit.dom.index.IdeaPluginRegistrationIndex;
import org.jetbrains.idea.devkit.util.DescriptorI18nUtil;
import org.jetbrains.idea.devkit.util.DevKitDomUtil;
import org.jetbrains.idea.devkit.util.PluginRelatedLocatorsUtils;
import org.jetbrains.uast.UExpression;

import javax.swing.*;
import java.util.List;
import java.util.Objects;

import static com.intellij.openapi.util.NullableLazyValue.lazyNullable;
import static org.jetbrains.idea.devkit.references.ActionOrGroupIdResolveUtil.ACTIVATE_TOOLWINDOW_ACTION_PREFIX;
import static org.jetbrains.idea.devkit.references.ActionOrGroupIdResolveUtil.ACTIVATE_TOOLWINDOW_ACTION_SUFFIX;

public final class ActionOrGroupIdReference extends PsiPolyVariantReferenceBase<PsiElement> implements PluginConfigReference {
  @Nullable
  private final String myId;

  private final ThreeState myIsAction;

  public ActionOrGroupIdReference(@NotNull PsiElement element, TextRange range, @Nullable String id, ThreeState isAction) {
    this(element, range, id, isAction, false);
  }

  public ActionOrGroupIdReference(@NotNull PsiElement element, TextRange range, @Nullable String id, ThreeState isAction, boolean soft) {
    super(element, range, soft);
    myIsAction = isAction;
    myId = id;
  }

  @Override
  public @NotNull ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    if (StringUtil.isEmpty(myId)) return ResolveResult.EMPTY_ARRAY;

    Project project = getElement().getProject();

    // for references from code with missing dependencies on XML -> must process all
    GlobalSearchScope domSearchScope = PluginRelatedLocatorsUtils.getCandidatesScope(project);
    CommonProcessors.CollectUniquesProcessor<ActionOrGroup> processor = new CommonProcessors.CollectUniquesProcessor<>();
    resolveDomActionOrGroup(project, myId, domSearchScope, processor);

    if (myIsAction != ThreeState.NO && processor.getResults().isEmpty()) {
      PsiElement executor = resolveExecutor(project);
      if (executor != null) {
        return PsiElementResolveResult.createResults(executor);
      }

      if (myId.startsWith(ACTIVATE_TOOLWINDOW_ACTION_PREFIX) && myId.endsWith(ACTIVATE_TOOLWINDOW_ACTION_SUFFIX)) {
        return resolveToolWindow(project);
      }
    }

    // action|group.ActionId.<override-text@place>.text
    if (processor.getResults().isEmpty()) {
      String place = StringUtil.substringAfterLast(myId, ".");
      if (StringUtil.isEmpty(place)) return ResolveResult.EMPTY_ARRAY;

      String idWithoutPlaceSuffix = StringUtil.substringBeforeLast(myId, ".");
      resolveDomActionOrGroup(project, idWithoutPlaceSuffix, domSearchScope, processor);

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
      ContainerUtil.mapNotNull(processor.getResults(), actionOrGroup -> getActionOrGroupDomTargetPsi(actionOrGroup));
    return PsiElementResolveResult.createResults(psiElements);
  }

  private void resolveDomActionOrGroup(Project project,
                                       String id,
                                       GlobalSearchScope scope,
                                       CommonProcessors.CollectUniquesProcessor<ActionOrGroup> processor) {
    if (myIsAction != ThreeState.NO) {
      if (!IdeaPluginRegistrationIndex.processAction(project, id, scope, processor)) return;
    }
    if (myIsAction != ThreeState.YES) {
      IdeaPluginRegistrationIndex.processGroup(project, id, scope, processor);
    }
  }

  private @Nullable PsiElement resolveExecutor(Project project) {
    Ref<PsiElement> executor = Ref.create();
    PairProcessor<String, PsiClass> pairProcessor = (id, psiClass) -> {
      if (StringUtil.equals(id, myId)) {
        executor.set(psiClass);
        return false;
      }
      return true;
    };
    ActionOrGroupIdResolveUtil.processExecutors(project, pairProcessor);
    return executor.get();
  }

  private ResolveResult @NotNull [] resolveToolWindow(Project project) {
    String toolwindowId = StringUtil.substringBeforeLast(
      Objects.requireNonNull(StringUtil.substringAfter(Objects.requireNonNull(myId), ACTIVATE_TOOLWINDOW_ACTION_PREFIX)),
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

  @Override
  public Object @NotNull [] getVariants() {
    Project project = getElement().getProject();

    List<LookupElement> domLookupElements = new SmartList<>();
    final GlobalSearchScope domSearchScope = PluginRelatedLocatorsUtils.getCandidatesScope(project);
    IdeaPluginRegistrationIndex.processAllActionOrGroup(project, domSearchScope, actionOrGroup -> {
      if (isRelevantForVariant(actionOrGroup)) {
        PsiElement psiElement = getActionOrGroupDomTargetPsi(actionOrGroup);
        String name = StringUtil.notNullize(actionOrGroup.getEffectiveId(),
                                            DevKitBundle.message("plugin.xml.convert.action.or.group.invalid.name"));
        LookupElementBuilder builder = LookupElementBuilder.create(psiElement, name)
          .withRenderer(ActionOrGroupLookupRenderer.INSTANCE);
        builder.putUserData(ActionOrGroupLookupRenderer.ACTION_OR_GROUP_KEY, actionOrGroup);
        domLookupElements.add(builder);
      }
      return true;
    });
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
    String resultTypes = switch (myIsAction) {
      case YES -> DevKitBundle.message("plugin.xml.convert.action.or.group.type.action");
      case NO -> DevKitBundle.message("plugin.xml.convert.action.or.group.type.group");
      default -> DevKitBundle.message("plugin.xml.convert.action.or.group.type.action.or.group");
    };
    return DevKitBundle.message("plugin.xml.convert.action.or.group.cannot.resolve", resultTypes, myId);
  }

  private boolean isRelevantForVariant(ActionOrGroup group) {
    return switch (myIsAction) {
      case YES -> group instanceof Action;
      case NO -> group instanceof Group;
      case UNSURE -> true;
    };
  }

  private static PsiElement getActionOrGroupDomTargetPsi(ActionOrGroup actionOrGroup) {
    DomTarget target = DomTarget.getTarget(actionOrGroup, actionOrGroup.getEffectiveIdAttribute());
    assert target != null;
    return PomService.convertToPsi(target);
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


  private static class ActionOrGroupLookupRenderer extends LookupElementRenderer<LookupElement> {

    private static final ActionOrGroupLookupRenderer INSTANCE = new ActionOrGroupLookupRenderer();

    private static final Key<ActionOrGroup> ACTION_OR_GROUP_KEY = Key.create("ACTION_OR_GROUP_KEY");

    @Override
    public void renderElement(LookupElement element, LookupElementPresentation presentation) {
      presentation.setItemText(element.getLookupString());

      ActionOrGroup actionOrGroup = element.getUserData(ACTION_OR_GROUP_KEY);
      assert actionOrGroup != null;

      presentation.setItemTextUnderlined(actionOrGroup.getPopup().getValue() == Boolean.TRUE);
      presentation.setIcon(ElementPresentationManager.getIcon(actionOrGroup));

      NullableLazyValue<PropertiesFile> propertiesFile =
        lazyNullable(() -> DescriptorI18nUtil.findBundlePropertiesFile(actionOrGroup));

      final String text = getLocalizedText(actionOrGroup, ActionOrGroup.TextType.TEXT, propertiesFile);
      if (StringUtil.isNotEmpty(text)) {
        String withoutMnemonic = StringUtil.replace(text, "_", "");
        presentation.setTailText(" \"" + withoutMnemonic + "\"", true);
      }

      final String description = getLocalizedText(actionOrGroup, ActionOrGroup.TextType.DESCRIPTION, propertiesFile);
      if (StringUtil.isNotEmpty(description)) {
        presentation.setTypeText(description);
      }
    }

    private static @Nullable String getLocalizedText(ActionOrGroup actionOrGroup,
                                                     ActionOrGroup.TextType text,
                                                     NullableLazyValue<PropertiesFile> propertiesFile) {
      final String plain = text.getDomValue(actionOrGroup).getStringValue();
      if (StringUtil.isNotEmpty(plain)) return plain;

      final PropertiesFile bundleFile = propertiesFile.getValue();
      if (bundleFile == null) return null;

      final Property property = ObjectUtils.tryCast(bundleFile.findPropertyByKey(text.getMessageKey(actionOrGroup)), Property.class);
      return property != null ? property.getValue() : null;
    }
  }
}
