/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.CompletionData;
import com.intellij.codeInsight.completion.WordCompletionData;
import com.intellij.codeInsight.completion.CompletionVariant;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.filters.position.LeftNeighbour;
import com.intellij.psi.filters.TextFilter;
import com.intellij.psi.filters.NotFilter;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.filters.AndFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.TopLevelFilter;

/**
 * @author ilyas
 */
public class GroovyCompletionData extends CompletionData {

  public GroovyCompletionData() {
    registerTopLevelCompletions();
  }

  private void registerAllCompletions() {
    LeftNeighbour afterDotFilter = new LeftNeighbour(new TextFilter("."));
    CompletionVariant variant = new CompletionVariant(new NotFilter(afterDotFilter));
    variant.includeScopeClass(LeafPsiElement.class);
    variant.addCompletionFilterOnElement(TrueFilter.INSTANCE);
    String[] keywords = new String[]{
            "package", "import", "static", "def", "class", "interface", "enum", "extends",
            "super", "void", "boolean", "byte", "char", "short", "int", "float", "long", "double", "any", "as",
            "private", "public", "protected", "transient", "native", "threadsafe", "synchronized", "volatile",
            "default", "throws", "implements", "this", "if", "else", "while", "with", "switch", "for", "in",
            "return", "break", "continue", "throw", "assert", "case", "try", "finally", "catch", "instanceof",
            "new", "true", "false", "null"
    };
    variant.addCompletion(keywords);
    registerVariant(variant);
  }

  /**
   * Registers completions on top level of Groovy script file
   */
  private void registerTopLevelCompletions() {
    LeftNeighbour afterDotFilter = new LeftNeighbour(new TextFilter("."));
    TopLevelFilter topLevelFiler = new TopLevelFilter();
    CompletionVariant variant = new CompletionVariant(new AndFilter(new NotFilter(afterDotFilter), topLevelFiler));
    variant.includeScopeClass(LeafPsiElement.class);
    variant.addCompletionFilterOnElement(TrueFilter.INSTANCE);
    addCompletions(variant,  "package", "import", "interface", "enum", "def", "class", "static",
            "new", "true", "false", "null","throw", "assert", "this", "if", "protected", "return",
            "while", "with", "switch", "for", "boolean", "byte", "char", "short", "int", "float", "long", "double");

/*
    String[] keywords = new String[]{
           "static", "extends", "super", "void", "any", "as", "private", "public",  "transient",
            "native", "threadsafe", "synchronized", "volatile",
            "default", "throws", "implements", "this", "if", "else", "in",
            "break", "continue",  "case", "try", "finally", "catch", "instanceof"

    };
*/
    registerVariant(variant);
  }



  public String findPrefix(PsiElement insertedElement, int offset) {
    return WordCompletionData.findPrefixSimple(insertedElement, offset);

  }

  /**
   * Adds all completion variants in sequence
   * @param comps  Given completions
   * @param variant Variant for completions
   */
  private void addCompletions(CompletionVariant variant, String... comps) {
    for (String completion : comps) {
      variant.addCompletion(completion);
    }
  }


}
