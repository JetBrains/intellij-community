/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.parser.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Interface for GradleDslElements to provide anchors for positioning their children. These anchors are requested when creating the
 * child.
 */
public interface AnchorProvider {
  /**
   * @param element the element an anchor has been requested for
   * @return the element that should be used as an anchor, the new element is created after this anchor
   */
  @Nullable
  GradleDslElement requestAnchor(@NotNull GradleDslElement element);
}
