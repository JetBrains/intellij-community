// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.annotate.AnnotationSource;
import com.intellij.openapi.vcs.annotate.AnnotationSourceSwitcher;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
class SwitchAnnotationSourceAction extends AnAction implements DumbAware {
  private final static String ourShowMerged = VcsBundle.message("annotation.switch.to.merged.text");
  private final static String ourHideMerged = VcsBundle.message("annotation.switch.to.original.text");
  private final AnnotationSourceSwitcher mySwitcher;
  private final List<Consumer<AnnotationSource>> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private boolean myShowMerged;

  SwitchAnnotationSourceAction(final AnnotationSourceSwitcher switcher) {
    mySwitcher = switcher;
    myShowMerged = mySwitcher.getDefaultSource().showMerged();
  }

  public void addSourceSwitchListener(final Consumer<AnnotationSource> listener) {
    myListeners.add(listener);
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    e.getPresentation().setText(myShowMerged ? ourHideMerged : ourShowMerged);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    myShowMerged = !myShowMerged;
    final AnnotationSource newSource = AnnotationSource.getInstance(myShowMerged);
    mySwitcher.switchTo(newSource);
    for (Consumer<AnnotationSource> listener : myListeners) {
      listener.consume(newSource);
    }

    AnnotateActionGroup.revalidateMarkupInAllEditors();
  }
}
