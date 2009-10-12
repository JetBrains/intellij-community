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

package com.intellij.lang.properties.psi;

import com.intellij.psi.PsiLiteralExpression;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class I18nizedTextGenerator {

  public abstract String getI18nizedText(String propertyKey, final @Nullable PropertiesFile propertiesFile,
                                         final PsiLiteralExpression context);

  public abstract String getI18nizedConcatenationText(String propertyKey, String parametersString,
                                                      final @Nullable PropertiesFile propertiesFile, final PsiLiteralExpression context);

}
