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
package org.jetbrains.plugins.groovy.lang.psi.api.statements;

import com.intellij.psi.PsiField;
import com.intellij.psi.StubBasedPsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocCommentOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrFieldStub;

import java.util.Set;

/**
 * @author ven
 */
public interface GrField extends GrVariable, GrMember, PsiField, GrTopLevelDefintion, StubBasedPsiElement<GrFieldStub>, GrDocCommentOwner {
  GrField[] EMPTY_ARRAY = new GrField[0];

  boolean isProperty();

  @Nullable
  GrAccessorMethod getSetter();

  @NotNull
  GrAccessorMethod[] getGetters();

  @NotNull
  String[] getNamedParametersArray();
}
