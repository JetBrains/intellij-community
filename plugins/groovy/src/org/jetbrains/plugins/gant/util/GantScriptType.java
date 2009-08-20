package org.jetbrains.plugins.gant.util;

import com.intellij.execution.Location;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.gant.GantIcons;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.runner.AbstractGroovyScriptRunConfiguration;

import javax.swing.*;

/**
 * @author ilyas
 */
public class GantScriptType extends GroovyScriptType {
  @NonNls public static final String DEFAULT_EXTENSION = "gant";

  public boolean isSpecificScriptFile(final GroovyFile file) {
    return GantUtils.isGantScriptFile(file);
  }

  @NotNull
  public Icon getScriptIcon() {
    return GantIcons.GANT_ICON_16x16;
  }

  @Override
  public void tuneConfiguration(@NotNull GroovyFile file, @NotNull AbstractGroovyScriptRunConfiguration configuration, Location location) {
    final PsiElement element = location.getPsiElement();
    PsiElement pp = element.getParent();
    PsiElement parent = element;
    while (!(pp instanceof PsiFile) && pp != null) {
      pp = pp.getParent();
      parent = parent.getParent();
    }
    if (pp != null && parent instanceof GrMethodCallExpression) {
      String target = getFoundTargetName(((GrMethodCallExpression)parent));
      if (target != null) {
        configuration.scriptParams = target;
        configuration.setName(configuration.getName() + "." + target);
      }
    }
  }

  @Nullable
  private static String getFoundTargetName(final GrMethodCallExpression call) {
    final GrNamedArgument[] args = call.getNamedArguments();
    if (args.length == 1) {
      final GrArgumentLabel label = args[0].getLabel();
      if (label != null && GantUtils.isPlainIdentifier(label)) {
        return label.getName();
      }
    }
    return null;
  }

}
