package org.jetbrains.plugins.groovy.dsl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.Nullable;

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

  public void delegatesTo(String type) {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(myProject);
    final PsiClass clazz = facade.findClass(type, GlobalSearchScope.allScope(myProject));
    if (clazz != null) {
      for (PsiMethod method : clazz.getAllMethods()) {
        myClassText.append(method.getText()).append("\n");
      }
    }
  }

  @Nullable
  public NonCodeMembersHolder getMembersHolder() {
    return myClassText.length() == 0 ? null : new NonCodeMembersHolder(myClassText.toString(), myProject);
  }
}
