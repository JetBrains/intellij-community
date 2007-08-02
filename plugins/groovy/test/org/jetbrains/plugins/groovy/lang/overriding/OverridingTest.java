package org.jetbrains.plugins.groovy.lang.overriding;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.testFramework.fixtures.*;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import junit.framework.*;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.testcases.simple.SimpleGroovyFileSetTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * User: Dmitry.Krasilschikov
 * Date: 31.07.2007
 */
public abstract class OverridingTest extends SimpleGroovyFileSetTestCase {
  protected OverridingTest(String path) {
    super(path);
  }

  public String transform(String testName, String[] data) throws Exception {
    String fileText = data[0];
    GroovyFile psiFile = (GroovyFile) TestUtils.createPseudoPhysicalFile(myProject, fileText);

    StringBuffer buffer = new StringBuffer();

    GrTypeDefinition[] grTypeDefinitions = psiFile.getTypeDefinitions();
    GrTypeDefinition lastTypeDefinition = psiFile.getTypeDefinitions()[grTypeDefinitions.length - 1];

    PsiMethod[] psiMethods = lastTypeDefinition.getMethods();

    for (PsiMethod method : psiMethods) {
      PsiMethod[] superMethods = findMethod(method);

      for (PsiMethod superMethod : superMethods) {
        buffer.append(superMethod.getContainingClass().toString() + ": " + superMethod.getContainingClass().getName());
        buffer.append("; ");
        buffer.append(superMethod.getSignature(PsiSubstitutor.EMPTY).toString());
        buffer.append("\n"); //between overring methods
      }
      buffer.append("\n");   //between different methods
    }
    buffer.append("\n");  //metween class definitions


    System.out.println(buffer);
    return buffer.toString();
  }

  abstract PsiMethod[] findMethod(PsiMethod method);

}