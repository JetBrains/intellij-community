package org.jetbrains.plugins.groovy.dsl;


import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Function
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.groovy.dsl.dsltop.GdslMembersProvider
import org.jetbrains.plugins.groovy.dsl.holders.CompoundMembersHolder
import org.jetbrains.plugins.groovy.dsl.holders.CustomMembersHolder
import org.jetbrains.plugins.groovy.dsl.holders.NonCodeMembersHolder
import org.jetbrains.plugins.groovy.refactoring.GroovyNamesUtil

/**
 * @author peter, ilyas
 */
public class CustomMembersGenerator implements GdslMembersHolderConsumer {
  private final StringBuilder myClassText = new StringBuilder();
  private final Project myProject;
  private final PsiElement myPlace;
  private final String myQualifiedName;
  private final CompoundMembersHolder myDepot = new CompoundMembersHolder();

  public CustomMembersGenerator(Project project, PsiElement place, String qualifiedName) {
    myProject = project;
    myPlace = place;
    myQualifiedName = qualifiedName;
  }

  private Object[] constructNewArgs(Object args) {
    final def newArgs = new Object[args.length + 1]
    for (int i = 0; i < args.length; i++) {
      newArgs[i] = args[i]
    }
    newArgs[args.length] = this
    return newArgs
  }

  def methodMissing(String name, args) {
    final def newArgs = constructNewArgs(args)

    // Get other DSL methods from extensions
    for (d in GdslMembersProvider.EP_NAME.getExtensions()) {
      final def variants = d.metaClass.respondsTo(d, name, newArgs)
      if (variants.size() == 1) {

/*        def cachedMethod = { Object[] args1 ->
          final def newArgs1 = constructNewArgs(args1)
          return d.invokeMethod(name, newArgs1)
        }

        // Cache method
        this.class.metaClass."$name" = cachedMethod

        return cachedMethod(args)*/
        return d.invokeMethod(name, newArgs)
      }
    }
    return null
  }

  public PsiElement getPlace() {
    return myPlace
  }

  public PsiClass getClassType() {
    final def facade = JavaPsiFacade.getInstance(myProject)
    return facade.findClass(myQualifiedName, GlobalSearchScope.allScope(myProject))
  }


  @Nullable
  public CustomMembersHolder getMembersHolder() {
    // Add non-code members holder
    if (myClassText.length() > 0) {
      addMemberHolder(NonCodeMembersHolder.fromText(myClassText.toString(), myPlace.containingFile));
    }
    return myDepot;
  }

  public void addMemberHolder(CustomMembersHolder holder) {
    myDepot.addHolder(holder);
  }

  public Project getProject() {
    return myProject;
  }

  /** **********************************************************
   Methods to add new behavior
   *********************************************************** */
  def property(Map args) {
    if (args.isStatic) myClassText.append("static ")
    myClassText.append("def ").append(stringifyType(args.type)).append(" ").append(args.name).append("\n")
  }

  def method(Map args) {
    def params = [:]
    if (args.params) {
      args.params.each {name, type ->
        params[name] = stringifyType(type)
      }
    }
    if (args.isStatic) {
      myClassText.append("static ")
    }
    def name = escapeKeywords(args.name)
    myClassText.append("def ").append(stringifyType(args.type)).append(" ").append(name).append("(")
    myClassText.append(StringUtil.join(params.keySet(),
                                       [fun: {String s -> return params.get(s) + " " + s}] as Function, ", "))
    myClassText.append(") {}\n")
  }

  private static def escapeKeywords(String str){
    if (GroovyNamesUtil.isKeyword(str)) return "'$str'"
    else return str
  }

  private def stringifyType(type) {
    type instanceof Closure ? "groovy.lang.Closure" :
    type instanceof Map ? "java.util.Map" :
    type ? type.toString() : ""
  }

}
