/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
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
package org.jetbrains.plugins.groovy.grape;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class GrabStartupActivity implements StartupActivity {

  @Override
  public void runActivity(@NotNull Project project) {
    if(project.isDisposed()) return;
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    DumbService myDumbService = DumbService.getInstance(project);
    myDumbService.runReadActionInSmartMode(() -> {
      performActivity(project);
      return null;
    });
  }

  private static void performActivity(@NotNull Project project) {
    List<PsiAnnotation> annotations = GrapeHelper.findGrabAnnotations(project, GlobalSearchScope.allScope(project));
    GrabService grabService = GrabService.getInstance(project);
    List<String> unprocessedGrabs = new ArrayList<>();
    for (PsiAnnotation annotation : annotations) {
      String grab = GrapeHelper.grabQuery(annotation);
      if (!grab.isEmpty()) {
        if (grabService.getPaths(grab) == null) {
          unprocessedGrabs.add(grab);
        }
      }
    }
    if (unprocessedGrabs.size() == 0) return;
    GrabService.showNotification(project);
  }
}
