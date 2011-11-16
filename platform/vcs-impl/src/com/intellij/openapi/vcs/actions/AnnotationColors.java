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

import com.intellij.ui.Gray;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public interface AnnotationColors {
  Color[] BG_COLORS = {
    new Color(222, 241, 229),
    new Color(234, 255, 226),
    new Color(208, 229, 229),
    new Color(255, 226, 199),
    new Color(227, 226, 223),
    new Color(255, 213, 203),
    new Color(220, 204, 236),
    new Color(255, 191, 195),
    new Color(243, 223, 243),
    new Color(217, 228, 249),
    new Color(255, 251, 207),
    new Color(217, 222, 229),
    new Color(255, 204, 238),
    Gray._236
  };
}
