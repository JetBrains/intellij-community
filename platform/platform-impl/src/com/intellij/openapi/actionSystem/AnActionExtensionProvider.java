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
package com.intellij.openapi.actionSystem;

import org.jetbrains.annotations.NotNull;

/**
 * Allows overriding AnAction behavior in some context, not affecting the other contexts.
 * <p>
 * Note, that various flags cannot be overridden. Ex:
 * {@link AnAction#isEnabledInModalContext()}
 * {@link AnAction#isDumbAware()}
 *
 * @see ExtendableAction
 */
public interface AnActionExtensionProvider extends ActionUpdateThreadAware {
  /**
   * @return whether current provider should be used in given context
   * <p>
   * Provider should not modify presentation in this method.
   * Only the first active provider will be used.
   * Method is called as {@link ActionUpdateThread#BGT}.
   */
  boolean isActive(@NotNull AnActionEvent e);

  void update(@NotNull AnActionEvent e);

  void actionPerformed(@NotNull AnActionEvent e);
}
