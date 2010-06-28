package org.jetbrains.plugins.groovy.dsl;


import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.dsl.dsltop.GdslMembersProvider

/**
 * @author peter, ilyas
 */
public class ExtensibleCustomMembersGenerator extends CustomMembersGenerator {
  private final Project myProject;
  private final PsiElement myPlace;
  private final String myQualifiedName;

  public ExtensibleCustomMembersGenerator(Project project, PsiElement place, String qualifiedName) {
    super(project, place, qualifiedName)
    myProject = project;
    myPlace = place;
    myQualifiedName = qualifiedName;
  }

  def methodMissing(String name, args) {
    final def newArgs = constructNewArgs(args)

    // Get other DSL methods from extensions
    for (d in GdslMembersProvider.EP_NAME.getExtensions()) {
      final def variants = d.metaClass.respondsTo(d, name, newArgs)
      if (variants.size() == 1) {
        return d.invokeMethod(name, newArgs)
      }
    }
    return null
  }

}
