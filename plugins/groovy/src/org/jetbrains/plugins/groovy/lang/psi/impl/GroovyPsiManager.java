/*
 * Copyright 2000-2007 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.ProjectTopics;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.application.ApplicationManager;
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
        if (!ApplicationManager.getApplication().isUnitTestMode() && myProject.isInitialized()) {
          fillDefaultGroovyMethods();
        }
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
        hisMethods.add(convertToNonStatic(method));
      }
    }
  }

  private PsiMethod convertToNonStatic(PsiMethod method) {
    return new DefaultGroovyMethod(method, null);
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
