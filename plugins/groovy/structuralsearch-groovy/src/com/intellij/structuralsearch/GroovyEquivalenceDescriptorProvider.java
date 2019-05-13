package com.intellij.structuralsearch;

import com.intellij.dupLocator.equivalence.EquivalenceDescriptor;
import com.intellij.dupLocator.equivalence.EquivalenceDescriptorBuilder;
import com.intellij.dupLocator.equivalence.EquivalenceDescriptorProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;

/**
 * @author Eugene.Kudelevsky
 */
public class GroovyEquivalenceDescriptorProvider extends EquivalenceDescriptorProvider {
  private static final TokenSet IGNORED_TOKENS = TokenSet.orSet(TokenSets.WHITE_SPACES_OR_COMMENTS, TokenSets.SEPARATORS);

  @Override
  public boolean isMyContext(@NotNull PsiElement context) {
    return context.getLanguage().isKindOf(GroovyLanguage.INSTANCE);
  }

  @Override
  public EquivalenceDescriptor buildDescriptor(@NotNull PsiElement e) {
    final EquivalenceDescriptorBuilder builder = new EquivalenceDescriptorBuilder();

    if (e instanceof GrVariableDeclaration) {
      return builder.elements(((GrVariableDeclaration)e).getVariables());
    }
    else if (e instanceof GrParameter) {
      final GrParameter p = (GrParameter)e;
      return builder
        .element(p.getNameIdentifierGroovy())
        .optionally(p.getTypeElementGroovy())
        .optionallyInPattern(p.getInitializerGroovy());
    }
    else if (e instanceof GrVariable) {
      final GrVariable v = (GrVariable)e;
      return builder
        .element(v.getNameIdentifierGroovy())
        .optionally(v.getTypeElementGroovy())
        .optionallyInPattern(v.getInitializerGroovy());
    }
    else if (e instanceof GrMethod) {
      final GrMethod m = (GrMethod)e;
      return builder
        .element(m.getNameIdentifierGroovy())
        .elements(m.getParameters())
        .optionally(m.getReturnTypeElementGroovy())
        .optionallyInPattern(m.getBlock());
    }
    else if (e instanceof GrTypeDefinitionBody) {
      final GrTypeDefinitionBody b = (GrTypeDefinitionBody)e;
      return builder
        .inAnyOrder(b.getFields())
        .inAnyOrder(b.getMethods())
        .inAnyOrder(b.getInitializers())
        .inAnyOrder(b.getInnerClasses());
    }
    else if (e instanceof GrTypeDefinition) {
      GrTypeDefinition d = (GrTypeDefinition)e;
      return builder.element(d.getNameIdentifierGroovy())
        .optionallyInPattern(d.getExtendsClause())
        .optionallyInPattern(d.getImplementsClause())
        .optionallyInPattern(d.getBody());
    }
    else if (e instanceof GrForInClause) {
      final GrForInClause f = (GrForInClause)e;
      return builder
        .element(f.getDeclaredVariable())
        .element(f.getIteratedExpression());
    }
    else if (e instanceof GrReferenceList) {
      return builder.inAnyOrder(((GrReferenceList)e).getReferenceElementsGroovy());
    }
    else if (e instanceof GrCodeBlock) {
      return builder.codeBlock(((GrStatementOwner)e).getStatements());
    }

    // todo: support 'object method()' <-> 'object.method()'

    return null;
  }

  @Override
  public TokenSet getIgnoredTokens() {
    return IGNORED_TOKENS;
  }
}
