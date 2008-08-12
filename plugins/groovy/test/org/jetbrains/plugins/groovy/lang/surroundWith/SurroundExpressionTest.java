package org.jetbrains.plugins.groovy.lang.surroundWith;

import com.intellij.lang.surroundWith.Surrounder;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.jetbrains.plugins.groovy.lang.surroundWith.descriptors.GroovyStmtsSurroundDescriptor;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.GroovyWithParenthesisExprSurrounder;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.GroovyWithTypeCastSurrounder;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.GroovyWithWithExprSurrounder;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.conditions.GroovyWithIfElseExprSurrounder;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.conditions.GroovyWithIfExprSurrounder;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.conditions.GroovyWithWhileExprSurrounder;
import org.jetbrains.plugins.groovy.util.PathUtil;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * User: Dmitry.Krasilschikov
 * Date: 01.06.2007
 */
public class SurroundExpressionTest extends TestSuite {
  private static Map<String, String> surroundersOfExprToPathsMap = new HashMap<String, String>();

  static {
    {
      String exprPrefix = "/expr" + File.separator;
      surroundersOfExprToPathsMap.put(GroovyWithIfExprSurrounder.class.getCanonicalName(), exprPrefix + "if");
      surroundersOfExprToPathsMap.put(GroovyWithIfElseExprSurrounder.class.getCanonicalName(), exprPrefix + "if_else");
      surroundersOfExprToPathsMap.put(GroovyWithWhileExprSurrounder.class.getCanonicalName(), exprPrefix + "while");
      surroundersOfExprToPathsMap.put(GroovyWithParenthesisExprSurrounder.class.getCanonicalName(), exprPrefix + "brackets");
      surroundersOfExprToPathsMap.put(GroovyWithWithExprSurrounder.class.getCanonicalName(), exprPrefix + "with");
      surroundersOfExprToPathsMap.put(GroovyWithTypeCastSurrounder.class.getCanonicalName(), exprPrefix + "type_cast");
    }
  }

  public SurroundExpressionTest() {
    Surrounder[] surrounders = GroovyStmtsSurroundDescriptor.getExprSurrounders();

    String path;
    for (Surrounder surrounder : surrounders) {
      path = surroundersOfExprToPathsMap.get(surrounder.getClass().getCanonicalName());
      String dataPath = PathUtil.getDataPath(SurroundExpressionTest.class);
      addTest(new SurroundWithTestItem((dataPath.endsWith("/") ? dataPath : dataPath + File.separator) + path, surrounder));
    }
  }

  public static Test suite() {
    return new SurroundExpressionTest();
  }
}