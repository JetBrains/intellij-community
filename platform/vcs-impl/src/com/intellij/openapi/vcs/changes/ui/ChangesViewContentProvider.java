/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @see ChangesViewContentEP#className
 */
public interface ChangesViewContentProvider {
  /**
   * Called when content tab is selected in toolwindow.
   */
  default void initTabContent(@NotNull Content content) {
    content.setComponent(initContent());
    content.setDisposer(() -> disposeContent());
  }

  /**
   * Called from {@link #initTabContent} to create tab content.
   * Unused if {@link #initTabContent} is overridden.
   */
  default JComponent initContent() {
    throw new UnsupportedOperationException();
  }

  /**
   * Called from {@link #initTabContent} register default tab disposal handler.
   * Unused if {@link #initTabContent} is overridden.
   */
  default void disposeContent() {
  }

  /**
   * @see ChangesViewContentEP#preloaderClassName
   */
  interface Preloader {
    /**
     * Called when content tab is created.
     */
    default void preloadTabContent(@NotNull Content content) {
    }
  }
}
