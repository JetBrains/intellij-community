// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.annotate.AnnotationSource;
import com.intellij.openapi.vcs.annotate.AnnotationSourceSwitcher;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;

/**
 * @author Konstantin Bulenkov
 */
final class SwitchAnnotationSourceAction extends AnAction implements DumbAware {
  private final AnnotationSourceSwitcher mySwitcher;
  private final List<Consumer<AnnotationSource>> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private boolean myShowMerged;

  SwitchAnnotationSourceAction(final AnnotationSourceSwitcher switcher) {
    mySwitcher = switcher;
    myShowMerged = mySwitcher.getDefaultSource().showMerged();
  }

  public void addSourceSwitchListener(Consumer<AnnotationSource> listener) {
    myListeners.add(listener);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    e.getPresentation().setText(myShowMerged ? getHideMerged() : getShowMerged());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    myShowMerged = !myShowMerged;
    final AnnotationSource newSource = AnnotationSource.getInstance(myShowMerged);
    mySwitcher.switchTo(newSource);
    for (Consumer<AnnotationSource> listener : myListeners) {
      listener.accept(newSource);
    }

    AnnotateActionGroup.revalidateMarkupInAllEditors();
  }

  @NlsActions.ActionText
  private static String getShowMerged() {
    return VcsBundle.message("annotation.switch.to.merged.text");
  }

  @NlsActions.ActionText
  private static String getHideMerged() {
    return VcsBundle.message("annotation.switch.to.original.text");
  }
}
