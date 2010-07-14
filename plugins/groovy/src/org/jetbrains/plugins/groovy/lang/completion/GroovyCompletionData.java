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

package org.jetbrains.plugins.groovy.lang.completion;


import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.CompletionData;
import com.intellij.codeInsight.completion.CompletionVariant;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.position.LeftNeighbour;
import com.intellij.psi.filters.position.ParentElementFilter;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.completion.filters.classdef.ExtendsFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.classdef.ImplementsFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.control.BranchFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.control.ControlStructureFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.control.additional.CaseDefaultFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.control.additional.CatchFinallyFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.control.additional.ElseFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.exprs.InstanceOfFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.exprs.SimpleExpressionFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.modifiers.*;
import org.jetbrains.plugins.groovy.lang.completion.filters.toplevel.AnnotationFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.toplevel.ClassInterfaceEnumFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.toplevel.ImportFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.toplevel.PackageFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.types.BuiltInTypeAsArgumentFilter;
import org.jetbrains.plugins.groovy.lang.completion.filters.types.BuiltInTypeFilter;
import org.jetbrains.plugins.groovy.lang.completion.getters.SuggestedVariableNamesGetter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import java.util.Set;

/**
 * @author ilyas
 */
public class GroovyCompletionData extends CompletionData {
  private final static Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionData");
  public static final String[] BUILT_IN_TYPES = {"boolean", "byte", "char", "short", "int", "float", "long", "double", "void"};
  public static final String[] MODIFIERS = new String[]{"private", "public", "protected", "transient", "abstract", "native", "volatile", "strictfp"};

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
    registerBuiltInTypesAsArgumentCompletion();
    registerInstanceofCompletion();
    registerThrowsCompletion();
    registerBranchCompletion();
    registerModifierCompletion();
    registerSynchronizedCompletion();
    registerFinalCompletion();

    registerSuggestVariableNameCompletion();
  }

  private void registerSuggestVariableNameCompletion() {
    CompletionVariant variant = new CompletionVariant(new ParentElementFilter(new ClassFilter(GrVariable.class)));
    variant.includeScopeClass(LeafPsiElement.class);
    variant.addCompletion(new SuggestedVariableNamesGetter(), TailType.NONE);
    registerVariant(variant);
  }


  private void registerPackageCompletion() {
    registerStandardCompletion(new PackageFilter(), "package");
  }

  private void registerClassInterfaceEnumAnnotationCompletion() {
    registerStandardCompletion(new ClassInterfaceEnumFilter(), "class", "interface", "enum");
    registerStandardCompletion(new AnnotationFilter(), "interface");
    registerStandardCompletion(new ExtendsFilter(), "extends");
    registerStandardCompletion(new ImplementsFilter(), "implements");
  }

  private void registerControlCompletion() {
    String[] controlKeywords = {"try", "while", "with", "switch", "for", "return", "throw", "assert", "synchronized",};

    registerStandardCompletion(new ControlStructureFilter(), controlKeywords);
    registerStandardCompletion(new CaseDefaultFilter(), "case", "default");
    registerStandardCompletion(new CatchFinallyFilter(), "catch", "finally");
    registerStandardCompletion(new ElseFilter(), "else");


  }

  private void registerBuiltInTypeCompletion() {
    registerStandardCompletion(new AndFilter(new BuiltInTypeFilter(), new NotFilter(new ThrowsFilter())), BUILT_IN_TYPES);
  }

  private void registerBuiltInTypesAsArgumentCompletion() {
    AndFilter filter = new AndFilter(new BuiltInTypeAsArgumentFilter(), new NotFilter(new ThrowsFilter()));
    registerStandardCompletion(filter, BUILT_IN_TYPES);
  }

  private void registerSimpleExprsCompletion() {
    String[] exprs = {"true", "false", "null", "super", "new", "this"};
    registerStandardCompletion(new SimpleExpressionFilter() {
      @Override
      public boolean isAcceptable(Object element, PsiElement context) {
        return super.isAcceptable(element, context) && !(context.getParent() instanceof GrLiteral);
      }
    }, exprs);
  }

  private void registerThrowsCompletion() {
    registerStandardCompletion(new ThrowsFilter(), "throws");
  }

  private void registerFinalCompletion() {
    registerStandardCompletion(new AndFilter(new FinalFilter(), new NotFilter(new ThrowsFilter())), "final");
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
    registerStandardCompletion(new ModifiersFilter(), MODIFIERS);
    registerStandardCompletion(new LeftNeighbour(new PreviousModifierFilter()), "private", "public", "protected", "transient", "abstract",
                               "native", "volatile", "strictfp", "synchronized", "static");
    registerStandardCompletion(new StaticFilter(), "static");
  }


  @Override
  public void completeReference(final PsiReference reference,
                                final Set<LookupElement> set,
                                @NotNull final PsiElement position,
                                final PsiFile file,
                                final int offset) {
    super.completeReference(reference, set, position, file, offset);
    Set<LookupElement> result = new THashSet<LookupElement>();
    for (final LookupElement element : set) {
      result.add(LookupElementDecorator.withInsertHandler(element, new GroovyInsertHandlerAdapter()));
    }
    set.clear();
    set.addAll(result);
  }


  /**
   * Template to add all standard keywords completions
   *
   * @param filter   - Semantic filter for given keywords
   * @param keywords - Keywords to be completed
   */
  private void registerStandardCompletion(ElementFilter filter, String... keywords) {
    LeftNeighbour afterDotFilter = new LeftNeighbour(new TextFilter(".", ".&"));
    CompletionVariant variant = new CompletionVariant(new AndFilter(new NotFilter(afterDotFilter), filter));
    variant.setItemProperty(LookupItem.HIGHLIGHTED_ATTR, "");
    variant.includeScopeClass(LeafPsiElement.class);
    variant.setInsertHandler(new GroovyInsertHandlerAdapter());
    addCompletions(variant, keywords);
    registerVariant(variant);
  }


  public String findPrefix(PsiElement insertedElement, int offset) {
    if (insertedElement == null) return "";
    final String text = insertedElement.getText();
    final int offsetInElement = offset - insertedElement.getTextRange().getStartOffset();
    int start = offsetInElement - 1;
    while (start >= 0) {
      final char c = text.charAt(start);
      if (!Character.isJavaIdentifierPart(c) && c != '\'') break;
      --start;
    }

    return text.substring(start + 1, offsetInElement).trim();
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
