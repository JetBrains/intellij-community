package com.intellij.lang.properties;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 27, 2005
 * Time: 6:00:51 PM
 * To change this template use File | Settings | File Templates.
 */
public class PropertiesReferenceManager implements ProjectComponent {
  private final Project myProject;

  public PropertiesReferenceManager(Project project) {
    myProject = project;
  }

  public void projectOpened() {
    final PsiReferenceProvider referenceProvider = new PropertiesReferenceProvider();
    ReferenceProvidersRegistry.getInstance(myProject).registerReferenceProvider(PsiLiteralExpression.class, referenceProvider);
  }

  public void projectClosed() {
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public String getComponentName() {
    return "Properties support manager";
  }

}
