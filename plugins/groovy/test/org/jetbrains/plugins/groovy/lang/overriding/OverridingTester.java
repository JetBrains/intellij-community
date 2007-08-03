package org.jetbrains.plugins.groovy.lang.overriding;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.testcases.simple.SimpleGroovyFileSetTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.Arrays;

/**
 * User: Dmitry.Krasilschikov
 * Date: 31.07.2007
 */
public abstract class OverridingTester extends SimpleGroovyFileSetTestCase {
  protected OverridingTester(String path) {
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
      String[] classes = sortUseContaingClass(superMethods);

      for (String classAsString : classes) {
        buffer.append(classAsString);
        buffer.append("\n");   //between different super methods
      }
      buffer.append("\n");   //between different methods
    }
    buffer.append("\n");  //metween class definitions


    System.out.println(buffer);
    return buffer.toString();
  }

  private String[] sortUseContaingClass(PsiMethod[] psiMethods) {
    String[] classes = new String[psiMethods.length];

    for (int i = 0; i < psiMethods.length; i++) {
      PsiMethod psiMethod = psiMethods[i];
      classes[i] = psiMethod.getContainingClass().toString() + ": " + psiMethod.getContainingClass().getName() +
          "; " + psiMethod.getSignature(PsiSubstitutor.EMPTY).toString();
    }
    Arrays.sort(classes);

    return classes;
  }

  abstract PsiMethod[] findMethod(PsiMethod method);

}