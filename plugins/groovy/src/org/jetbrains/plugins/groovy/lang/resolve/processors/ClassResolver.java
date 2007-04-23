package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.util.containers.HashSet;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import java.util.Set;
import java.util.List;
import java.util.ArrayList;

/**
 * @author ven
 */
public class ClassResolver implements PsiScopeProcessor {
  private String myName;

  private List<GrTypeDefinition> myCandidates = new ArrayList<GrTypeDefinition>();

  public ClassResolver(String name) {
    myName = name;
  }

  public boolean execute(PsiElement element, PsiSubstitutor substitutor) {
    if (element instanceof GrTypeDefinition) {
      if (myName == null || myName.equals(((GrTypeDefinition) element).getName())) {
        myCandidates.add((GrTypeDefinition) element);
      }
    }

    return myName == null || myCandidates.size() < 2;
  }

  public List<GrTypeDefinition> getCandidates() {
    return myCandidates;
  }

  public <T> T getHint(Class<T> hintClass) {
    return null;
  }

  public void handleEvent(Event event, Object associated) {
  }
}
