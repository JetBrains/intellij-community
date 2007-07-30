package org.jetbrains.plugins.groovy.lang.surroundWith.descriptors;

import com.intellij.lang.surroundWith.Surrounder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.GroovySurrounderByParametrizedClosure;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.blocks.open.*;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.*;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.conditions.GroovyWithIfExprSurrounder;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.conditions.GroovyWithWhileExprSurrounder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.05.2007
 */
public class GroovyStmtsSurroundDescriptor extends GroovySurroundDescriptor {
  private static Surrounder[] stmtsSurrounders = new Surrounder[]{
      new GroovyWithWithStatementsSurrounder(),

      new GroovyWithIfSurrounder(),
      new GroovyWithIfElseSurrounder(),

      new GroovyWithTryCatchSurrounder(),
      new GroovyWithTryFinallySurrounder(),
      new GroovyWithTryCatchFinallySurrounder(),

      new GroovyWithWhileSurrounder(),

      new GroovySurrounderByParametrizedClosure(),
  };

      /********** ***********/
  private static Surrounder[] exprSurrounders = new Surrounder[]{
      new GroovyWithBracketsExprSurrounder(),
      new GroovyWithTypeCastSurrounder(),

      new GroovyWithWithExprSurrounder(),

      new GroovyWithIfExprSurrounder(),
      new GroovyWithIfElseExprSurrounder(),

      new GroovyWithWhileExprSurrounder()
  };

  public static Surrounder[] getStmtsSurrounders() {
    return stmtsSurrounders;
  }

  public static Surrounder[] getExprSurrounders() {
    return exprSurrounders;
  }

  @NotNull
  public Surrounder[] getSurrounders() {
    List<Surrounder> surroundersList = new ArrayList<Surrounder>();
    surroundersList.addAll(Arrays.asList(exprSurrounders));
    surroundersList.addAll(Arrays.asList(stmtsSurrounders));
    return surroundersList.toArray(new Surrounder[0]);
  }
}