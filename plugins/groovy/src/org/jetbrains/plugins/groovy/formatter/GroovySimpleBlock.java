/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.formatter;

import com.intellij.formatting.Alignment;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Wrap;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author Max Medvedev
 */
public class GroovySimpleBlock extends GroovyBlock {
  public GroovySimpleBlock(@NotNull ASTNode node,
                           @Nullable Alignment alignment,
                           @NotNull Indent indent,
                           @Nullable Wrap wrap,
                           CommonCodeStyleSettings settings,
                           GroovyCodeStyleSettings groovySettings,
                           @NotNull Map<PsiElement, Alignment> innerAlignments, Map<PsiElement, GroovyBlock> blocks) {
    super(node, alignment, indent, wrap, settings, groovySettings, innerAlignments, blocks);
    mySubBlocks = new GroovyBlockGenerator(this).generateSubBlocks();
  }
}
