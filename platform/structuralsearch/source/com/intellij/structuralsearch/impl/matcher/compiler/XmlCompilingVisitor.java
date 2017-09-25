/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.psi.PsiElement;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlToken;
import com.intellij.structuralsearch.impl.matcher.filters.TagValueFilter;
import com.intellij.structuralsearch.impl.matcher.handlers.MatchingHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.TopLevelMatchingHandler;

/**
* @author Eugene.Kudelevsky
*/
public class XmlCompilingVisitor extends XmlRecursiveElementVisitor {
  private final GlobalCompilingVisitor myCompilingVisitor;

  public XmlCompilingVisitor(GlobalCompilingVisitor compilingVisitor) {
    this.myCompilingVisitor = compilingVisitor;
  }

  public void compile(PsiElement[] topLevelElements) {
    for (PsiElement element : topLevelElements) {
      element.accept(this);
      final MatchingHandler matchingHandler = myCompilingVisitor.getContext().getPattern().getHandler(element);
      myCompilingVisitor.getContext().getPattern().setHandler(element, new TopLevelMatchingHandler(matchingHandler));
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
