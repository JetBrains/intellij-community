// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
public final class GroovyDocPsiCreator {

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
