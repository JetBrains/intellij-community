package org.jetbrains.javafx.sdk;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.facet.ExecutionModel;
import org.jetbrains.javafx.facet.JavaFxFacet;

import java.util.Collection;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxSdkListener implements ApplicationComponent {
  public JavaFxSdkListener(final MessageBus messageBus) {
    ProjectJdkTable.Listener jdkTableListener = new ProjectJdkTable.Listener() {
      public void jdkAdded(final Sdk sdk) {
        if (sdk.getSdkType() instanceof JavaFxSdkType) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                public void run() {
                  addLibrary(sdk);
                }
              });
            }
          });
        }
      }

      public void jdkRemoved(final Sdk sdk) {
        if (sdk.getSdkType() instanceof JavaFxSdkType) {
          removeLibrary(sdk);
        }
      }

      public void jdkNameChanged(final Sdk sdk, final String previousName) {
        if (sdk.getSdkType() instanceof JavaFxSdkType) {
          renameLibrary(sdk, previousName);
        }
      }
    };
    messageBus.connect().subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, jdkTableListener);
  }

  public static Library addLibrary(final Sdk sdk) {
    final LibraryTable.ModifiableModel libraryTableModel = LibraryTablesRegistrar.getInstance().getLibraryTable().getModifiableModel();
    final Library library = libraryTableModel.createLibrary(JavaFxFacet.getFacetLibraryName(sdk.getName()));
    final Library.ModifiableModel model = library.getModifiableModel();

    for (VirtualFile root : getRoots(sdk)) {
      model.addRoot(root, OrderRootType.CLASSES);
    }

    for (String url : sdk.getRootProvider().getUrls(OrderRootType.SOURCES)) {
      model.addRoot(url, OrderRootType.SOURCES);
    }
    model.commit();
    libraryTableModel.commit();
    return library;
  }

  private static Collection<VirtualFile> getRoots(final Sdk sdk) {
    final Set<VirtualFile> temp = new HashSet<VirtualFile>();
    ContainerUtil.addAll(temp, ExecutionModel.COMMON.getRoots(sdk));
    ContainerUtil.addAll(temp, ExecutionModel.DESKTOP.getRoots(sdk));
    final VirtualFile[] classes = sdk.getRootProvider().getFiles(OrderRootType.CLASSES);
    final Set<VirtualFile> result = new HashSet<VirtualFile>();
    for (VirtualFile cl : classes) {
      if (temp.contains(cl)) {
        result.add(cl);
      }
    }
    return result;
  }

  private static void removeLibrary(final Sdk sdk) {
    LaterInvocator.invokeLater(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            final LibraryTable.ModifiableModel libraryTableModel =
              LibraryTablesRegistrar.getInstance().getLibraryTable().getModifiableModel();
            final Library library = libraryTableModel.getLibraryByName(JavaFxFacet.getFacetLibraryName(sdk.getName()));
            if (library != null) {
              libraryTableModel.removeLibrary(library);
            }
            libraryTableModel.commit();
          }
        });
      }
    }, ModalityState.NON_MODAL);
  }

  private static void renameLibrary(final Sdk sdk, final String previousName) {
    LaterInvocator.invokeLater(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            final LibraryTable.ModifiableModel libraryTableModel =
              LibraryTablesRegistrar.getInstance().getLibraryTable().getModifiableModel();
            final Library library = libraryTableModel.getLibraryByName(JavaFxFacet.getFacetLibraryName(previousName));
            if (library != null) {
              final Library.ModifiableModel model = library.getModifiableModel();
              model.setName(JavaFxFacet.getFacetLibraryName(sdk.getName()));
              model.commit();
            }
            libraryTableModel.commit();
          }
        });
      }
    }, ModalityState.NON_MODAL);
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "JavaFxSdkListener";
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
  }
}
