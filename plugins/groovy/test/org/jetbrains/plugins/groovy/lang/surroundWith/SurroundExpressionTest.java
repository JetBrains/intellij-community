package org.jetbrains.plugins.groovy.lang.surroundWith;

import com.intellij.lang.surroundWith.Surrounder;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.jetbrains.plugins.groovy.lang.surroundWith.descriptors.GroovyStmtsSurroundDescriptor;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.*;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.conditions.GroovyWithIfExprSurrounder;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.conditions.GroovyWithWhileExprSurrounder;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * User: Dmitry.Krasilschikov
 * Date: 01.06.2007
 */
public class SurroundExpressionTest extends TestSuite {
  protected static final String DATA_PATH = "test/org/jetbrains/plugins/groovy/lang/surroundWith/data/";

  private static Map<String, String> surroundersOfExprToPathsMap = new HashMap<String, String>();

  static {
    {
      String exprPrefix = "expr" + File.separator;
      surroundersOfExprToPathsMap.put(GroovyWithIfExprSurrounder.class.getCanonicalName(), exprPrefix + "if");
      surroundersOfExprToPathsMap.put(GroovyWithIfElseExprSurrounder.class.getCanonicalName(), exprPrefix + "if_else");
      surroundersOfExprToPathsMap.put(GroovyWithWhileExprSurrounder.class.getCanonicalName(), exprPrefix + "while");
      surroundersOfExprToPathsMap.put(GroovyWithBracketsExprSurrounder.class.getCanonicalName(), exprPrefix + "brackets");
      surroundersOfExprToPathsMap.put(GroovyWithWithExprSurrounder.class.getCanonicalName(), exprPrefix + "with");
      surroundersOfExprToPathsMap.put(GroovyWithTypeCastSurrounder.class.getCanonicalName(), exprPrefix + "type_cast");
    }
  }

  public SurroundExpressionTest() {
    Surrounder[] surrounders = GroovyStmtsSurroundDescriptor.getExprSurrounders();

    String path;
    for (Surrounder surrounder : surrounders) {
      path = surroundersOfExprToPathsMap.get(surrounder.getClass().getCanonicalName());
      addTest(new SurroundWithTestExpr((DATA_PATH.endsWith("/") ? DATA_PATH : DATA_PATH + File.separator) + path, surrounder));
    }
  }

  public static Test suite() {
    return new SurroundExpressionTest();
  }
}