/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInsight.hint;

import com.intellij.codeInsight.hint.DeclarationRangeHandler;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer;

/**
 * Created by Max Medvedev on 10/24/13
 */
public class GrClassInitializerDeclarationRangeHandler implements DeclarationRangeHandler<GrClassInitializer> {
  @NotNull
  @Override
  public TextRange getDeclarationRange(@NotNull GrClassInitializer initializer) {
    int startOffset = initializer.getModifierList().getTextRange().getStartOffset();
    int endOffset = initializer.getBlock().getTextRange().getStartOffset();
    return new TextRange(startOffset, endOffset);
  }
}
