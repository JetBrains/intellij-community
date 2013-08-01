package org.jetbrains.android;

import com.android.SdkConstants;
import com.android.sdklib.IAndroidTarget;
import com.intellij.ProjectTopics;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesElementFactory;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.importDependencies.ImportDependenciesUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidPropertyFilesUpdater extends AbstractProjectComponent {
  private static final NotificationGroup PROPERTY_FILES_UPDATING_NOTIFICATION =
    NotificationGroup.balloonGroup("Android Property Files Updating");
  private static final Key<List<Object>> ANDROID_PROPERTIES_STATE_KEY = Key.create("ANDROID_PROPERTIES_STATE");
  private Notification myNotification;

  private Disposable myDisposable;

  protected AndroidPropertyFilesUpdater(Project project) {
    super(project);
  }

  @Override
  public void initComponent() {
    myDisposable = new Disposable() {
      @Override
      public void dispose() {
      }
    };
    if (!ApplicationManager.getApplication().isUnitTestMode() &&
        !ApplicationManager.getApplication().isHeadlessEnvironment()) {
      addProjectPropertiesUpdatingListener();
    }
  }

  @Override
  public void disposeComponent() {
    if (myNotification != null && !myNotification.isExpired()) {
      myNotification.expire();
    }

    Disposer.dispose(myDisposable);
  }

  private void addProjectPropertiesUpdatingListener() {
    myProject.getMessageBus().connect(myDisposable).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      public void rootsChanged(final ModuleRootEvent event) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            StartupManager.getInstance(myProject).runWhenProjectIsInitialized(new Runnable() {
              @Override
              public void run() {
                updatePropertyFilesIfNecessary();
              }
            });
          }
        }, myProject.getDisposed());
      }
    });
  }

  private void updatePropertyFilesIfNecessary() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    final List<VirtualFile> toAskFiles = new ArrayList<VirtualFile>();
    final List<AndroidFacet> toAskFacets = new ArrayList<AndroidFacet>();
    final List<Runnable> toAskChanges = new ArrayList<Runnable>();

    final List<VirtualFile> files = new ArrayList<VirtualFile>();
    final List<Runnable> changes = new ArrayList<Runnable>();

    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      final AndroidFacet facet = AndroidFacet.getInstance(module);

      if (facet != null) {
        final String updatePropertyFiles = facet.getProperties().UPDATE_PROPERTY_FILES;
        final boolean ask = updatePropertyFiles.isEmpty();

        if (!ask && !Boolean.parseBoolean(updatePropertyFiles)) {
          continue;
        }
        final Pair<VirtualFile, List<Runnable>> pair = updateProjectPropertiesIfNecessary(facet);

        if (pair != null) {
          if (ask) {
            toAskFacets.add(facet);
            toAskFiles.add(pair.getFirst());
            toAskChanges.addAll(pair.getSecond());
          }
          else {
            files.add(pair.getFirst());
            changes.addAll(pair.getSecond());
          }
        }
      }
    }

    /* We should expire old notification even if there are no properties to update in current event.
     For example, user changed "is library" setting to 'true', the notification was shown, but user ignored it.
     Then he changed the setting to 'false' again. New notification won't be shown, because the value of
     "android.library" in project.properties is correct. However if the old notification was not expired,
     user may press on it, and "android.library" property will be changed to 'false'. */
    if (myNotification != null && !myNotification.isExpired()) {
      myNotification.expire();
    }

    if (changes.size() > 0 || toAskChanges.size() > 0) {
      if (toAskChanges.size() > 0) {
        askUserIfUpdatePropertyFile(myProject, toAskFacets, new Processor<MyResult>() {
          @Override
          public boolean process(MyResult result) {
            if (result == MyResult.NEVER) {
              for (AndroidFacet facet : toAskFacets) {
                facet.getProperties().UPDATE_PROPERTY_FILES = Boolean.FALSE.toString();
              }
              return true;
            }
            else if (result == MyResult.ALWAYS) {
              for (AndroidFacet facet : toAskFacets) {
                facet.getProperties().UPDATE_PROPERTY_FILES = Boolean.TRUE.toString();
              }
            }
            if (ReadonlyStatusHandler.ensureFilesWritable(myProject, toAskFiles.toArray(new VirtualFile[toAskFiles.size()]))) {
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                @Override
                public void run() {
                  for (Runnable change : toAskChanges) {
                    change.run();
                  }
                }
              });
            }
            return true;
          }
        });
      }

      if (changes.size() > 0 && ReadonlyStatusHandler.ensureFilesWritable(
        myProject, files.toArray(new VirtualFile[files.size()]))) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            for (Runnable change : changes) {
              change.run();
            }
          }
        });
      }
    }
  }

  @Nullable
  private static Pair<VirtualFile, List<Runnable>> updateProjectPropertiesIfNecessary(@NotNull AndroidFacet facet) {
    if (facet.isDisposed()) {
      return null;
    }
    final Module module = facet.getModule();
    final Pair<PropertiesFile, VirtualFile> pair =
      AndroidRootUtil.findPropertyFile(module, SdkConstants.FN_PROJECT_PROPERTIES);

    if (pair == null) {
      return null;
    }
    final PropertiesFile projectProperties = pair.getFirst();
    final VirtualFile projectPropertiesVFile = pair.getSecond();

    final Pair<Properties, VirtualFile> localProperties =
      AndroidRootUtil.readPropertyFile(module, SdkConstants.FN_LOCAL_PROPERTIES);
    final List<Runnable> changes = new ArrayList<Runnable>();

    final IAndroidTarget androidTarget = facet.getConfiguration().getAndroidTarget();
    final String androidTargetHashString = androidTarget != null ? androidTarget.hashString() : null;
    final VirtualFile[] dependencies = collectDependencies(module);
    final String[] dependencyPaths = toSortedPaths(dependencies);

    final List<Object> newState = Arrays.asList(androidTargetHashString, facet.getProperties().
      LIBRARY_PROJECT, Arrays.asList(dependencyPaths));
    final List<Object> state = facet.getUserData(ANDROID_PROPERTIES_STATE_KEY);

    if (state == null || !Comparing.equal(state, newState)) {
      updateTargetProperty(facet, projectProperties, changes);
      updateLibraryProperty(facet, projectProperties, changes);
      updateDependenciesInPropertyFile(projectProperties, localProperties, dependencies, changes);

      facet.putUserData(ANDROID_PROPERTIES_STATE_KEY, newState);
    }
    return changes.size() > 0 ? Pair.create(projectPropertiesVFile, changes) : null;
  }

  private static void updateDependenciesInPropertyFile(@NotNull final PropertiesFile projectProperties,
                                                       @Nullable final Pair<Properties, VirtualFile> localProperties,
                                                       @NotNull final VirtualFile[] dependencies,
                                                       @NotNull List<Runnable> changes) {
    final VirtualFile vFile = projectProperties.getVirtualFile();
    if (vFile == null) {
      return;
    }
    final Set<VirtualFile> localDependencies = localProperties != null
                                               ? ImportDependenciesUtil.getLibDirs(localProperties)
                                               : Collections.<VirtualFile>emptySet();
    final VirtualFile baseDir = vFile.getParent();
    final String baseDirPath = baseDir.getPath();
    final List<String> newDepValues = new ArrayList<String>();

    for (VirtualFile dependency : dependencies) {
      if (!localDependencies.contains(dependency)) {
        final String relPath = FileUtil.getRelativePath(baseDirPath, dependency.getPath(), '/');
        final String value = relPath != null ? relPath : dependency.getPath();
        newDepValues.add(value);
      }
    }
    final Set<String> oldDepValues = new HashSet<String>();

    for (IProperty property : projectProperties.getProperties()) {
      final String name = property.getName();
      if (name != null && name.startsWith(AndroidUtils.ANDROID_LIBRARY_REFERENCE_PROPERTY_PREFIX)) {
        oldDepValues.add(property.getValue());
      }
    }

    if (!new HashSet<String>(newDepValues).equals(oldDepValues)) {
      changes.add(new Runnable() {
        @Override
        public void run() {
          for (IProperty property : projectProperties.getProperties()) {
            final String name = property.getName();
            if (name != null && name.startsWith(AndroidUtils.ANDROID_LIBRARY_REFERENCE_PROPERTY_PREFIX)) {
              property.getPsiElement().delete();
            }
          }

          for (int i = 0; i < newDepValues.size(); i++) {
            final String value = newDepValues.get(i);
            projectProperties.addProperty(AndroidUtils.ANDROID_LIBRARY_REFERENCE_PROPERTY_PREFIX + Integer.toString(i + 1), value);
          }
        }
      });
    }
  }

  @NotNull
  private static VirtualFile[] collectDependencies(@NotNull Module module) {
    final List<VirtualFile> dependenciesList = new ArrayList<VirtualFile>();

    for (AndroidFacet depFacet : AndroidUtils.getAndroidLibraryDependencies(module)) {
      final Module depModule = depFacet.getModule();
      final VirtualFile libDir = getBaseAndroidContentRoot(depModule);
      if (libDir != null) {
        dependenciesList.add(libDir);
      }
    }
    return dependenciesList.toArray(new VirtualFile[dependenciesList.size()]);
  }

  private static void updateTargetProperty(@NotNull AndroidFacet facet,
                                           @NotNull final PropertiesFile propertiesFile,
                                           @NotNull List<Runnable> changes) {
    final Project project = facet.getModule().getProject();
    final IAndroidTarget androidTarget = facet.getConfiguration().getAndroidTarget();

    if (androidTarget != null) {
      final String targetPropertyValue = androidTarget.hashString();
      final IProperty property = propertiesFile.findPropertyByKey(AndroidUtils.ANDROID_TARGET_PROPERTY);


      if (property == null) {
        changes.add(new Runnable() {
          @Override
          public void run() {
            propertiesFile.addProperty(createProperty(project, targetPropertyValue));
          }
        });
      }
      else {
        if (!Comparing.equal(property.getValue(), targetPropertyValue)) {
          final PsiElement element = property.getPsiElement();
          if (element != null) {
            changes.add(new Runnable() {
              @Override
              public void run() {
                element.replace(createProperty(project, targetPropertyValue).getPsiElement());
              }
            });
          }
        }
      }
    }
  }

  public static void updateLibraryProperty(@NotNull AndroidFacet facet,
                                           @NotNull final PropertiesFile propertiesFile,
                                           @NotNull List<Runnable> changes) {
    final IProperty property = propertiesFile.findPropertyByKey(AndroidUtils.ANDROID_LIBRARY_PROPERTY);

    if (property != null) {
      final String value = Boolean.toString(facet.getProperties().LIBRARY_PROJECT);

      if (!value.equals(property.getValue())) {
        changes.add(new Runnable() {
          @Override
          public void run() {
            property.setValue(value);
          }
        });
      }
    }
    else if (facet.getProperties().LIBRARY_PROJECT) {
      changes.add(new Runnable() {
        @Override
        public void run() {
          propertiesFile.addProperty(AndroidUtils.ANDROID_LIBRARY_PROPERTY, Boolean.TRUE.toString());
        }
      });
    }
  }

  @Nullable
  private static VirtualFile getBaseAndroidContentRoot(@NotNull Module module) {
    final AndroidFacet facet = AndroidFacet.getInstance(module);
    final VirtualFile manifestFile = facet != null ? AndroidRootUtil.getManifestFile(facet) : null;
    final VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    if (manifestFile != null) {
      for (VirtualFile contentRoot : contentRoots) {
        if (VfsUtilCore.isAncestor(contentRoot, manifestFile, true)) {
          return contentRoot;
        }
      }
    }
    return contentRoots.length > 0 ? contentRoots[0] : null;
  }

  // workaround for behavior of Android SDK , which uses non-escaped ':' characters
  @NotNull
  private static IProperty createProperty(@NotNull Project project, @NotNull String targetPropertyValue) {
    final String text = AndroidUtils.ANDROID_TARGET_PROPERTY + "=" + targetPropertyValue;
    final PropertiesFile dummyFile = PropertiesElementFactory.createPropertiesFile(project, text);
    return dummyFile.getProperties().get(0);
  }

  @NotNull
  private static String[] toSortedPaths(@NotNull VirtualFile[] files) {
    final String[] result = new String[files.length];

    for (int i = 0; i < files.length; i++) {
      result[i] = files[i].getPath();
    }
    Arrays.sort(result);
    return result;
  }

  private void askUserIfUpdatePropertyFile(@NotNull Project project,
                                                  @NotNull Collection<AndroidFacet> facets,
                                                  @NotNull final Processor<MyResult> callback) {
    final StringBuilder moduleList = new StringBuilder();

    for (AndroidFacet facet : facets) {
      moduleList.append(facet.getModule().getName()).append("<br>");
    }
    myNotification = PROPERTY_FILES_UPDATING_NOTIFICATION.createNotification(
      AndroidBundle.message("android.update.project.properties.dialog.title"),
      AndroidBundle.message("android.update.project.properties.dialog.text", moduleList.toString()),
      NotificationType.INFORMATION, new NotificationListener() {
      @Override
      public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
        if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          final String desc = event.getDescription();

          if ("once".equals(desc)) {
            callback.process(MyResult.ONCE);
          }
          else if ("never".equals(desc)) {
            callback.process(MyResult.NEVER);
          }
          else {
            callback.process(MyResult.ALWAYS);
          }
          notification.expire();
        }
      }
    });
    myNotification.notify(project);
  }

  private enum MyResult {
    ONCE, NEVER, ALWAYS
  }
}
