// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.DevKitIcons;
import org.jetbrains.idea.devkit.dom.index.IdeaPluginRegistrationIndex;
import org.jetbrains.idea.devkit.util.*;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

/**
 * Related declaration(s) in {@code plugin.xml} for class registered as:
 * <ul>
 *   <li>Action/ActionGroup</li>
 *   <li>Listener/Listener Topic</li>
 *   <li>Extension</li>
 *   <li>Component Interface/Implementation</li>
 * </ul>
 */
final class PluginDescriptorDeclarationRelatedItemLineMarkerProvider extends DevkitRelatedClassLineMarkerProviderBase {

  @Override
  public String getName() {
    return DevKitBundle.message("gutter.related.extension.declaration");
  }

  @Override
  public @NotNull Icon getIcon() {
    return DevKitIcons.Gutter.Plugin;
  }

  @Override
  protected void process(@NotNull PsiElement identifier,
                         @NotNull PsiClass psiClass,
                         @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
    if (psiClass.getQualifiedName() == null) {
      return;
    }

    if (!PsiUtil.isInstantiable(psiClass)) {

      // non-instantiable, abstract/interface can be registered as
      // - component interface-class
      // - listener topic
      if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        Project project = psiClass.getProject();
        GlobalSearchScope scope = PluginRelatedLocatorsUtils.getCandidatesScope(project);
        if (IdeaPluginRegistrationIndex.isRegisteredComponentInterface(psiClass, scope)) {
          processComponent(identifier, psiClass, result, project, scope);
        }
        else if (IdeaPluginRegistrationIndex.isRegisteredListenerTopic(psiClass, scope)) {
          List<ListenerCandidate> listenerTargets = new SmartList<>();
          IdeaPluginRegistrationIndex.processListenerTopic(project, psiClass, scope, listener -> {
            listenerTargets.add(new ListenerCandidate(listener));
            return true;
          });
          result.add(LineMarkerInfoHelper.createListenerTopicLineMarkerInfo(listenerTargets, identifier));
        }
      }
      return;
    }

    Project project = psiClass.getProject();
    GlobalSearchScope candidatesScope = PluginRelatedLocatorsUtils.getCandidatesScope(project);

    // only extensions are not indexed via IdeaPluginRegistrationIndex
    if (!IdeaPluginRegistrationIndex.isRegisteredClass(psiClass, candidatesScope)) {
      List<ExtensionCandidate> epTargets = ExtensionLocatorKt.locateExtensionsByPsiClass(psiClass);
      if (!epTargets.isEmpty()) {
        result.add(LineMarkerInfoHelper.createExtensionLineMarkerInfo(epTargets, identifier));
      }
      return;
    }

    // Action/ActionGroup (most likely)
    // search all candidates, e.g. EmptyAction/NonTrivialActionGroup is registered multiple times
    if (IdeaPluginRegistrationIndex.isRegisteredActionOrGroup(psiClass, candidatesScope)) {
      List<ActionCandidate> targets = new SmartList<>();
      IdeaPluginRegistrationIndex.processActionOrGroupClass(project, psiClass, candidatesScope, actionOrGroup -> {
        targets.add(new ActionCandidate(actionOrGroup));
        return true;
      });
      if (InheritanceUtil.isInheritor(psiClass, ActionGroup.class.getName())) {
        result.add(LineMarkerInfoHelper.createActionGroupLineMarkerInfo(targets, identifier));
      }
      else {
        result.add(LineMarkerInfoHelper.createActionLineMarkerInfo(targets, identifier));
      }
      return;
    }

    // Listeners: search for _all_ candidates as
    // - some listeners are registered on both application- and project-level, e.g. com.intellij.notification.Notifications
    // - some classes implement multiple listeners, e.g. com.intellij.notification.impl.widget.NotificationWidgetListener
    List<ListenerCandidate> listenerTargets = new SmartList<>();
    IdeaPluginRegistrationIndex.processListener(project, psiClass, candidatesScope, listener -> {
      listenerTargets.add(new ListenerCandidate(listener));
      return true;
    });
    if (!listenerTargets.isEmpty()) {
      result.add(LineMarkerInfoHelper.createListenerLineMarkerInfo(listenerTargets, identifier));
      return;
    }

    processComponent(identifier, psiClass, result, project, candidatesScope);
  }

  private static void processComponent(@NotNull PsiElement identifier,
                                       @NotNull PsiClass psiClass,
                                       @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result,
                                       Project project,
                                       GlobalSearchScope candidatesScope) {
    // Components: search for _all_ occurrences
    // - component can be registered multiple times
    // - component-interface can occur multiple times
    List<ComponentCandidate> componentTargets = new SmartList<>();
    IdeaPluginRegistrationIndex.processComponent(project, psiClass, candidatesScope, component -> {
      componentTargets.add(new ComponentCandidate(component));
      return true;
    });
    if (!componentTargets.isEmpty()) {
      result.add(LineMarkerInfoHelper.createComponentLineMarkerInfo(componentTargets, identifier));
    }
  }
}
