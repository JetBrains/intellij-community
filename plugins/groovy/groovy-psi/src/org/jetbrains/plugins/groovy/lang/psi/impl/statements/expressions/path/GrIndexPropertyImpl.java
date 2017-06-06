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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.AtomicNullableLazyValue;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyPolyVariantReference;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyIndexPropertyUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyLValueUtil;
import org.jetbrains.plugins.groovy.lang.typing.GrTypeCalculator;

/**
 * @author ilyas
 */
public class GrIndexPropertyImpl extends GrExpressionImpl implements GrIndexProperty {

  private final NullableLazyValue<GrIndexPropertyReference> myRValueReference = AtomicNullableLazyValue.createValue(
    () -> GroovyLValueUtil.isRValue(this) ? new GrIndexPropertyReference(this, true) : null
  );

  private final NullableLazyValue<GrIndexPropertyReference> myLValueReference = AtomicNullableLazyValue.createValue(
    () -> GroovyLValueUtil.isLValue(this) ? new GrIndexPropertyReference(this, false) : null
  );

  private final NotNullLazyValue<GroovyPolyVariantReference[]> myReferences = AtomicNotNullLazyValue.createValue(() -> {
    GroovyPolyVariantReference lValueReference = getLValueReference();
    GroovyPolyVariantReference rValueReference = getRValueReference();
    if (lValueReference == null && rValueReference == null) {
      return GroovyPolyVariantReference.EMPTY_ARRAY;
    }
    else if (lValueReference == null) {
      return new GroovyPolyVariantReference[]{rValueReference};
    }
    else if (rValueReference == null) {
      return new GroovyPolyVariantReference[]{lValueReference};
    }
    else {
      return new GroovyPolyVariantReference[]{rValueReference, lValueReference};
    }
  });

  @Nullable
  @Override
  public GroovyPolyVariantReference getLValueReference() {
    return myLValueReference.getValue();
  }

  @Nullable
  @Override
  public GroovyPolyVariantReference getRValueReference() {
    return myRValueReference.getValue();
  }

  @NotNull
  @Override
  public GroovyPolyVariantReference[] getReferences() {
    return myReferences.getValue();
  }

  public GrIndexPropertyImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitIndexProperty(this);
  }

  public String toString() {
    return "Property by index";
  }

  @Override
  @NotNull
  public GrExpression getInvokedExpression() {
    return findNotNullChildByClass(GrExpression.class);
  }

  @Override
  @NotNull
  public GrArgumentList getArgumentList() {
    return findNotNullChildByClass(GrArgumentList.class);
  }

  @Override
  public PsiType getType() {
    return TypeInferenceHelper.getCurrentContext().getExpressionType(this, GrTypeCalculator::getTypeFromCalculators);
  }

  @Nullable
  @Override
  public PsiType getNominalType() {
    return GroovyIndexPropertyUtil.getSimpleArrayAccessType(this);
  }
}
