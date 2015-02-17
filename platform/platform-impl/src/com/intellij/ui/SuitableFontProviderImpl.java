/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.editor.impl.ComplementaryFontsRegistry;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author egor
 */
public class SuitableFontProviderImpl implements SuitableFontProvider {
  @Override
  public Font getFontAbleToDisplay(char c, int size, @JdkConstants.FontStyle int style, @NotNull String defaultFontFamily) {
    return ComplementaryFontsRegistry.getFontAbleToDisplay(c, size, style, defaultFontFamily).getFont();
  }
}
