// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;

import javax.swing.JComponent;
import java.util.function.Consumer;

import static com.intellij.openapi.actionSystem.PlatformDataKeys.CONTEXT_COMPONENT;

public abstract class ExpandableActions extends DumbAwareAction {
  private final Consumer<Expandable> consumer;

  private ExpandableActions(Consumer<Expandable> consumer) {
    setEnabledInModalContext(true);
    this.consumer = consumer;
  }

  private static Expandable getExpandable(AnActionEvent event) {
    Object component = event.getData(CONTEXT_COMPONENT);
    if (component instanceof Expandable) return (Expandable)component;
    if (component instanceof JComponent) {
      JComponent container = (JComponent)component;
      Object property = container.getClientProperty(Expandable.class);
      if (property instanceof Expandable) return (Expandable)property;
    }
    return null;
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    Expandable expandable = getExpandable(event);
    if (expandable != null) consumer.accept(expandable);
  }

  public static final class Expand extends ExpandableActions {
    public Expand() {
      super(Expandable::expand);
    }

    @Override
    public void update(AnActionEvent event) {
      Expandable expandable = getExpandable(event);
      event.getPresentation().setEnabled(expandable != null && !expandable.isExpanded());
    }
  }

  public static final class Collapse extends ExpandableActions {
    public Collapse() {
      super(Expandable::collapse);
    }

    @Override
    public void update(AnActionEvent event) {
      Expandable expandable = getExpandable(event);
      event.getPresentation().setEnabled(expandable != null && expandable.isExpanded());
    }
  }
}
