package org.jetbrains.plugins.groovy.spoc;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLabeledStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic.GrShiftExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.bitwise.GrBitwiseExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.util.LightCacheKey;

import java.util.*;

/**
 * @author Sergey Evdokimov
 */
public class SpocUtils {

  public static final String SPEC_CLASS_NAME = "spock.lang.Specification";

  private static final LightCacheKey<Map<String, SpocVariableDescriptor>> KEY = LightCacheKey.create();

  private SpocUtils() {

  }

  public static Map<String, SpocVariableDescriptor> getVariableMap(@NotNull GrMethod method) {
    Map<String, SpocVariableDescriptor> cachedValue = KEY.getCachedValue(method);
    if (cachedValue == null) {
      cachedValue = createVariableMap(method);

      KEY.putCachedValue(method, cachedValue);
    }

    return cachedValue;
  }

  // See org.spockframework.compiler.WhereBlockRewriter
  public static Map<String, SpocVariableDescriptor> createVariableMap(GrMethod method) {
    GrOpenBlock block = method.getBlock();
    if (block == null) return Collections.emptyMap();

    PsiElement elementUnderLabel = null;
    PsiElement elementAfterLabel = null;

    main:
    for (PsiElement e = block.getFirstChild(); e != null; e = e.getNextSibling()) {
      if (e instanceof GrLabeledStatement) {
        GrLabeledStatement l = (GrLabeledStatement)e;

        elementAfterLabel = l.getNextSibling();

        while (true) {
          GrStatement statement = l.getStatement();

          if ("where".equals(l.getLabelName())) {
            elementUnderLabel = statement;
            break main;
          }

          if (statement instanceof GrLabeledStatement) {
            l = (GrLabeledStatement)statement;
            continue;
          }

          break;
        }
      }
    }

    if (elementUnderLabel == null) return Collections.emptyMap();

    Map<String, SpocVariableDescriptor> res = new HashMap<String, SpocVariableDescriptor>();

    PsiElement e = elementUnderLabel;

    while (e != null) {
      if (e instanceof GrShiftExpressionImpl) {
        GrShiftExpressionImpl shift = (GrShiftExpressionImpl)e;

        if (shift.getOperationTokenType() == GroovyElementTypes.COMPOSITE_LSHIFT_SIGN) {
          GrExpression leftOperand = shift.getLeftOperand();
          GrExpression rightOperand = shift.getRightOperand();

          if (leftOperand instanceof GrReferenceExpression) {
            String name = getNameByReference(leftOperand);
            if (name != null) {
              SpocVariableDescriptor descriptor = new SpocVariableDescriptor(leftOperand, name);
              descriptor.addExpressionOfCollection(rightOperand);
              res.put(name, descriptor);
            }
          }
          else if (leftOperand instanceof GrListOrMap) {
            GrExpression[] variableDefinitions = ((GrListOrMap)leftOperand).getInitializers();

            SpocVariableDescriptor[] variables = createVariables(method, res, Arrays.asList(variableDefinitions));

            if (rightOperand instanceof GrListOrMap) {
              for (GrExpression expression : ((GrListOrMap)rightOperand).getInitializers()) {
                if (expression instanceof GrListOrMap) {
                  add(variables, Arrays.asList(((GrListOrMap)expression).getInitializers()));
                }
                else {
                  for (SpocVariableDescriptor variable : variables) {
                    if (variable != null) {
                      variable.addExpressionOfCollection(expression);
                    }
                  }
                }
              }
            }
          }
        }
      }
      else if (e instanceof GrAssignmentExpression) {
        GrAssignmentExpression assExpr = (GrAssignmentExpression)e;
        GrExpression lValue = assExpr.getLValue();
        String name = getNameByReference(lValue);
        if (name != null) {
          res.put(name, new SpocVariableDescriptor(lValue, name).addExpression(assExpr.getRValue()));
        }
      }
      else if (isOrStatement(e)) {
        // See org.spockframework.compiler.WhereBlockRewriter#rewriteTableLikeParameterization()
        List<GrExpression> variableDefinitions = new ArrayList<GrExpression>();
        splitOr(variableDefinitions, (GrExpression)e);

        SpocVariableDescriptor[] variables = createVariables(method, res, variableDefinitions);

        List<GrExpression> row = new ArrayList<GrExpression>();

        PsiElement rowElement = getNext(e, elementUnderLabel,elementAfterLabel);
        while (isOrStatement(rowElement)) {
          row.clear();
          splitOr(row, (GrExpression)rowElement);

          add(variables, row);

          rowElement = getNext(rowElement, elementUnderLabel,elementAfterLabel);
        }

        e = rowElement;
        continue;
      }

      e = getNext(e, elementUnderLabel, elementAfterLabel);
    }

    return res;
  }

  private static SpocVariableDescriptor[] createVariables(GrMethod method, Map<String, SpocVariableDescriptor> map, List<GrExpression> variableDefinitions) {
    SpocVariableDescriptor[] variables = new SpocVariableDescriptor[variableDefinitions.size()];

    for (int i = 0; i < variableDefinitions.size(); i++) {
      GrExpression expression = variableDefinitions.get(i);
      String name = getNameByReference(expression);
      if (name == null) continue;

      SpocVariableDescriptor variableDescriptor = new SpocVariableDescriptor(expression, name);
      map.put(name, variableDescriptor);
      variables[i] = variableDescriptor;
    }

    return variables;
  }

  private static void add(SpocVariableDescriptor[] variables, List<GrExpression> expressions) {
    for (int i = 0, end = Math.min(variables.length, expressions.size()); i < end; i++) {
      if (variables[i] != null) { // variables[i] can be null.
        variables[i].addExpression(expressions.get(i));
      }
    }
  }

  private static boolean isOrStatement(PsiElement element) {
    return element instanceof GrBitwiseExpressionImpl
           && ((GrBitwiseExpressionImpl)element).getOperationTokenType() == GroovyTokenTypes.mBOR;
  }

  @Nullable
  private static PsiElement getNext(@NotNull PsiElement current, PsiElement elementUnderLabel, PsiElement elementAfterLabel) {
    PsiElement e = current;

    do {
      if (e == elementUnderLabel) {
        e = elementAfterLabel;
      }
      else {
        e = e.getNextSibling();
      }
    } while (PsiUtil.isLeafElementOfType(e, TokenSets.WHITE_SPACES_OR_COMMENTS));

    if (e instanceof GrLabeledStatement) return null;

    return e;
  }

  @Nullable
  public static String getNameByReference(@Nullable PsiElement expression) {
    if (!(expression instanceof GrReferenceExpression)) return null;

    PsiElement firstChild = expression.getFirstChild();
    if (firstChild != expression.getLastChild() || !PsiUtil.isLeafElementOfType(firstChild, GroovyTokenTypes.mIDENT)) return null;

    GrReferenceExpression ref = (GrReferenceExpression)expression;
    if (ref.isQualified()) return null;

    return ref.getName();
  }

  private static void splitOr(List<GrExpression> res, GrExpression element) {
    GrExpression e = element;

    while (true) {
      if (e instanceof GrBitwiseExpressionImpl) {
        GrBitwiseExpressionImpl be = (GrBitwiseExpressionImpl)e;
        if (be.getOperationTokenType() == GroovyTokenTypes.mBOR) {
          res.add(be.getRightOperand());
          e = be.getLeftOperand();
          continue;
        }
      }

      res.add(e);
      break;
    }

    Collections.reverse(res);
  }
}
