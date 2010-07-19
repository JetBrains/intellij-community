package org.jetbrains.plugins.groovy.dsl;


import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.dsl.dsltop.GdslMembersProvider

/**
 * @author peter, ilyas
 */
public class ExtensibleCustomMembersGenerator extends CustomMembersGenerator {

  public ExtensibleCustomMembersGenerator(GroovyClassDescriptor descriptor) {
    super(descriptor)
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
