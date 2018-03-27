/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.groovydoc.parser;

import com.intellij.lang.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.ILazyParseableElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocElementType;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocElementTypeImpl;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocLexer;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl.GrDocCommentImpl;

/**
 * @author ilyas
 */
public interface GroovyDocElementTypes {

  /**
   * GroovyDoc comment
   */
  ILazyParseableElementType GROOVY_DOC_COMMENT = new ILazyParseableElementType("GrDocComment") {
    @Override
    @NotNull
    public Language getLanguage() {
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

  GroovyDocElementType GDOC_TAG = new GroovyDocElementTypeImpl("GroovyDocTag");
  GroovyDocElementType GDOC_INLINED_TAG = new GroovyDocElementTypeImpl("GroovyDocInlinedTag");

  GroovyDocElementType GDOC_REFERENCE_ELEMENT = new GroovyDocElementTypeImpl("GroovyDocReferenceElement");
  GroovyDocElementType GDOC_PARAM_REF = new GroovyDocElementTypeImpl("GroovyDocParameterReference");
  GroovyDocElementType GDOC_METHOD_REF = new GroovyDocElementTypeImpl("GroovyDocMethodReference");
  GroovyDocElementType GDOC_FIELD_REF = new GroovyDocElementTypeImpl("GroovyDocFieldReference");
  GroovyDocElementType GDOC_METHOD_PARAMS = new GroovyDocElementTypeImpl("GroovyDocMethodParameterList");
  GroovyDocElementType GDOC_METHOD_PARAMETER = new GroovyDocElementTypeImpl("GroovyDocMethodParameter");
}
