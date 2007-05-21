package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.ProjectTopics;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Collections;

public class GroovyPsiManager implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager");
  private Project myProject;

  private Map<String, List<PsiMethod>> myDefaultMethods;
  private MessageBusConnection myRootConnection;
  private static final String DEFAULT_METHODS_QNAME = "org.codehaus.groovy.runtime.DefaultGroovyMethods";

  public GroovyPsiManager(Project project) {
    myProject = project;
  }

  public void projectOpened() {}

  public void projectClosed() {}

  @NonNls
  @NotNull
  public String getComponentName() {
    return "Groovy Psi Manager";
  }

  public void initComponent() {
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        fillDefaultGroovyMethods();
      }
    });

    myRootConnection = myProject.getMessageBus().connect();
    ModuleRootListener moduleRootListener = new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {
      }

      public void rootsChanged(ModuleRootEvent event) {
        fillDefaultGroovyMethods();
      }
    };

    myRootConnection.subscribe(ProjectTopics.PROJECT_ROOTS, moduleRootListener);
  }

  private void fillDefaultGroovyMethods() {
    myDefaultMethods = new HashMap<String, List<PsiMethod>>();

    PsiClass defaultMethodsClass = PsiManager.getInstance(myProject).findClass(DEFAULT_METHODS_QNAME, GlobalSearchScope.allScope(myProject));
    if (defaultMethodsClass != null) {
      for (PsiMethod method : defaultMethodsClass.getMethods()) {
        if (method.isConstructor()) continue;
        PsiParameter[] parameters = method.getParameterList().getParameters();
        LOG.assertTrue(parameters.length > 0);
        PsiType thisType = parameters[0].getType();
        if (!(thisType instanceof PsiClassType)) continue;
        PsiClass resolved = ((PsiClassType) thisType).resolve();
        if (resolved == null) continue;
        String thisQName = resolved.getQualifiedName();
        LOG.assertTrue(thisQName != null);
        List<PsiMethod> hisMethods = myDefaultMethods.get(thisQName);
        if (hisMethods== null) {
          hisMethods = new ArrayList<PsiMethod>();
          myDefaultMethods.put(thisQName, hisMethods);
        }
        hisMethods.add(convertToNonStatic(method, method.getManager().getElementFactory()));
      }
    }
  }

  private PsiMethod convertToNonStatic(PsiMethod method, PsiElementFactory elementFactory) {
    try {
      PsiMethod copy = elementFactory.createMethod(method.getName(), method.getReturnType());
      copy.getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);
      copy.getModifierList().setModifierProperty(PsiModifier.STATIC, false);
      PsiParameter[] originalParameters = method.getParameterList().getParameters();
      PsiParameterList newParamList = copy.getParameterList();
      for (int i = 1; i < originalParameters.length; i++) {
        PsiParameter originalParameter = originalParameters[i];
        PsiParameter parameter = elementFactory.createParameter("p" + i, originalParameter.getType());
        newParamList.add(parameter);
      }
      copy.getThrowsList().replace(method.getThrowsList());
      return copy;
    } catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  public void disposeComponent() {
    myRootConnection.disconnect();
  }


  public List<PsiMethod> getDefaultMethods(String qName) {
    if (myDefaultMethods == null) {
      fillDefaultGroovyMethods();
    }

    List<PsiMethod> methods = myDefaultMethods.get(qName);
    if (methods == null) return Collections.emptyList();
    return methods;
  }

  public static GroovyPsiManager getInstance(Project project) {
    return project.getComponent(GroovyPsiManager.class);
  }
}
