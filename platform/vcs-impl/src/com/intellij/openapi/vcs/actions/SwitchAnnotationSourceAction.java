/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.annotate.AnnotationSource;
import com.intellij.openapi.vcs.annotate.AnnotationSourceSwitcher;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;

import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
class SwitchAnnotationSourceAction extends AnAction {
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
  public void update(final AnActionEvent e) {
    e.getPresentation().setText(myShowMerged ? ourHideMerged : ourShowMerged);
  }

  public void actionPerformed(AnActionEvent e) {
    myShowMerged = !myShowMerged;
    final AnnotationSource newSource = AnnotationSource.getInstance(myShowMerged);
    mySwitcher.switchTo(newSource);
    for (Consumer<AnnotationSource> listener : myListeners) {
      listener.consume(newSource);
    }

    AnnotateActionGroup.revalidateMarkupInAllEditors();
  }
}
