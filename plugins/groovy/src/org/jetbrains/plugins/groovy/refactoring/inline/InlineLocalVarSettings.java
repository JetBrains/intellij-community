/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.inline;

import com.intellij.lang.refactoring.InlineHandler;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import java.util.Collection;

/**
 * @author Max Medvedev
 */
public class InlineLocalVarSettings implements InlineHandler.Settings {
  private GrExpression myInitializer;
  private Collection<GrReferenceExpression> myRefs;
  private boolean myRemoveDeclaration;

  public InlineLocalVarSettings(GrExpression initializer, Collection<GrReferenceExpression> refs, boolean removeDeclaration) {
    myInitializer = initializer;

    myRefs = refs;
    myRemoveDeclaration = removeDeclaration;
  }

  @Override
  public boolean isOnlyOneReferenceToInline() {
    return false;
  }

  public GrExpression getInitializer() {
    return myInitializer;
  }


  public Collection<? extends PsiReference> getRefs() {
    return myRefs;
  }

  public boolean isRemoveDeclaration() {
    return myRemoveDeclaration;
  }
}
