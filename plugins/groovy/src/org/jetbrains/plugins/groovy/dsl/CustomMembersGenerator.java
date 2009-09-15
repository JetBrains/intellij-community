package org.jetbrains.plugins.groovy.dsl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Function;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.dsl.holders.CompoundMembersHolder;
import org.jetbrains.plugins.groovy.dsl.holders.CustomMembersHolder;
import org.jetbrains.plugins.groovy.dsl.holders.NonCodeMembersHolder;
import org.jetbrains.plugins.groovy.dsl.holders.DelegatedMembersHolder;

import java.util.LinkedHashMap;

/**
 * @author peter, ilyas
 */
public class CustomMembersGenerator implements GroovyEnhancerConsumer{
  private final StringBuilder myClassText = new StringBuilder();
  private final Project myProject;
  private final CompoundMembersHolder myDepot = new CompoundMembersHolder();

  public CustomMembersGenerator(Project project) {
    myProject = project;
  }

  @Nullable
  public CustomMembersHolder getMembersHolder() {
    // Add non-code members holder
    if (myClassText.length() > 0) {
      addMemberHolder(new NonCodeMembersHolder(myClassText.toString(), myProject));
    }

    return myDepot;

  }

  public void addMemberHolder(CustomMembersHolder holder) {
    myDepot.addHolder(holder);
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

  /*************************************************************************************
   Methods and properties of the GroovyDSL language
   ************************************************************************************/

  public void delegatesTo(String type) {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(myProject);
    final PsiClass clazz = facade.findClass(type, GlobalSearchScope.allScope(myProject));
    if (clazz != null) {
      final DelegatedMembersHolder holder = new DelegatedMembersHolder();
      for (PsiMethod method : clazz.getAllMethods()) {
        if (!method.isConstructor()) holder.addMember(method);
      }
      for (PsiField field : clazz.getAllFields()) {
        holder.addMember(field);
      }
      addMemberHolder(holder);
    }
  }

  /**
   * Find a class by its full-qulified name
   * @param fqn
   * @return
   */
  @Nullable
  public PsiClass findClass(String fqn) {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(myProject);
    return facade.findClass(fqn, GlobalSearchScope.allScope(myProject));
  }


  /**
   * Add a member to a context's ctype
   * @param member
   */
  public void add(@NonNls PsiMember member) {
    final DelegatedMembersHolder holder = new DelegatedMembersHolder();
    holder.addMember(member);
    addMemberHolder(holder);
  }

}
