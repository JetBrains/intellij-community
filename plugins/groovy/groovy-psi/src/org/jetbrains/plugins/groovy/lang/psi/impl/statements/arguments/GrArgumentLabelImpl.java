/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.arguments;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.extensions.GroovyNamedArgumentProvider;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentUtilKt;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.Map;

/**
 * @author ilyas
 */
public class GrArgumentLabelImpl extends GroovyPsiElementImpl implements GrArgumentLabel {

  public GrArgumentLabelImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitArgumentLabel(this);
  }

  public String toString() {
    return "Argument label";
  }

  @Nullable
  private PsiPolyVariantReference getReferenceFromNamedArgumentProviders() {
    PsiElement namedArgument = getParent();
    if (!(namedArgument instanceof GrNamedArgument)) return null;

    PsiElement nameElement = getNameElement();
    if (!(nameElement instanceof LeafPsiElement)) return null;

    IElementType elementType = ((LeafPsiElement)nameElement).getElementType();
    if (elementType != GroovyTokenTypes.mIDENT && !CommonClassNames.JAVA_LANG_STRING.equals(TypesUtil.getBoxedTypeName(elementType))) {
      return null;
    }

    GrCall call = PsiUtil.getCallByNamedParameter((GrNamedArgument)namedArgument);
    if (call == null) return NamedArgumentUtilKt.getReferenceFromDescriptor(this);

    String labelName = getName();

    Map<String,NamedArgumentDescriptor> providers = GroovyNamedArgumentProvider.getNamedArgumentsFromAllProviders(call, labelName, false);
    if (providers != null) {
      NamedArgumentDescriptor descr = providers.get(labelName);
      if (descr != null) {
        PsiPolyVariantReference res = descr.createReference(this);
        if (res != null) {
          return res;
        }
      }
    }

    return null;
  }

  @NotNull
  private PsiPolyVariantReference getRealReference() {
    PsiReference[] otherReferences = ReferenceProvidersRegistry.getReferencesFromProviders(this, PsiReferenceService.Hints.NO_HINTS);
    PsiPolyVariantReference reference = getReferenceFromNamedArgumentProviders();

    if (otherReferences.length == 0) {
      if (reference != null) {
        return reference;
      }
      else {
        return new PsiPolyVariantReferenceBase<PsiElement>(this) {

          @NotNull
          @Override
          public Object[] getVariants() {
            return ArrayUtil.EMPTY_OBJECT_ARRAY;
          }

          @NotNull
          @Override
          public ResolveResult[] multiResolve(boolean incompleteCode) {
            return ResolveResult.EMPTY_ARRAY;
          }
        };
      }
    }
    else {
      if (reference != null) {
        PsiReference[] refs = new PsiReference[otherReferences.length + 1];
        refs[0] = reference;
        //noinspection ManualArrayCopy
        for (int i = 0; i < otherReferences.length; i++) {
          refs[i + 1] = otherReferences[i];
        }

        otherReferences = refs;
      }

      return new PsiMultiReference(otherReferences, this);
    }
  }

  @Override
  public PsiReference getReference() {
    final PsiElement name = getNameElement();
    return name instanceof GrLiteral || name instanceof LeafPsiElement ? this : null;
  }

  @Override
  @Nullable
  public String getName() {
    final PsiElement element = getNameElement();
    if (element instanceof GrExpression) {
      final Object value = JavaPsiFacade.getInstance(getProject()).getConstantEvaluationHelper().computeConstantExpression(element);
      if (value instanceof String) {
        return (String)value;
      }
    }

    final IElementType elemType = element.getNode().getElementType();
    if (GroovyTokenTypes.mIDENT == elemType || TokenSets.KEYWORDS.contains(elemType)) {
      return element.getText();
    }

    return convertToString(GrLiteralImpl.getLiteralValue(element));
  }

  private static String convertToString(Object value) {
    if (value instanceof String) {
      return (String)value;
    }

    if (value instanceof Number) {
      return value.toString();
    }
    return null;
  }

  @Override
  public PsiElement getElement() {
    return this;
  }

  @Override
  public TextRange getRangeInElement() {
    return new TextRange(0, getTextLength());
  }

  @Override
  @Nullable
  public PsiElement resolve() {
    return advancedResolve().getElement();
  }

  @NotNull
  @Override
  public GroovyResolveResult[] multiResolve(boolean incompleteCode) {
    final ResolveResult[] results = getRealReference().multiResolve(incompleteCode);
    if (results instanceof GroovyResolveResult[]) {
      return (GroovyResolveResult[])results;
    }
    else {
      final GroovyResolveResult[] results1 = new GroovyResolveResult[results.length];
      for (int i = 0; i < results.length; i++) {
        ResolveResult result = results[i];
        final PsiElement element = result.getElement();
        if (element == null) {
          results1[i] = GroovyResolveResult.EMPTY_RESULT;
        }
        else {
          results1[i] = new GroovyResolveResultImpl(element, true);
        }
      }
      return results1;
    }
  }

  @NotNull
  @Override
  public GroovyResolveResult advancedResolve() {
    return PsiImplUtil.extractUniqueResult(multiResolve(false));
  }

  @Override
  @NotNull
  public String getCanonicalText() {
    PsiElement resolved = resolve();
    if (resolved instanceof PsiMember && resolved instanceof PsiNamedElement) {
      PsiClass clazz = ((PsiMember) resolved).getContainingClass();
      if (clazz != null) {
        String qName = clazz.getQualifiedName();
        if (qName != null) {
          return qName + "." + ((PsiNamedElement) resolved).getName();
        }
      }
    }

    return getText();
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return getRealReference().handleElementRename(newElementName);
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return getRealReference().bindToElement(element);
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    return getRealReference().isReferenceTo(element);
  }

  @Override
  @NotNull
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  @Override
  @NotNull
  public PsiElement getNameElement() {
    final PsiElement element = getFirstChild();
    assert element != null;
    return element;
  }

  @Override
  public GrExpression getExpression() {
    final PsiElement nameElement = getNameElement();
    if (nameElement instanceof GrParenthesizedExpression) return ((GrParenthesizedExpression)nameElement).getOperand();
    if (nameElement instanceof GrExpression) return (GrExpression)nameElement;
    return null;
  }

  @Override
  @Nullable
  public PsiType getExpectedArgumentType() { // TODO use GroovyNamedArgumentProvider to determinate expected argument type.
    return null;
  }

  @Override
  public PsiType getLabelType() {
    PsiElement el = getNameElement();
    if (el instanceof GrParenthesizedExpression) {
      return ((GrParenthesizedExpression)el).getType();
    }

    final ASTNode node = el.getNode();
    if (node == null) {
      return null;
    }

    PsiType nodeType = TypesUtil.getPsiType(el, node.getElementType());
    if (nodeType != null) {
      return nodeType;
    }
    return TypesUtil.createType(CommonClassNames.JAVA_LANG_STRING, this);
  }

  @Override
  public GrNamedArgument getNamedArgument() {
    final PsiElement parent = getParent();
    assert parent instanceof GrNamedArgument;
    return (GrNamedArgument)parent;
  }

  @Override
  public PsiElement setName(@NotNull String newName) {
    PsiImplUtil.setName(newName, getNameElement());
    return this;
  }
}
