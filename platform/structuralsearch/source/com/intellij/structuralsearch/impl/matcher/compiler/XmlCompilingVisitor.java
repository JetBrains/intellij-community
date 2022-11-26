// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.psi.PsiElement;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.XmlRecursiveElementWalkingVisitor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.*;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.filters.TagValueFilter;
import com.intellij.structuralsearch.impl.matcher.handlers.TopLevelMatchingHandler;
import com.intellij.structuralsearch.impl.matcher.strategies.XmlMatchingStrategy;
import org.jetbrains.annotations.NotNull;

import static com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor.OccurenceKind.CODE;
import static com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor.OccurenceKind.TEXT;

/**
* @author Eugene.Kudelevsky
*/
public class XmlCompilingVisitor extends XmlRecursiveElementVisitor {
  @NotNull
  private final GlobalCompilingVisitor myCompilingVisitor;
  private final XmlWordOptimizer myOptimizer = new XmlWordOptimizer();

  public XmlCompilingVisitor(@NotNull GlobalCompilingVisitor compilingVisitor) {
    this.myCompilingVisitor = compilingVisitor;
  }

  public void compile(PsiElement @NotNull [] topLevelElements) {
    final CompileContext context = myCompilingVisitor.getContext();
    final CompiledPattern pattern = context.getPattern();
    final MatchOptions options = context.getOptions();
    pattern.setStrategy(new XmlMatchingStrategy(options.getDialect()));
    for (PsiElement element : topLevelElements) {
      element.accept(this);
      optimize(element);
      pattern.setHandler(element, new TopLevelMatchingHandler(pattern.getHandler(element)));
    }
  }

  public void optimize(@NotNull PsiElement element) {
    element.accept(myOptimizer);
  }

  private class XmlWordOptimizer extends XmlRecursiveElementWalkingVisitor implements WordOptimizer {
    @Override
    public void visitXmlTag(@NotNull XmlTag tag) {
      if (!handleWord(tag.getName(), CODE, myCompilingVisitor.getContext())) return;
      super.visitXmlTag(tag);
    }

    @Override
    public void visitXmlAttribute(@NotNull XmlAttribute attribute) {
      if (!handleWord(attribute.getName(), CODE, myCompilingVisitor.getContext())) return;
      handleWord(attribute.getValue(), CODE, myCompilingVisitor.getContext());
      super.visitXmlAttribute(attribute);
    }

    @Override
    public void visitXmlToken(@NotNull XmlToken token) {
      super.visitXmlToken(token);
      final IElementType tokenType = token.getTokenType();
      if (tokenType == XmlTokenType.XML_COMMENT_CHARACTERS ||
          tokenType == XmlTokenType.XML_DATA_CHARACTERS) {
        handleWord(token.getText(), TEXT, myCompilingVisitor.getContext());
      }
    }
  }

  @Override
  public void visitElement(@NotNull PsiElement element) {
    myCompilingVisitor.handle(element);
    super.visitElement(element);
  }

  @Override
  public void visitXmlToken(@NotNull XmlToken token) {
    final IElementType tokenType = token.getTokenType();
    if (tokenType != XmlTokenType.XML_NAME &&
        tokenType != XmlTokenType.XML_COMMENT_CHARACTERS &&
        tokenType != XmlTokenType.XML_DATA_CHARACTERS) {
      return;
    }
    super.visitXmlToken(token);
    if (tokenType == XmlTokenType.XML_DATA_CHARACTERS) {
      myCompilingVisitor.setFilterSimple(token, TagValueFilter.getInstance());
    }
  }

  @Override
  public void visitXmlText(@NotNull XmlText text) {
    super.visitXmlText(text);
    if (myCompilingVisitor.getContext().getPattern().isRealTypedVar(text)) {
      myCompilingVisitor.setFilterSimple(text, TagValueFilter.getInstance());
    }
  }

  @Override
  public void visitXmlTag(@NotNull XmlTag tag) {
    super.visitXmlTag(tag);
    // there are a lot of implementations of XmlTag which we should be able to match
    myCompilingVisitor.setFilterSimple(tag, element -> element instanceof XmlTag);
  }
}
