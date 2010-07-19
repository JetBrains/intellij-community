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
package org.jetbrains.plugins.groovy.lang.surroundWith.descriptors;

import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.GroovySurrounderByClosure;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.blocks.open.*;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.GroovyWithParenthesisExprSurrounder;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.GroovyWithTypeCastSurrounder;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.GroovyWithWithExprSurrounder;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.conditions.GroovyWithIfElseExprSurrounder;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.conditions.GroovyWithIfExprSurrounder;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.conditions.GroovyWithWhileExprSurrounder;

import java.util.ArrayList;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.05.2007
 */
public class GroovyStmtsSurroundDescriptor extends GroovySurroundDescriptor {
  private static final Surrounder[] stmtsSurrounders = new Surrounder[]{
      new GroovyWithWithStatementsSurrounder(),

      new GroovyWithIfSurrounder(),
      new GroovyWithIfElseSurrounder(),

      new GroovyWithTryCatchSurrounder(),
      new GroovyWithTryFinallySurrounder(),
      new GroovyWithTryCatchFinallySurrounder(),

      new GroovyWithWhileSurrounder(),

      new GroovySurrounderByClosure(),
      new GroovyWithShouldFailWithTypeStatementsSurrounder(),
  };

      /********** ***********/
  private static final Surrounder[] exprSurrounders = new Surrounder[]{
      new GroovyWithParenthesisExprSurrounder(),
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
    ContainerUtil.addAll(surroundersList, exprSurrounders);
    ContainerUtil.addAll(surroundersList, stmtsSurrounders);
    return surroundersList.toArray(new Surrounder[0]);
  }
}
