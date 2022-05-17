// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
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
 *   <li>Listener</li>
 *   <li>Extension</li>
 * </ul>
 */
public final class PluginDescriptorDeclarationRelatedItemLineMarkerProvider extends DevkitRelatedClassLineMarkerProviderBase {

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
    if (!PsiUtil.isInstantiable(psiClass) ||
        psiClass.getQualifiedName() == null) {
      return;
    }

    Project project = psiClass.getProject();
    GlobalSearchScope candidatesScope = PluginRelatedLocatorsUtils.getCandidatesScope(project);

    // only EPs are not indexed via IdeaPluginRegistrationIndex
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
    }
  }
}
