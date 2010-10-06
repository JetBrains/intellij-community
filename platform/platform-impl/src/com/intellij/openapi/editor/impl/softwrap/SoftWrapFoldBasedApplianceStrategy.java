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
package com.intellij.openapi.editor.impl.softwrap;

import com.intellij.openapi.editor.ex.EditorEx;
import org.jetbrains.annotations.NotNull;

/**
 * This {@link SoftWrapApplianceStrategy} implementation operates on current fold processing state (enabled/disabled) and
 * {@link SoftWrapAppliancePlaces soft wrap appliance place}.
 * <p/>
 * <b>Rationale:</b> IJ editor toggles folding processing periodically (e.g. during showing collapsed fold region contents).
 * Hence, we don't need to use soft wraps processing there (because soft wraps data cache stores fold regions-related information).
 * However, there is a possible case that the folding is disabled at all, e.g. that is the case for VCS 'diff window'. Hence,
 * we need to distinguish between those situations.
 * <p/>
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 * @since 10/6/10 8:09 AM
 */
public class SoftWrapFoldBasedApplianceStrategy implements SoftWrapApplianceStrategy {

  private final EditorEx myEditor;
  private SoftWrapAppliancePlaces myPlace = SoftWrapAppliancePlaces.MAIN_EDITOR;

  public SoftWrapFoldBasedApplianceStrategy(EditorEx editor) {
    myEditor = editor;
  }

  @Override
  public boolean processSoftWraps() {
    return myEditor.getFoldingModel().isFoldingEnabled() || myPlace == SoftWrapAppliancePlaces.VCS_DIFF;
  }

  /**
   * Instructs current strategy about place where soft wraps-aware editor is used.
   * <p/>
   * {@link SoftWrapAppliancePlaces#MAIN_EDITOR} is used by default.
   *
   * @param place   place where soft wraps-aware editor is used
   */
  public void setCurrentPlace(@NotNull SoftWrapAppliancePlaces place) {
    myPlace = place;
  }
}
