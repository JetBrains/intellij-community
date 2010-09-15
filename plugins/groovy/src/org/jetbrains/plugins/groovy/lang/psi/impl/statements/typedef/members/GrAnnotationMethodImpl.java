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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members;

import com.intellij.lang.ASTNode;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAnnotationMethod;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrAnnotationMethodStub;

import java.util.Collections;
import java.util.Set;

/**
 * User: Dmitry.Krasilschikov
 * Date: 04.06.2007
 */
public class GrAnnotationMethodImpl extends GrMethodBaseImpl<GrAnnotationMethodStub>
    implements GrAnnotationMethod, StubBasedPsiElement<GrAnnotationMethodStub> {

  public GrAnnotationMethodImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrAnnotationMethodImpl(final GrAnnotationMethodStub stub) {
    super(stub, GroovyElementTypes.ANNOTATION_METHOD);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitDefaultAnnotationMember(this);
  }

  public String toString() {
    return "Default annotation member";
  }

  @NotNull
  public String[] getNamedParametersArray() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @NotNull
  @Override
  public String getName() {
    final GrAnnotationMethodStub stub = getStub();
    if (stub != null) {
      return stub.getName();
    }
    return super.getName();
  }
}
