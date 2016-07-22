/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PairProcessor;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.ElementPresentationManager;
import com.intellij.util.xml.ResolvingConverter;
import com.intellij.util.xml.impl.DomImplUtil;
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
  public ActionOrGroup fromString(@Nullable @NonNls final String value, ConvertContext context) {
    if (StringUtil.isEmptyOrSpaces(value)) return null;

    final ActionOrGroup[] result = {null};
    PairProcessor<String, ActionOrGroup> findProcessor = (s, actionOrGroup) -> {
      if (isRelevant(actionOrGroup) &&
          Comparing.strEqual(value, s)) {
        result[0] = actionOrGroup;
        return false;
      }
      return true;
    };
    processActionOrGroup(context, findProcessor);
    return result[0];
  }

  @Nullable
  @Override
  public String toString(@Nullable ActionOrGroup actionGroup, ConvertContext context) {
    return actionGroup == null ? null : getName(actionGroup);
  }

  @Override
  public String getErrorMessage(@Nullable String s, ConvertContext context) {
    return "Cannot resolve action or group '" + s + "'";
  }

  @Nullable
  @Override
  public LookupElement createLookupElement(ActionOrGroup actionOrGroup) {
    if (actionOrGroup instanceof Action) {
      Action action = (Action)actionOrGroup;
      String msg = action.getId().getStringValue() + " in " + DomUtil.getFile(action) + " " + action.isValid() + " ";
      DomImplUtil.assertValidity(action, msg);
      final PsiElement element = getPsiElement(actionOrGroup);
      if (element == null) {
        throw new IllegalStateException("no PSI: " + msg);
      }

      LookupElementBuilder builder =
        LookupElementBuilder.create(ObjectUtils.assertNotNull(element),
                                    ObjectUtils.assertNotNull(getName(action)));

      final String text = action.getText().getStringValue();
      if (StringUtil.isNotEmpty(text)) {
        String withoutMnemonic = StringUtil.replace(text, "_", "");
        builder = builder.withTailText(" \"" + withoutMnemonic + "\"", true);
      }

      return builder;
    }

    return super.createLookupElement(actionOrGroup);
  }

  protected boolean isRelevant(ActionOrGroup actionOrGroup) {
    return true;
  }

  public static class OnlyActions extends ActionOrGroupResolveConverter {
    @Override
    protected boolean isRelevant(ActionOrGroup actionOrGroup) {
      return actionOrGroup instanceof Action;
    }

    @Override
    public String getErrorMessage(@Nullable String s, ConvertContext context) {
      return "Cannot resolve action '" + s + "'";
    }
  }

  public static class OnlyGroups extends ActionOrGroupResolveConverter {
    @Override
    protected boolean isRelevant(ActionOrGroup actionOrGroup) {
      return actionOrGroup instanceof Group;
    }

    @Override
    public String getErrorMessage(@Nullable String s, ConvertContext context) {
      return "Cannot resolve group '" + s + "'";
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
    for (IdeaPlugin plugin : plugins) {
      final Map<String, ActionOrGroup> forFile = collectForFile(plugin);
      for (Map.Entry<String, ActionOrGroup> entry : forFile.entrySet()) {
        if (!processor.process(entry.getKey(), entry.getValue())) return false;
      }
    }
    return true;
  }

  private static Map<String, ActionOrGroup> collectForFile(final IdeaPlugin plugin) {
    final XmlFile xmlFile = DomUtil.getFile(plugin);
    return CachedValuesManager.getCachedValue(xmlFile, () -> {
      Map<String, ActionOrGroup> result = new HashMap<>();
      for (Actions actions : plugin.getActions()) {
        collectRecursive(result, actions);
      }

      return CachedValueProvider.Result.create(result, xmlFile);
    });
  }

  private static void collectRecursive(Map<String, ActionOrGroup> result, Actions actions) {
    for (Action action : actions.getActions()) {
      final String name = getName(action);
      if (!StringUtil.isEmptyOrSpaces(name)) {
        result.put(name, action);
      }
    }
    for (Group group : actions.getGroups()) {
      final String name = getName(group);
      if (!StringUtil.isEmptyOrSpaces(name)) {
        result.put(name, group);
      }
      collectRecursive(result, group);
    }
  }

  @Nullable
  private static String getName(@NotNull ActionOrGroup actionOrGroup) {
    return ElementPresentationManager.getElementName(actionOrGroup);
  }
}
