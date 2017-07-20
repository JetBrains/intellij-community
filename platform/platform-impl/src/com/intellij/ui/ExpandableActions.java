/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
  public void update(AnActionEvent event) {
    Expandable expandable = getExpandable(event);
    event.getPresentation().setEnabled(expandable != null);
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
  }

  public static final class Collapse extends ExpandableActions {
    public Collapse() {
      super(Expandable::collapse);
    }
  }
}
