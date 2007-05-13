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
import com.intellij.codeInsight.completion.CompletionVariant;
import com.intellij.codeInsight.completion.WordCompletionData;
import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiReference;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.position.LeftNeighbour;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.plugins.groovy.lang.completion.filters.toplevel.PackageFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.toplevel.ImportFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.toplevel.ClassInterfaceEnumFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.control.ControlStructureFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.control.additional.CaseDefaultFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.control.additional.CatchFinallyFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.control.additional.ElseFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.types.BuiltInTypeFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.classdef.ExtendsFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.classdef.ImplementsFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.exprs.SimpleExpressionFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.exprs.InstanceOfFilter;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author ilyas
 */
public class GroovyCompletionData extends CompletionData {

  public GroovyCompletionData() {
    registerAllCompletions();

  }

  @Nullable
  public static PsiElement nearestLeftSibling(PsiElement elem) {
    elem = elem.getPrevSibling();
    while (elem != null &&
        (elem instanceof PsiWhiteSpace ||
            elem instanceof PsiComment ||
            GroovyTokenTypes.mNLS.equals(elem.getNode().getElementType()))) {
      elem = elem.getPrevSibling();
    }
    return elem;
  }

  private void registerAllCompletions1() {
    LeftNeighbour afterDotFilter = new LeftNeighbour(new TextFilter("."));
    CompletionVariant variant = new CompletionVariant(new NotFilter(afterDotFilter));

    variant.includeScopeClass(LeafPsiElement.class);
    variant.addCompletionFilterOnElement(TrueFilter.INSTANCE);
    String[] keywords = new String[]{
        "class", "interface", "enum",   // Types
        "extends", "implements",  // Other
        "try", "while", "with", "switch", "for", "return", "break", "continue", "throw", "assert", "synchronized",  // Control
        "finally", "catch", // Additional 1
        "case", "default", // Additional 2
        "else", // Additional 3
        "true", "false", "null", "super", "new", "this", // Expressions
        "instanceof",

        "boolean", "byte", "char", "short", "int", "float", "long", "double", "any", // Built-in Types
        "private", "public", "protected", "transient", "native", "threadsafe", "volatile", "static", "def", "void",
        "throws"

    };


    variant.addCompletion(keywords);
    registerVariant(variant);
  }

  /**
   * Registers completions on top level of Groovy script file
   */
  private void registerAllCompletions() {
    registerPackageCompletion();
    registerImportCompletion();

    registerClassInterfaceEnumAnnotationCompletion();
    registerControlCompletion();
    registerSimpleExprsCompletion();
    registerBuiltInTypeCompletion();
    registerInstanceofCompletion();
    registerThrowsCompletion();

  }


  private void registerPackageCompletion() {
    registerStandardCompletion(new PackageFilter(), "package");
  }

  private void registerClassInterfaceEnumAnnotationCompletion() {
    registerStandardCompletion(new ClassInterfaceEnumFilter(), "class", "interface", "enum");

    registerStandardCompletion(new ExtendsFilter(), "extends");
    registerStandardCompletion(new ImplementsFilter(), "implements");
  }

  private void registerControlCompletion() {
    String[] controlKeywords = {"try", "while", "with", "switch", "for",
        "return", "break", "continue", "throw", "assert", "synchronized",};

    registerStandardCompletion(new ControlStructureFilter(), controlKeywords);
    registerStandardCompletion(new CaseDefaultFilter(), "case", "default");
    registerStandardCompletion(new CatchFinallyFilter(), "catch", "finally");
    registerStandardCompletion(new ElseFilter(), "else");


  }

  private void registerBuiltInTypeCompletion() {
    String[] builtInTypes = {"boolean", "byte", "char", "short", "int", "float", "long", "double", "any"};
    registerStandardCompletion(new BuiltInTypeFilter(), builtInTypes);
  }

  private void registerSimpleExprsCompletion() {
    String[] exprs = {"true", "false", "null", "super", "new", "this"};
    registerStandardCompletion(new SimpleExpressionFilter(), exprs);
  }

  private void registerThrowsCompletion() {
    registerStandardCompletion(new SimpleExpressionFilter(), "throws");
  }

  private void registerImportCompletion() {
    registerStandardCompletion(new ImportFilter(), "import");
  }

  private void registerInstanceofCompletion() {
    registerStandardCompletion(new InstanceOfFilter(), "instanceof");
  }

  public void completeReference(PsiReference reference, Set<LookupItem> set, CompletionContext context, PsiElement position) {
    ourGenericVariant.addReferenceCompletions(reference, position, set, context);
  }

    /**
   * Template to add all standard keywords complettions
   *
   * @param filter   - Semantic filter for given keywords
   * @param keywords - Keyword to be completed
   */
  private void registerStandardCompletion(ElementFilter filter, String... keywords) {
    LeftNeighbour afterDotFilter = new LeftNeighbour(new TextFilter("."));
    CompletionVariant variant = new CompletionVariant(new AndFilter(new NotFilter(afterDotFilter), filter));
    variant.includeScopeClass(LeafPsiElement.class);
    variant.addCompletionFilterOnElement(TrueFilter.INSTANCE);
    addCompletions(variant, keywords);
    registerVariant(variant);
  }


  public String findPrefix(PsiElement insertedElement, int offset) {
    return WordCompletionData.findPrefixSimple(insertedElement, offset);

  }

  /**
   * Adds all completion variants in sequence
   *
   * @param comps   Given completions
   * @param variant Variant for completions
   */
  private void addCompletions(CompletionVariant variant, String... comps) {
    for (String completion : comps) {
      variant.addCompletion(completion);
    }
  }


}
