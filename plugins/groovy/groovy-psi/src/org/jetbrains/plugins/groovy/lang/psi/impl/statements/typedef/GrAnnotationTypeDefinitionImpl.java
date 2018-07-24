// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef;

import com.intellij.lang.ASTNode;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClassType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnnotationTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrTypeDefinitionStub;

/**
 * @author Dmitry.Krasilschikov
 * @date 18.03.2007
 */
public class GrAnnotationTypeDefinitionImpl extends GrTypeDefinitionImpl implements GrAnnotationTypeDefinition {
  public GrAnnotationTypeDefinitionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrAnnotationTypeDefinitionImpl(GrTypeDefinitionStub stub) {
    super(stub, GroovyElementTypes.ANNOTATION_DEFINITION);
  }

  public String toString() {
    return "Annotation definition";
  }

  @Override
  public boolean isAnnotationType() {
    return true;
  }

  @Override
  public boolean isInterface() {
    return true;
  }

  @Override
  @NotNull
  public PsiClassType[] getExtendsListTypes(boolean includeSynthetic) {
    return new PsiClassType[]{createAnnotationType()};
  }

  private PsiClassType createAnnotationType() {
    return TypesUtil.createTypeByFQClassName(CommonClassNames.JAVA_LANG_ANNOTATION_ANNOTATION, this);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitAnnotationTypeDefinition(this);
  }
}
