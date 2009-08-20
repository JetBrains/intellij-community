package org.jetbrains.plugins.gant.util;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.lang.ASTNode;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

import java.util.ArrayList;

/**
 * @author ilyas
 */
public class GantUtils {

  private GantUtils() {
  }

  public static boolean isGantScriptFile(PsiFile file) {
    if (file instanceof GroovyFile) {
      GroovyFile groovyFile = (GroovyFile)file;
      if (!groovyFile.isScript()) return false;
      String name = file.getName();
      return name.endsWith(GantScriptType.DEFAULT_EXTENSION);
    }
    return false;
  }

  public static GrArgumentLabel[] getScriptTargets(GroovyFile file) {
    ArrayList<GrArgumentLabel> labels = new ArrayList<GrArgumentLabel>();
    for (PsiElement child : file.getChildren()) {
      if (child instanceof GrMethodCallExpression) {
        GrMethodCallExpression call = (GrMethodCallExpression)child;
        GrNamedArgument[] arguments = call.getNamedArguments();
        if (arguments.length == 1) {
          GrArgumentLabel label = arguments[0].getLabel();
          if (label != null && isPlainIdentifier(label)) {
            labels.add(label);
          }
        }
      }
    }
    return labels.toArray(new GrArgumentLabel[labels.size()]);
  }

  public static boolean isPlainIdentifier(final GrArgumentLabel label) {
    final PsiElement elem = label.getNameElement();
    final ASTNode node = elem.getNode();
    if (node == null) return false;
    return node.getElementType() == GroovyTokenTypes.mIDENT;
  }

}
