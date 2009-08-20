package org.jetbrains.plugins.groovy.dsl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.Function;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;

import java.util.LinkedHashMap;

/**
 * @author peter
 */
public class NonCodeMembersGenerator implements GroovyEnhancerConsumer{
  private final StringBuilder myClassText = new StringBuilder();
  private final Project myProject;

  public NonCodeMembersGenerator(Project project) {
    myProject = project;
  }

  public void property(String name, String type) {
    myClassText.append("def ").append(type).append(" ").append(name).append("\n");
  }

  public void method(String name, String type, final LinkedHashMap<String, String> parameters) {
    myClassText.append("def ").append(type).append(" ").append(name).append("(");
    myClassText.append(StringUtil.join(parameters.keySet(), new Function<String, String>() {
      public String fun(String s) {
        return parameters.get(s) + " " + s;
      }
    }, ", "));

    myClassText.append(") {}\n");
  }

  public boolean processGeneratedMembers(PsiScopeProcessor processor) {
    if (myClassText.length() == 0) {
      return true;
    }

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(myProject);
    final String text = "class GroovyEnhanced {\n" + myClassText + "}";
    PsiClass psiClass = factory.createGroovyFile(text, false, null).getTypeDefinitions()[0];

    final NameHint nameHint = processor.getHint(NameHint.KEY);
    final String expectedName = nameHint == null ? null : nameHint.getName(ResolveState.initial());

    for (PsiMethod method : psiClass.getMethods()) {
      if ((expectedName == null || expectedName.equals(method.getName())) && !processor.execute(method, ResolveState.initial())) {
        return false;
      }
    }
    for (final PsiField field : psiClass.getFields()) {
      if ((expectedName == null || expectedName.equals(field.getName())) && !processor.execute(field, ResolveState.initial())) return false;
    }
    return true;
  }
}
