// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.function.Consumer;

import static com.intellij.openapi.actionSystem.PlatformCoreDataKeys.CONTEXT_COMPONENT;

@ApiStatus.Internal
public abstract class ExpandableActions extends DumbAwareAction implements ActionRemoteBehaviorSpecification.Frontend {
  private final Consumer<? super Expandable> consumer;

  private ExpandableActions(Consumer<? super Expandable> consumer) {
    setEnabledInModalContext(true);
    this.consumer = consumer;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  private static Expandable getExpandable(AnActionEvent event) {
    Object component = event.getData(CONTEXT_COMPONENT);
    if (component instanceof Expandable) return (Expandable)component;
    if (component instanceof JComponent container) {
      Object property = container.getClientProperty(Expandable.class);
      if (property instanceof Expandable) return (Expandable)property;
    }
    return null;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    Expandable expandable = getExpandable(event);
    if (expandable != null) consumer.accept(expandable);
  }

  public static final class Expand extends ExpandableActions {
    public Expand() {
      super(Expandable::expand);
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
      Expandable expandable = getExpandable(event);
      event.getPresentation().setEnabled(expandable != null && !expandable.isExpanded());
    }
  }

  public static final class Collapse extends ExpandableActions {
    public Collapse() {
      super(Expandable::collapse);
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
      Expandable expandable = getExpandable(event);
      event.getPresentation().setEnabled(expandable != null && expandable.isExpanded());
    }
  }
}