/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.references.PomService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.PairProcessor;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.*;

import java.util.*;

public class ActionOrGroupResolveConverter extends ResolvingConverter<ActionOrGroup> {

  @NotNull
  @Override
  public Collection<? extends ActionOrGroup> getVariants(ConvertContext context) {
    final List<ActionOrGroup> variants = new ArrayList<>();
    PairProcessor<String, ActionOrGroup> collectProcessor = (s, actionOrGroup) -> {
      if (isRelevant(actionOrGroup)) {
        variants.add(actionOrGroup);
      }
      return true;
    };
    processActionOrGroup(context, collectProcessor);
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

    Ref<ActionOrGroup> result = Ref.create();
    PairProcessor<String, ActionOrGroup> findProcessor = (s, actionOrGroup) -> {
      if (isRelevant(actionOrGroup) &&
          Comparing.strEqual(value, s)) {
        result.set(actionOrGroup);
        return false;
      }
      return true;
    };
    processActionOrGroup(context, findProcessor);
    return result.get();
  }

  @Nullable
  @Override
  public String toString(@Nullable ActionOrGroup actionGroup, ConvertContext context) {
    return actionGroup == null ? null : getName(actionGroup);
  }

  @Override
  public String getErrorMessage(@Nullable String s, ConvertContext context) {
    return "Cannot resolve " + getResultTypes() + " '" + s + "'";
  }

  @Nullable
  @Override
  public LookupElement createLookupElement(ActionOrGroup actionOrGroup) {
    PsiElement psiElement = getPsiElement(actionOrGroup);
    String name = StringUtil.notNullize(getName(actionOrGroup), "<invalid name>");
    LookupElementBuilder builder = psiElement == null ? LookupElementBuilder.create(name) :
                                   LookupElementBuilder.create(psiElement, name);

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

  protected String getResultTypes() {
    return "action or group";
  }


  public static class OnlyActions extends ActionOrGroupResolveConverter {
    @Override
    protected boolean isRelevant(ActionOrGroup actionOrGroup) {
      return actionOrGroup instanceof Action;
    }

    @Override
    protected String getResultTypes() {
      return "action";
    }
  }

  public static class OnlyGroups extends ActionOrGroupResolveConverter {
    @Override
    protected boolean isRelevant(ActionOrGroup actionOrGroup) {
      return actionOrGroup instanceof Group;
    }

    @Override
    protected String getResultTypes() {
      return "group";
    }
  }


  private static boolean processActionOrGroup(ConvertContext context, final PairProcessor<String, ActionOrGroup> processor) {
    final Project project = context.getProject();

    Module module = context.getModule();
    if (module == null) {
      final Collection<IdeaPlugin> plugins = IdeaPluginConverter.getAllPlugins(project);
      return processPlugins(plugins, processor);
    }

    return ModuleUtilCore.visitMeAndDependentModules(module, module1 -> {
      final Collection<IdeaPlugin> dependenciesAndLibs =
        IdeaPluginConverter.getPlugins(project, module1.getModuleRuntimeScope(false));
      return processPlugins(dependenciesAndLibs, processor);
    });
  }

  private static boolean processPlugins(Collection<IdeaPlugin> plugins, PairProcessor<String, ActionOrGroup> processor) {
    for (IdeaPlugin plugin: plugins) {
      final Map<String, ActionOrGroup> forFile = collectForFile(plugin);
      for (Map.Entry<String, ActionOrGroup> entry: forFile.entrySet()) {
        if (!processor.process(entry.getKey(), entry.getValue())) return false;
      }
    }
    return true;
  }

  private static Map<String, ActionOrGroup> collectForFile(final IdeaPlugin plugin) {
    final XmlFile xmlFile = DomUtil.getFile(plugin);
    return CachedValuesManager.getCachedValue(xmlFile, () -> {
      Map<String, ActionOrGroup> result = new HashMap<>();
      for (Actions actions: plugin.getActions()) {
        collectRecursive(result, actions);
      }

      return CachedValueProvider.Result.create(result, xmlFile);
    });
  }

  private static void collectRecursive(Map<String, ActionOrGroup> result, Actions actions) {
    for (Action action: actions.getActions()) {
      final String name = getName(action);
      if (!StringUtil.isEmptyOrSpaces(name)) {
        result.put(name, action);
      }
    }
    for (Group group: actions.getGroups()) {
      final String name = getName(group);
      if (!StringUtil.isEmptyOrSpaces(name)) {
        result.put(name, group);
      }
      collectRecursive(result, group);
    }
  }

  @Nullable
  private static String getName(@NotNull ActionOrGroup actionOrGroup) {
    return actionOrGroup.getId().getStringValue();
  }
}
