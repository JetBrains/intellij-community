/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.incrementalMerge.ui;

import com.intellij.openapi.diff.impl.highlighting.FragmentSide;

/**
 * Represents one of three columns in the 3-way merge.
 * To be used when specific column code is needed.
 *
 * Little by little, this should substitute all legacy code, that references merge columns by digital indexes from 1 to 3 or from 0 to 2,
 * as well as by {@link FragmentSide FragmentSides}.
 *
 * @author Kirill Likhodedov
 */
public enum MergePanelColumn {
  LEFT, BASE, RIGHT
}
