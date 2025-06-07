// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.groovydoc.parser;

import com.intellij.lang.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.ILazyParseableElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocElementType;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocLexer;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl.GrDocCommentImpl;

public interface GroovyDocElementTypes {

  /**
   * GroovyDoc comment
   */
  ILazyParseableElementType GROOVY_DOC_COMMENT = new ILazyParseableElementType("GrDocComment") {
    @Override
    public @NotNull Language getLanguage() {
      return GroovyLanguage.INSTANCE;
    }

    @Override
    public ASTNode parseContents(@NotNull ASTNode chameleon) {
      final PsiElement parentElement = chameleon.getTreeParent().getPsi();
      final Project project = JavaPsiFacade.getInstance(parentElement.getProject()).getProject();

      final PsiBuilder builder = PsiBuilderFactory.getInstance().createBuilder(project, chameleon, new GroovyDocLexer(), getLanguage(), chameleon.getText());
      final PsiParser parser = new GroovyDocParser();

      return parser.parse(this, builder).getFirstChildNode();
    }

    @Override
    public ASTNode createNode(CharSequence text) {
      return new GrDocCommentImpl(text);
    }
  };

  GroovyDocElementType GDOC_TAG = new GroovyDocElementType("GroovyDocTag");
  GroovyDocElementType GDOC_INLINED_TAG = new GroovyDocElementType("GroovyDocInlinedTag");

  GroovyDocElementType GDOC_REFERENCE_ELEMENT = new GroovyDocElementType("GroovyDocReferenceElement");
  GroovyDocElementType GDOC_PARAM_REF = new GroovyDocElementType("GroovyDocParameterReference");
  GroovyDocElementType GDOC_METHOD_REF = new GroovyDocElementType("GroovyDocMethodReference");
  GroovyDocElementType GDOC_FIELD_REF = new GroovyDocElementType("GroovyDocFieldReference");
  GroovyDocElementType GDOC_METHOD_PARAMS = new GroovyDocElementType("GroovyDocMethodParameterList");
  GroovyDocElementType GDOC_METHOD_PARAMETER = new GroovyDocElementType("GroovyDocMethodParameter");
}
