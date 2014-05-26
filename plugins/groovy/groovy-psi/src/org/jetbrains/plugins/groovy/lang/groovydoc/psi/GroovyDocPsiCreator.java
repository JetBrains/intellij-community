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

package org.jetbrains.plugins.groovy.lang.groovydoc.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.groovydoc.parser.GroovyDocElementTypes;
import org.jetbrains.plugins.groovy.lang.groovydoc.parser.elements.GroovyDocTagValueTokenType;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl.*;

/**
 * @author ilyas
 */
public class GroovyDocPsiCreator {

  public static PsiElement createElement(ASTNode node) {
    IElementType type = node.getElementType();

    if (type instanceof GroovyDocTagValueTokenType) {
      GroovyDocTagValueTokenType value = (GroovyDocTagValueTokenType) type;
      GroovyDocTagValueTokenType.TagValueTokenType valueType = GroovyDocTagValueTokenType.getValueType(node);
      if (valueType == GroovyDocTagValueTokenType.TagValueTokenType.REFERENCE_ELEMENT) return new GrDocReferenceElementImpl(node);

      return new GrDocTagValueTokenImpl(node);
    }


    if (type == GroovyDocElementTypes.GDOC_TAG) return new GrDocTagImpl(node);
    if (type == GroovyDocElementTypes.GDOC_INLINED_TAG) return new GrDocInlinedTagImpl(node);

    if (type == GroovyDocElementTypes.GDOC_METHOD_REF) return new GrDocMethodReferenceImpl(node);
    if (type == GroovyDocElementTypes.GDOC_FIELD_REF) return new GrDocFieldReferenceImpl(node);
    if (type == GroovyDocElementTypes.GDOC_PARAM_REF) return new GrDocParameterReferenceImpl(node);
    if (type == GroovyDocElementTypes.GDOC_METHOD_PARAMS) return new GrDocMethodParamsImpl(node);
    if (type == GroovyDocElementTypes.GDOC_METHOD_PARAMETER) return new GrDocMethodParameterImpl(node);

    return new ASTWrapperPsiElement(node);
  }
}
