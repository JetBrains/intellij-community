package org.jetbrains.plugins.gant.reference;

import com.intellij.ProjectTopics;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.psi.SearchUtils;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * @author ilyas
 */
public class AntTasksProvider implements ProjectComponent {

  @NonNls private static final String ANT_TASK_CLASS = "org.apache.tools.ant.Task";

  private ArrayList<PsiClass> myAntTaks = new ArrayList<PsiClass>();
  private final Project myProject;
  private MessageBusConnection myRootConnection;
  private boolean needToRefresh = true;

  public static AntTasksProvider getInstance(Project project) {
    return project.getComponent(AntTasksProvider.class);
  }

  public AntTasksProvider(Project project) {
    myProject = project;
  }

  public void projectOpened() {
    myRootConnection = myProject.getMessageBus().connect();
    myRootConnection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {

      public void beforeRootsChange(ModuleRootEvent event) {
      }

      public void rootsChanged(final ModuleRootEvent event) {
        myAntTaks.clear();
        setRefresh(true);
      }
    });

    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        myAntTaks.clear();
        myAntTaks = findAntTasks(myProject);
        needToRefresh = false;
      }
    });
  }

  public void setRefresh(boolean flag){
    needToRefresh = flag;
  }

  public void projectClosed() {
    if (myRootConnection != null) {
      myRootConnection.disconnect();
    }
  }

  @NotNull
  public String getComponentName() {
    return "AntTasksProvider";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  synchronized public ArrayList<PsiClass> getAntTasks() {
    if (needToRefresh) {
      myAntTaks = findAntTasks(myProject);
      needToRefresh = false;
    }
    return myAntTaks;
  }

  private static ArrayList<PsiClass> findAntTasks(Project project) {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final PsiClass taskClass = facade.findClass(ANT_TASK_CLASS, GlobalSearchScope.allScope(project));

    if (taskClass != null) {
      final Iterable<PsiClass> inheritors = SearchUtils.findClassInheritors(taskClass, true);
      final ArrayList<PsiClass> classes = new ArrayList<PsiClass>();
      for (PsiClass inheritor : inheritors) {
        if (!inheritor.hasModifierProperty(PsiModifier.ABSTRACT) && !inheritor.hasModifierProperty(PsiModifier.PRIVATE)) {
          classes.add(inheritor);
        }
      }
      return classes;
    }

    return new ArrayList<PsiClass>(0);
  }
}
