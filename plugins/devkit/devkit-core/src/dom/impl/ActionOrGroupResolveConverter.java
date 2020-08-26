// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.references.PomService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.ProjectScope;
import com.intellij.util.Processor;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomTarget;
import com.intellij.util.xml.ElementPresentationManager;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.Action;
import org.jetbrains.idea.devkit.dom.ActionOrGroup;
import org.jetbrains.idea.devkit.dom.Group;
import org.jetbrains.idea.devkit.dom.index.IdeaPluginRegistrationIndex;

import java.util.*;

public class ActionOrGroupResolveConverter extends ResolvingConverter<ActionOrGroup> {

  @NotNull
  @Override
  public Collection<? extends ActionOrGroup> getVariants(ConvertContext context) {
    final List<ActionOrGroup> variants = new ArrayList<>();
    final Set<String> processedVariants = new HashSet<>();

    processScopes(context, scope -> {
      IdeaPluginRegistrationIndex.processAllActionOrGroup(context.getProject(), scope, actionOrGroup -> {
        if (isRelevant(actionOrGroup) && processedVariants.add(getName(actionOrGroup))) {
          variants.add(actionOrGroup);
        }
        return true;
      });
      return true;
    });
    return variants;
  }

  @Nullable
  @Override
  public PsiElement getPsiElement(@Nullable ActionOrGroup resolvedValue) {
    if (resolvedValue == null) return null;
    DomTarget target = DomTarget.getTarget(resolvedValue);
    return target == null ? super.getPsiElement(resolvedValue) : PomService.convertToPsi(target);
  }

  @Nullable
  @Override
  public ActionOrGroup fromString(@Nullable @NonNls final String value, ConvertContext context) {
    if (StringUtil.isEmptyOrSpaces(value)) return null;

    final Project project = context.getProject();
    Ref<ActionOrGroup> result = Ref.create();
    processScopes(context, scope -> {
      return IdeaPluginRegistrationIndex.processActionOrGroup(project, value, scope, actionOrGroup -> {
        if (isRelevant(actionOrGroup)) {
          result.set(actionOrGroup);
          return false;
        }
        return true;
      });
    });
    return result.get();
  }

  @Nullable
  @Override
  public String toString(@Nullable ActionOrGroup actionGroup, ConvertContext context) {
    return actionGroup == null ? null : getName(actionGroup);
  }

  @Override
  public String getErrorMessage(@Nullable String s, ConvertContext context) {
    return DevKitBundle.message("plugin.xml.convert.action.or.group.cannot.resolve", getResultTypes(), s);
  }

  @Nullable
  @Override
  public LookupElement createLookupElement(ActionOrGroup actionOrGroup) {
    PsiElement psiElement = getPsiElement(actionOrGroup);
    String name = StringUtil.notNullize(getName(actionOrGroup), DevKitBundle.message("plugin.xml.convert.action.or.group.invalid.name"));
    LookupElementBuilder builder = psiElement == null ? LookupElementBuilder.create(name) :
                                   LookupElementBuilder.create(psiElement, name);

    builder = builder.withItemTextUnderlined(actionOrGroup.getPopup().getValue() == Boolean.TRUE);
    builder = builder.withIcon(ElementPresentationManager.getIcon(actionOrGroup));

    final String text = actionOrGroup.getText().getStringValue();
    if (StringUtil.isNotEmpty(text)) {
      String withoutMnemonic = StringUtil.replace(text, "_", "");
      builder = builder.withTailText(" \"" + withoutMnemonic + "\"", true);
    }

    final String description = actionOrGroup.getDescription().getStringValue();
    if (StringUtil.isNotEmpty(description)) {
      builder = builder.withTypeText(description);
    }

    return builder;
  }

  protected boolean isRelevant(ActionOrGroup actionOrGroup) {
    return true;
  }

  @Nls
  protected String getResultTypes() {
    return DevKitBundle.message("plugin.xml.convert.action.or.group.type.action.or.group");
  }


  public static class OnlyActions extends ActionOrGroupResolveConverter {
    @Override
    protected boolean isRelevant(ActionOrGroup actionOrGroup) {
      return actionOrGroup instanceof Action;
    }

    @Override
    protected String getResultTypes() {
      return DevKitBundle.message("plugin.xml.convert.action.or.group.type.action");
    }
  }

  public static class OnlyGroups extends ActionOrGroupResolveConverter {
    @Override
    protected boolean isRelevant(ActionOrGroup actionOrGroup) {
      return actionOrGroup instanceof Group;
    }

    @Override
    protected String getResultTypes() {
      return DevKitBundle.message("plugin.xml.convert.action.or.group.type.group");
    }
  }


  private static void processScopes(ConvertContext context,
                                    final Processor<GlobalSearchScope> processor) {
    final Project project = context.getProject();

    Module module = context.getModule();
    if (module == null) {
      final GlobalSearchScope projectScope = GlobalSearchScopesCore.projectProductionScope(project).
        union(ProjectScope.getLibrariesScope(project));
      processor.process(projectScope);
      return;
    }

    ModuleUtilCore.visitMeAndDependentModules(module, module1 -> {
      return processor.process(module1.getModuleRuntimeScope(false));
    });
  }

  @Nullable
  private static String getName(@NotNull ActionOrGroup actionOrGroup) {
    return actionOrGroup.getId().getStringValue();
  }
}
