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


import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.TailType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.position.LeftNeighbour;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.plugins.groovy.lang.completion.filters.toplevel.PackageFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.toplevel.ImportFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.toplevel.ClassInterfaceEnumFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.control.ControlStructureFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.control.BranchFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.control.additional.CaseDefaultFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.control.additional.CatchFinallyFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.control.additional.ElseFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.types.BuiltInTypeFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.classdef.ExtendsFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.classdef.ImplementsFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.exprs.SimpleExpressionFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.exprs.InstanceOfFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.modifiers.*;
import org.jetbrains.plugins.groovy.GroovyFileType;

import java.util.Set;

/**
 * @author ilyas
 */
public class GroovyCompletionData extends CompletionData {

  public GroovyCompletionData() {
    registerAllCompletions();

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
    registerBranchCompletion();
    registerModifierCompletion();
    registerSynchronizedCompletion();
    registerFinalCompletion();
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
        "return", "throw", "assert", "synchronized",};

    registerStandardCompletion(new ControlStructureFilter(), controlKeywords);
    registerStandardCompletion(new CaseDefaultFilter(), "case", "default");
    registerStandardCompletion(new CatchFinallyFilter(), "catch", "finally");
    registerStandardCompletion(new ElseFilter(), "else");


  }

  private void registerBuiltInTypeCompletion() {
    String[] builtInTypes = {"boolean", "byte", "char", "short", "int", "float", "long", "double", "void"};
    registerStandardCompletion(new AndFilter (new BuiltInTypeFilter(), new NotFilter(new ThrowsFilter())), builtInTypes);
  }

  private void registerSimpleExprsCompletion() {
    String[] exprs = {"true", "false", "null", "super", "new", "this"};
    registerStandardCompletion(new SimpleExpressionFilter(), exprs);
  }

  private void registerThrowsCompletion() {
    registerStandardCompletion(new ThrowsFilter(), "throws");
  }

  private void registerFinalCompletion() {
    registerStandardCompletion(new AndFilter (new FinalFilter(), new NotFilter(new ThrowsFilter())), "final");
  }

  private void registerSynchronizedCompletion() {
    registerStandardCompletion(new SynchronizedFilter(), "synchronized");
  }

  private void registerImportCompletion() {
    registerStandardCompletion(new ImportFilter(), "import");
  }

  private void registerInstanceofCompletion() {
    registerStandardCompletion(new InstanceOfFilter(), "instanceof");
  }

  private void registerBranchCompletion() {
    registerStandardCompletion(new BranchFilter(), "break", "continue");
  }

  private void registerModifierCompletion() {
    String[] modifiers = new String[]{"private", "public", "protected", "static", "transient", "abstract",
        "native", "volatile", "strictfp"};
    registerStandardCompletion(new ModifiersFilter(), modifiers);
    registerStandardCompletion(new LeftNeighbour(new PreviousModifierFilter()), "private", "public", "protected", "static", "transient", "abstract",
        "native", "volatile", "strictfp", "synchronized");
  }

  private static CompletionVariant ourReferenceVariant;
  static {
    ourReferenceVariant = new CompletionVariant() {
      public void addReferenceCompletions(PsiReference reference, PsiElement position, Set<LookupItem> set, CompletionContext prefix) {
        addReferenceCompletions(reference, position, set, prefix, new CompletionVariantItem(TrueFilter.INSTANCE, TailType.NONE));
      }
    };
    
    ourReferenceVariant.setInsertHandler(new GroovyInsertHandler());

    DefaultCharFilter.registerFilter(GroovyFileType.GROOVY_FILE_TYPE.getLanguage(), new CharFilter() {
      public int accept(char c, String prefix) {
        if (Character.isJavaIdentifierPart(c)) return CharFilter.ADD_TO_PREFIX;
        else if (c == '\n' || c == '\t') {
          return CharFilter.SELECT_ITEM_AND_FINISH_LOOKUP;
        }
        return CharFilter.HIDE_LOOKUP;
      }
    });
  }

  public void completeReference(PsiReference reference, Set<LookupItem> set, CompletionContext context, PsiElement position) {
    ourReferenceVariant.addReferenceCompletions(reference, position, set, context);
  }

  /**
   * Template to add all standard keywords completions
   *
   * @param filter   - Semantic filter for given keywords
   * @param keywords - Keywords to be completed
   */
  private void registerStandardCompletion(ElementFilter filter, String... keywords) {
    LeftNeighbour afterDotFilter = new LeftNeighbour(new TextFilter("."));
    CompletionVariant variant = new CompletionVariant(new AndFilter(new NotFilter(afterDotFilter), filter));
    variant.includeScopeClass(LeafPsiElement.class);
    variant.addCompletionFilterOnElement(TrueFilter.INSTANCE);
    variant.setInsertHandler(new GroovyInsertHandler());
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
      variant.addCompletion(completion, TailType.SPACE);
    }
  }


}
