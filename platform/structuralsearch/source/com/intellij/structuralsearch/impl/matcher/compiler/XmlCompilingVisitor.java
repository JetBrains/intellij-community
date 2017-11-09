// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.psi.PsiElement;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.XmlRecursiveElementWalkingVisitor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlToken;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.filters.TagValueFilter;
import com.intellij.structuralsearch.impl.matcher.handlers.MatchingHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.TopLevelMatchingHandler;
import com.intellij.structuralsearch.impl.matcher.predicates.RegExpPredicate;
import org.jetbrains.annotations.Nullable;

/**
* @author Eugene.Kudelevsky
*/
public class XmlCompilingVisitor extends XmlRecursiveElementVisitor {
  private final GlobalCompilingVisitor myCompilingVisitor;

  public XmlCompilingVisitor(GlobalCompilingVisitor compilingVisitor) {
    this.myCompilingVisitor = compilingVisitor;
  }

  public void compile(PsiElement[] topLevelElements) {
    final WordOptimizer optimizer = new WordOptimizer();
    final CompiledPattern pattern = myCompilingVisitor.getContext().getPattern();
    for (PsiElement element : topLevelElements) {
      element.accept(this);
      element.accept(optimizer);
      pattern.setHandler(element, new TopLevelMatchingHandler(pattern.getHandler(element)));
    }
  }

  private class WordOptimizer extends XmlRecursiveElementWalkingVisitor {

    @Override
    public void visitXmlTag(XmlTag tag) {
      if (!handleWord(tag.getName())) return;
      super.visitXmlTag(tag);
    }

    @Override
    public void visitXmlAttribute(XmlAttribute attribute) {
      if (!handleWord(attribute.getName())) return;
      handleWord(attribute.getValue());
      super.visitXmlAttribute(attribute);
    }

    /**
     * @param word  word to check index with
     * @return true, if psi tree should be processed deeper, false otherwise.
     */
    private boolean handleWord(@Nullable String word) {
      if (word == null) {
        return true;
      }
      final CompileContext compileContext = myCompilingVisitor.getContext();
      final CompiledPattern pattern = compileContext.getPattern();
      if (pattern.isTypedVar(word)) {
        final SubstitutionHandler handler = (SubstitutionHandler)pattern.getHandler(word);
        if (handler == null || handler.getMinOccurs() == 0) {
          // don't call super
          return false;
        }

        final RegExpPredicate predicate = MatchingHandler.getSimpleRegExpPredicate(handler);
        if (predicate != null && predicate.couldBeOptimized()) {
          GlobalCompilingVisitor.addFilesToSearchForGivenWord(predicate.getRegExp(), true, GlobalCompilingVisitor.OccurenceKind.CODE,
                                                              compileContext);
        }
      }
      else {
        GlobalCompilingVisitor.addFilesToSearchForGivenWord(word, true, GlobalCompilingVisitor.OccurenceKind.CODE, compileContext);
      }
      return true;
    }
  }

  @Override public void visitElement(PsiElement element) {
    myCompilingVisitor.handle(element);
    super.visitElement(element);
  }

  @Override
  public void visitXmlToken(XmlToken token) {}

  @Override
  public void visitXmlText(XmlText text) {
    super.visitXmlText(text);

    final MatchingHandler handler = myCompilingVisitor.getContext().getPattern().getHandler(text);
    handler.setFilter(TagValueFilter.getInstance());
  }
}
